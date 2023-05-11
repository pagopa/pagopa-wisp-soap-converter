package eu.sia.pagopa.rendicontazioni.actor.soap

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import eu.sia.pagopa.common.enums.EsitoRE
import eu.sia.pagopa.common.exception
import eu.sia.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException}
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.repo.Repositories
import eu.sia.pagopa.common.repo.fdr.model._
import eu.sia.pagopa.common.repo.re.model.Re
import eu.sia.pagopa.common.util.{ReUtil, _}
import eu.sia.pagopa.common.util.xml.XsdValid
import eu.sia.pagopa.commonxml.XmlEnum
import eu.sia.pagopa.rendicontazioni.actor.BaseInviaFlussoRendicontazioneActor
import eu.sia.pagopa.rendicontazioni.actor.soap.response.NodoInviaFlussoRendicontazioneResponse
import eu.sia.pagopa.rendicontazioni.util.CheckRendicontazioni
import eu.sia.pagopa.{ActorProps, BootstrapUtil}
import it.pagopa.config.CreditorInstitution
import scalaxbmodel.nodoperpsp.{NodoInviaFlussoRendicontazione, NodoInviaFlussoRendicontazioneRisposta}

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}
import scala.concurrent.Future
import scala.util.Try

final case class NodoInviaFlussoRendicontazioneActorPerRequest(repositories: Repositories, actorProps: ActorProps) extends BaseInviaFlussoRendicontazioneActor with ReUtil  with NodoInviaFlussoRendicontazioneResponse {

  var req: SoapRequest = _
  var replyTo: ActorRef = _

  private val callNewServiceFdr: Boolean = Try(context.system.settings.config.getBoolean(s"callNewServiceFdr")).getOrElse(false)

  val RESPONSE_NAME = "nodoInviaFlussoRendicontazioneRisposta"

  override def receive: Receive = { case soapRequest: SoapRequest =>
    req = soapRequest
    replyTo = sender()

    re = Some(
      Re(
        componente = Componente.FDR.toString,
        categoriaEvento = CategoriaEvento.INTERNO.toString,
        sessionId = Some(req.sessionId),
        payload = None,
        esito = Some(EsitoRE.CAMBIO_STATO.toString),
        tipoEvento = Some(actorClassId),
        sottoTipoEvento = SottoTipoEvento.INTERN.toString,
        insertedTimestamp = soapRequest.timestamp,
        erogatore = Some(FaultId.FDR),
        businessProcess = Some(actorClassId),
        erogatoreDescr = Some(FaultId.FDR)
      )
    )

    val pipeline = for {
      _ <- Future.successful(())

      nodoInviaFlussoRendicontazione <- Future.fromTry(parseInput(soapRequest.payload, inputXsdValid))

      now = Util.now()
      re_ = Re(
        idDominio = Some(nodoInviaFlussoRendicontazione.identificativoDominio),
        psp = Some(nodoInviaFlussoRendicontazione.identificativoPSP),
        componente = Componente.FDR.toString,
        categoriaEvento = CategoriaEvento.INTERNO.toString,
        tipoEvento = Some(actorClassId),
        sottoTipoEvento = SottoTipoEvento.INTERN.toString,
        fruitore = Some(nodoInviaFlussoRendicontazione.identificativoCanale),
        erogatore = Some(FaultId.FDR),
        canale = Some(nodoInviaFlussoRendicontazione.identificativoCanale),
        esito = Some(EsitoRE.RICEVUTA.toString),
        sessionId = Some(req.sessionId),
        insertedTimestamp = now,
        businessProcess = Some(actorClassId),
        erogatoreDescr = Some(FaultId.FDR)
      )
      _ = re = Some(re_)

      _ = log.info(FdrLogConstant.logSemantico(actorClassId))
      (pa, psp, canale) <- Future.fromTry(checks(ddataMap, nodoInviaFlussoRendicontazione, true, actorClassId))

      _ = re = re.map(r => r.copy(fruitoreDescr = canale.flatMap(c => c.description), pspDescr = psp.flatMap(p => p.description)))

      _ = log.debug("Check duplicates on db")
      _ <- checksDB(nodoInviaFlussoRendicontazione)

      dataOraFlussoNew = LocalDateTime.parse(nodoInviaFlussoRendicontazione.dataOraFlusso.toString, DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.systemDefault()))
      oldRendi <- repositories.fdrRepository.findRendicontazioniByIdFlusso(
        nodoInviaFlussoRendicontazione.identificativoPSP,
        nodoInviaFlussoRendicontazione.identificativoFlusso,
        LocalDateTime.of(dataOraFlussoNew.getYear, 1, 1, 0, 0, 0),
        LocalDateTime.of(dataOraFlussoNew.getYear, 12, 31, 23, 59, 59)
      )

      _ = log.debug("Check dataOraFlusso new with dataOraFlusso old")
      _ <- oldRendi match {
        case Some(old) =>
          val idDominioNew = nodoInviaFlussoRendicontazione.identificativoDominio
          if (dataOraFlussoNew.isAfter(old.dataOraFlusso)) {
            log.debug("Check idDominio new with idDominio old")
            if (idDominioNew == old.dominio) {
              Future.successful(())
            } else {
              Future.failed(
                exception.DigitPaException(
                  s"Il file con identificativoFlusso [${nodoInviaFlussoRendicontazione.identificativoFlusso}] ha idDominio old[${old.dominio}], idDominio new [$idDominioNew]",
                  DigitPaErrorCodes.PPT_SEMANTICA
                )
              )
            }
          } else {
            Future.failed(
              exception.DigitPaException(
                s"Esiste già un file con identificativoFlusso [${nodoInviaFlussoRendicontazione.identificativoFlusso}] più aggiornato con dataOraFlusso [${old.dataOraFlusso.toString}]",
                DigitPaErrorCodes.PPT_SEMANTICA
              )
            )
          }
        case None =>
          Future.successful(())
      }

      (flussoRiversamento, flussoRiversamentoContent) <- validateRendicontazione(nodoInviaFlussoRendicontazione, checkUTF8, inputXsdValid)
      (esito, _, sftpFile, _) <- saveRendicontazione(nodoInviaFlussoRendicontazione, flussoRiversamentoContent, flussoRiversamento, pa, ddataMap, actorClassId)

      _ <-
        if (sftpFile.isDefined) {
          notifySFTPSender(pa, req.sessionId, req.testCaseId, sftpFile.get).flatMap(resp => {
            if (resp.throwable.isDefined) {
              //HOTFIX non torno errore al chiamante se ftp non funziona
              log.warn(s"Error sending file first time for reporting flow [${resp.throwable.get.getMessage}]")
              Future.successful(())
            } else {
              Future.successful(())
            }
          })
        } else {
          Future.successful(())
        }

      _ <- if( callNewServiceFdr ) {
        inviaFlussoRendicontazioneSoap2Rest(req, nodoInviaFlussoRendicontazione, flussoRiversamento)
      } else {
        Future.successful(())
      }

      _ = log.info(FdrLogConstant.logGeneraPayload(RESPONSE_NAME))
      nodoInviaFlussoRisposta = NodoInviaFlussoRendicontazioneRisposta(None, esito)
      _ = log.info(FdrLogConstant.logSintattico(RESPONSE_NAME))
      resultMessage <- Future.fromTry(wrapInBundleMessage(nodoInviaFlussoRisposta))
      sr = SoapResponse(req.sessionId, Some(resultMessage), StatusCodes.OK.intValue, re, req.testCaseId, None)
    } yield sr

    pipeline.recover({
      case e: DigitPaException =>
        log.warn(e, FdrLogConstant.logGeneraPayload(s"negative $RESPONSE_NAME, [${e.getMessage}]"))
        errorHandler(req.sessionId, req.testCaseId, outputXsdValid, e, re)
      case e: Throwable =>
        log.warn(e, FdrLogConstant.logGeneraPayload(s"negative $RESPONSE_NAME, [${e.getMessage}]"))
        errorHandler(req.sessionId, req.testCaseId, outputXsdValid, exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, e), re)
    }) map (sr => {
      traceInterfaceRequest(soapRequest, re.get, soapRequest.reExtra, reEventFunc, ddataMap)
      log.info(FdrLogConstant.logEnd(actorClassId))
      replyTo ! sr
      complete()
    })

  }

  override def actorError(e: DigitPaException): Unit = {
    actorError(req, replyTo, ddataMap, e, re)
  }

  private def checksDB(nifr: NodoInviaFlussoRendicontazione) = {
    val datazoned =
      LocalDateTime.parse(nifr.dataOraFlusso.toString, DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.systemDefault()))
    CheckRendicontazioni.checkFlussoRendicontazioneNotPresentOnSamePsp(repositories.fdrRepository, nifr.identificativoFlusso, nifr.identificativoPSP, datazoned).flatMap {
      case Some(r) =>
        Future.failed(
          exception.DigitPaException(
            s"flusso di rendicontazione gia' presente(identificativo PSP ${r.psp}, identificativo flusso ${r.idFlusso}, data - ora ${r.dataOraFlusso})",
            DigitPaErrorCodes.PPT_SEMANTICA
          )
        )
      case None =>
        Future.successful(())
    }
  }

  private def notifySFTPSender(pa: CreditorInstitution, sessionId: String, testCaseId: Option[String], file: FtpFile): Future[FTPResponse] = {
    log.info(s"SFTP Request pushFile")

    val ftpServerConf = ddataMap.ftpServers.find(s => {
      s._2.service == Constant.KeyName.RENDICONTAZIONI
    }).get

    askBundle[FTPRequest, FTPResponse](
      actorProps.routers(BootstrapUtil.actorRouter(Constant.KeyName.FTP_SENDER)),
      FTPRequest(sessionId, testCaseId, "pushFileRendicontazioni", pa.creditorInstitutionCode, file.fileName, file.id, ftpServerConf._2.id)
    )
  }

  private def wrapInBundleMessage(ncefrr: NodoInviaFlussoRendicontazioneRisposta) = {
    for {
      respPayload <- XmlEnum.nodoInviaFlussoRendicontazioneRisposta2Str_nodoperpsp(ncefrr)
      _ <- XsdValid.checkOnly(respPayload, XmlEnum.NODO_INVIA_FLUSSO_RENDICONTAZIONE_RISPOSTA_NODOPERPSP, outputXsdValid)
      _ = log.debug("Valid Response envelope")
    } yield respPayload
  }

}
