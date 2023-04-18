package eu.sia.pagopa.rendicontazioni.actor

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import eu.sia.pagopa.Main.ConfigData
import eu.sia.pagopa.common.actor.PerRequestActor
import eu.sia.pagopa.common.enums.EsitoRE
import eu.sia.pagopa.common.exception
import eu.sia.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException}
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.repo.Repositories
import eu.sia.pagopa.common.repo.fdr.enums.{FtpFileStatus, RendicontazioneStatus}
import eu.sia.pagopa.common.repo.fdr.model._
import eu.sia.pagopa.common.repo.re.model.Re
import eu.sia.pagopa.common.util._
import eu.sia.pagopa.common.util.xml.XsdValid
import eu.sia.pagopa.commonxml.XmlEnum
import eu.sia.pagopa.rendicontazioni.actor.response.NodoInviaFlussoRendicontazioneResponse
import eu.sia.pagopa.rendicontazioni.util.CheckRendicontazioni
import eu.sia.pagopa.{ActorProps, BootstrapUtil}
import it.pagopa.config.CreditorInstitution
import scalaxbmodel.nodoperpsp.{NodoInviaFlussoRendicontazione, NodoInviaFlussoRendicontazioneRisposta}

import java.io.{File, FileOutputStream}
import java.nio.file.{Files, Paths}
import java.security.MessageDigest
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

final case class NodoInviaFlussoRendicontazioneActorPerRequest(repositories: Repositories, actorProps: ActorProps) extends PerRequestActor with NodoInviaFlussoRendicontazioneResponse {

  var req: SoapRequest = _
  var replyTo: ActorRef = _

  val checkUTF8: Boolean = context.system.settings.config.getBoolean("bundle.checkUTF8")
  val inputXsdValid: Boolean = DDataChecks.getConfigurationKeys(ddataMap, "validate_input").toBoolean
  val outputXsdValid: Boolean = DDataChecks.getConfigurationKeys(ddataMap, "validate_output").toBoolean
  var re: Option[Re] = None

  override def actorError(e: DigitPaException): Unit = {
    actorError(req, replyTo, ddataMap, e, re)
  }

  private def parseInput(payload: String, inputXsdValid: Boolean): Try[NodoInviaFlussoRendicontazione] = {
    log.debug("parseInput")
    (for {
      _ <- XsdValid.checkOnly(payload, XmlEnum.NODO_INVIA_FLUSSO_RENDICONTAZIONE_NODOPERPSP, inputXsdValid)
      body <- XmlEnum.str2nodoInviaFlussoRendicontazione_nodoperpsp(payload)
    } yield body) recoverWith { case e =>
      log.warn(e, s"${e.getMessage}")
      val cfb = exception.DigitPaException(e.getMessage, DigitPaErrorCodes.PPT_SINTASSI_EXTRAXSD, e)
      Failure(cfb)
    }
  }

  private def checks(ddataMap: ConfigData, nodoInviaFlussoRendicontazione: NodoInviaFlussoRendicontazione) = {
    val paaa = for {
      (psp, canale) <- DDataChecks
        .checkPspIntermediarioPspCanale(
          log,
          ddataMap,
          Some(nodoInviaFlussoRendicontazione.identificativoPSP),
          nodoInviaFlussoRendicontazione.identificativoIntermediarioPSP,
          Some(nodoInviaFlussoRendicontazione.identificativoCanale),
          Some(nodoInviaFlussoRendicontazione.password)
        )
        .map(pc => pc._1 -> pc._3)
      pa <- DDataChecks.checkPA(log, ddataMap, nodoInviaFlussoRendicontazione.identificativoDominio)
      _ <- CheckRendicontazioni.checkFormatoIdFlussoRendicontazione(nodoInviaFlussoRendicontazione.identificativoFlusso, nodoInviaFlussoRendicontazione.identificativoPSP)
    } yield (pa, psp, canale)

    paaa.recoverWith({
      case ex: DigitPaException =>
        Failure(ex)
      case ex @ _ =>
        Failure(exception.DigitPaException(ex.getMessage, DigitPaErrorCodes.PPT_SINTASSI_XSD, ex))
    })
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

  private def validateAndSaveRendicontazione(nifr: NodoInviaFlussoRendicontazione, pa: CreditorInstitution): Future[(String, Rendicontazione, Option[FtpFile])] = {
    log.debug("Check validita flusso riversamento")

    for {
      content <- Future.fromTry(StringUtils.getStringDecoded(nifr.xmlRendicontazione, checkUTF8))
      r <- XsdValid.checkOnly(content, XmlEnum.FLUSSO_RIVERSAMENTO_FLUSSORIVERSAMENTO, inputXsdValid) match {
        case Success(_) =>
          log.debug("Salvataggio rendicontazione valida")

          val flussoRiversamento =
            XmlEnum.str2FlussoRiversamento_flussoriversamento(content).getOrElse(throw exception.DigitPaException(DigitPaErrorCodes.PPT_SINTASSI_XSD))

          if (flussoRiversamento.identificativoFlusso != nifr.identificativoFlusso) {
            throw exception.DigitPaException("Il campo [identificativoFlusso] non è uguale al campo dentro xml flusso riversamento [identificativoFlusso]", DigitPaErrorCodes.PPT_SEMANTICA)
          }

          val dataOraFlussoFlussoRiversamento = flussoRiversamento.dataOraFlusso.toGregorianCalendar.toZonedDateTime.toLocalDateTime
          val dataOraFlusso = nifr.dataOraFlusso.toGregorianCalendar.toZonedDateTime.toLocalDateTime

          if (
            dataOraFlussoFlussoRiversamento.getYear != dataOraFlusso.getYear ||
            dataOraFlussoFlussoRiversamento.getMonth != dataOraFlusso.getMonth ||
            dataOraFlussoFlussoRiversamento.getDayOfMonth != dataOraFlusso.getDayOfMonth ||
            dataOraFlussoFlussoRiversamento.getHour != dataOraFlusso.getHour ||
            dataOraFlussoFlussoRiversamento.getMinute != dataOraFlusso.getMinute ||
            dataOraFlussoFlussoRiversamento.getSecond != dataOraFlusso.getSecond
          ) {
            throw exception.DigitPaException("Il campo [dataOraFlusso] non è uguale al campo dentro xml flusso riversamento [dataOraFlusso]", DigitPaErrorCodes.PPT_SEMANTICA)
          }

          if (pa.reportingFtp) {
            val ftpServerConf = ddataMap.ftpServers
              .find(s => {
                s._2.service == Constant.KeyName.RENDICONTAZIONI
              })
            if (ftpServerConf.isEmpty) {
              log.error("Nessun server ftp configurato")
              throw exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR)
            }
            val ftpServer = ftpServerConf.get._2

            val normalizedIdFlusso = s"${CheckRendicontazioni.normalizeIdFlusso(nifr.identificativoFlusso)}.xml"
            val (filename, contenutoFileBytes) = if (pa.reportingZip) {
              val zipFile = File.createTempFile(normalizedIdFlusso, ".zip")
              val zip = new ZipOutputStream(new FileOutputStream(zipFile))
              zip.putNextEntry(new ZipEntry(normalizedIdFlusso))
              zip.write(content.getBytes(Constant.UTF_8))
              zip.closeEntry()
              zip.close()

              val zippedContent = Files.readAllBytes(Paths.get(zipFile.getAbsolutePath))
              Files.delete(Paths.get(zipFile.getAbsolutePath))

              s"$normalizedIdFlusso.zip" -> zippedContent
            } else {
              normalizedIdFlusso -> content.getBytes(Constant.UTF_8)
            }

            val sftpFile = FtpFile(
              0,
              contenutoFileBytes.length,
              contenutoFileBytes,
              new String(MessageDigest.getInstance("MD5").digest(contenutoFileBytes)),
              filename,
              pa.creditorInstitutionCode,
              FtpFileStatus.TO_UPLOAD,
              ftpServer.id,
              ftpServer.host,
              ftpServer.port,
              0,
              Util.now(),
              Util.now(),
              actorClassId,
              actorClassId
            )
            val rendi = Rendicontazione(
              0,
              RendicontazioneStatus.VALID,
              0,
              nifr.identificativoPSP,
              Some(nifr.identificativoIntermediarioPSP),
              Some(nifr.identificativoCanale),
              nifr.identificativoDominio,
              nifr.identificativoFlusso,
              LocalDateTime.parse(nifr.dataOraFlusso.toString, DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.systemDefault())),
              None,
              None
            )
            repositories.fdrRepository
              .save(rendi, sftpFile)
              .recoverWith({ case e =>
                Future.failed(exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, e))
              })
              .flatMap(data => {
                Future.successful((Constant.OK, data._1, Some(data._2)))
              })
          } else {
            val rendi = Rendicontazione(
              0,
              RendicontazioneStatus.VALID,
              0,
              nifr.identificativoPSP,
              Some(nifr.identificativoIntermediarioPSP),
              Some(nifr.identificativoCanale),
              nifr.identificativoDominio,
              nifr.identificativoFlusso,
              LocalDateTime.parse(nifr.dataOraFlusso.toString, DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.systemDefault())),
              None,
              None
            )
            val bf = BinaryFile(0, nifr.xmlRendicontazione.length, Some(nifr.xmlRendicontazione.toArray), None, Some(content))
            repositories.fdrRepository
              .saveRendicontazioneAndBinaryFile(rendi, bf)
              .recoverWith({ case e =>
                Future.failed(exception.DigitPaException("Errore salvataggio rendicontazione", DigitPaErrorCodes.PPT_SYSTEM_ERROR, e))
              })
              .flatMap(data => {
                Future.successful((Constant.OK, data._1, None))
              })
          }

        case Failure(e) =>
          log.warn(e, "Flusso riversamento non valido")
          val rendi = Rendicontazione(
            0,
            RendicontazioneStatus.INVALID,
            0,
            nifr.identificativoPSP,
            Some(nifr.identificativoIntermediarioPSP),
            Some(nifr.identificativoCanale),
            nifr.identificativoDominio,
            nifr.identificativoFlusso,
            LocalDateTime.parse(nifr.dataOraFlusso.toString, DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.systemDefault())),
            None,
            None
          )
          repositories.fdrRepository
            .save(rendi)
            .recoverWith({ case e1 =>
              Future.failed(exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, e1))
            })
            .flatMap(_ => {
              Future.failed(exception.DigitPaException(e.getMessage, DigitPaErrorCodes.PPT_SINTASSI_XSD, e))
            })
      }
    } yield r
  }

  private def notifySFTPSender(pa: CreditorInstitution, sessionId: String, testCaseId: Option[String], file: FtpFile): Future[FTPResponse] = {
    log.info(s"Richiesta SFTPRequest pushFile")

    val ftpServerConf = ddataMap.ftpServers.find(s => {s._2.service == Constant.KeyName.RENDICONTAZIONI}).get

    askBundle[FTPRequest, FTPResponse](
      actorProps.routers(BootstrapUtil.actorRouter(Constant.KeyName.FTP_SENDER)),
      FTPRequest(sessionId, testCaseId, "pushFileRendicontazioni", pa.creditorInstitutionCode, file.fileName, file.id, ftpServerConf._2.id)
    )

  }

  private def wrapInBundleMessage(ncefrr: NodoInviaFlussoRendicontazioneRisposta) = {
    for {
      respPayload <- XmlEnum.nodoInviaFlussoRendicontazioneRisposta2Str_nodoperpsp(ncefrr)
      _ <- XsdValid.checkOnly(respPayload, XmlEnum.NODO_INVIA_FLUSSO_RENDICONTAZIONE_RISPOSTA_NODOPERPSP, outputXsdValid)
      _ = log.debug("Envelope di risposta valida")
    } yield respPayload
  }

  override def receive: Receive = { case soapRequest: SoapRequest =>
    req = soapRequest
    replyTo = sender()

    re = Some(
      Re(
        componente = Componente.FESP.toString,
        categoriaEvento = CategoriaEvento.INTERNO.toString,
        sessionId = Some(req.sessionId),
        payload = None,
        esito = Some(EsitoRE.CAMBIO_STATO.toString),
        tipoEvento = Some(actorClassId),
        sottoTipoEvento = SottoTipoEvento.INTERN.toString,
        insertedTimestamp = soapRequest.timestamp,
        erogatore = Some(FaultId.NODO_DEI_PAGAMENTI_SPC),
        businessProcess = Some(actorClassId),
        erogatoreDescr = Some(FaultId.NODO_DEI_PAGAMENTI_SPC)
      )
    )

    val pipeline = for {

      _ <- Future.successful(())

      _ = log.debug("Parserizzazione input")
      _ = log.info(NodoLogConstant.logSintattico(actorClassId))
      nodoInviaFlussoRendicontazione <- Future.fromTry(parseInput(soapRequest.payload, inputXsdValid))
      _ = log.debug("Input parserizzato correttamente")

      now = Util.now()
      re_ = Re(
        idDominio = Some(nodoInviaFlussoRendicontazione.identificativoDominio),
        psp = Some(nodoInviaFlussoRendicontazione.identificativoPSP),
        componente = Componente.FESP.toString,
        categoriaEvento = CategoriaEvento.INTERNO.toString,
        tipoEvento = Some(actorClassId),
        sottoTipoEvento = SottoTipoEvento.INTERN.toString,
        fruitore = Some(nodoInviaFlussoRendicontazione.identificativoCanale),
        erogatore = Some(FaultId.NODO_DEI_PAGAMENTI_SPC),
        canale = Some(nodoInviaFlussoRendicontazione.identificativoCanale),
        esito = Some(EsitoRE.RICEVUTA.toString),
        sessionId = Some(req.sessionId),
        insertedTimestamp = now,
        businessProcess = Some(actorClassId),
        erogatoreDescr = Some(FaultId.NODO_DEI_PAGAMENTI_SPC)
      )
      _ = re = Some(re_)

      _ = log.debug("Check semantici input req")
      _ = log.info(NodoLogConstant.logSemantico(actorClassId))
      (pa, psp, canale) <- Future.fromTry(checks(ddataMap, nodoInviaFlussoRendicontazione))

      _ = re = re.map(r => r.copy(fruitoreDescr = canale.flatMap(c => c.description), pspDescr = psp.flatMap(p => p.description)))

      _ = log.debug("Check duplicati su db")
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
//          val d = LocalDateTime.parse(nodoInviaFlussoRendicontazione.dataOraFlusso.toString, DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.systemDefault()))
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

      _ = log.debug("Check xml rendicontazione e salvataggio")
      (esito, _, sftpFile) <- validateAndSaveRendicontazione(nodoInviaFlussoRendicontazione, pa)

      _ <-
        if (sftpFile.isDefined) {
          notifySFTPSender(pa, req.sessionId, req.testCaseId, sftpFile.get).flatMap(resp => {
            if (resp.throwable.isDefined) {
              //HOTFIX non torno errore al chiamante se ftp non funziona
              log.warn(s"Errore primo invio flusso di rendicontazione [${resp.throwable.get.getMessage}]")
              Future.successful(())
            } else {
              Future.successful(())
            }
          })
        } else {
          Future.successful(())
        }

      _ = log.info(NodoLogConstant.logGeneraPayload("nodoInviaFlussoRendicontazioneRisposta"))
      nodoInviaFlussoRisposta = NodoInviaFlussoRendicontazioneRisposta(None, esito)
      _ = log.info(NodoLogConstant.logSintattico("nodoInviaFlussoRendicontazioneRisposta"))
      resultMessage <- Future.fromTry(wrapInBundleMessage(nodoInviaFlussoRisposta))
      sr = SoapResponse(req.sessionId, Some(resultMessage), StatusCodes.OK.intValue, re, req.testCaseId, None)
    } yield sr

    pipeline.recover({
      case e: DigitPaException =>
        log.warn(e, s"Creazione response negativa [${e.getMessage}]")
        log.info(NodoLogConstant.logGeneraPayload("nodoInviaFlussoRendicontazioneRisposta"))
        errorHandler(req.sessionId, req.testCaseId, outputXsdValid, e, re)
      case e: Throwable =>
        log.warn(e, s"Creazione response negativa [${e.getMessage}]")
        log.info(NodoLogConstant.logGeneraPayload("nodoInviaFlussoRendicontazioneRisposta"))
        errorHandler(req.sessionId, req.testCaseId, outputXsdValid, exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, e), re)
    }) map (sr => {
      log.info(NodoLogConstant.logEnd(actorClassId))
      replyTo ! sr
      complete()
    })

  }

}
