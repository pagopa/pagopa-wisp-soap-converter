package eu.sia.pagopa.rendicontazioni.actor

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import eu.sia.pagopa.ActorProps
import eu.sia.pagopa.common.actor.PerRequestActor
import eu.sia.pagopa.common.enums.EsitoRE
import eu.sia.pagopa.common.exception
import eu.sia.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException}
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.repo.Repositories
import eu.sia.pagopa.common.repo.re.model.Re
import eu.sia.pagopa.common.util._
import eu.sia.pagopa.common.util.xml.XmlUtil.XsdDatePattern
import eu.sia.pagopa.common.util.xml.{XmlUtil, XsdValid}
import eu.sia.pagopa.commonxml.XmlEnum
import eu.sia.pagopa.rendicontazioni.actor.response.NodoChiediElencoFlussiRendicontazioneResponse
import scalaxbmodel.nodoperpa.{NodoChiediElencoFlussiRendicontazione, NodoChiediElencoFlussiRendicontazioneRisposta, TipoElencoFlussiRendicontazione, TipoIdRendicontazione}

import java.time.LocalDateTime
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

final case class NodoChiediElencoFlussiRendicontazioneActorPerRequest(repositories: Repositories, actorProps: ActorProps)
    extends PerRequestActor
    with ReUtil
    with NodoChiediElencoFlussiRendicontazioneResponse {

  var req: SoapRequest = _
  var replyTo: ActorRef = _
  val inputXsdValid: Boolean = DDataChecks.getConfigurationKeys(ddataMap, "validate_input").toBoolean
  val outputXsdValid: Boolean = DDataChecks.getConfigurationKeys(ddataMap, "validate_output").toBoolean

  val dayLimit: Long = Try(context.system.settings.config.getLong(s"chiediElencoFlussiRendicontazioneDayLimit")).getOrElse(90)

  var re: Option[Re] = None

  override def actorError(e: DigitPaException): Unit = {
    actorError(req, replyTo, ddataMap, e, re)
  }

  private def parseInput(br: SoapRequest): Try[NodoChiediElencoFlussiRendicontazione] = {
    (for {
      _ <- XsdValid.checkOnly(br.payload, XmlEnum.NODO_CHIEDI_ELENCO_FLUSSI_RENDICONTAZIONE_NODOPERPA, inputXsdValid)
      body <- XmlEnum.str2nodoChiediElencoFlussiRendicontazione_nodoperpa(br.payload)
    } yield body) recoverWith { case e =>
      log.error(e, e.getMessage)
      Failure(exception.DigitPaException(e.getMessage, DigitPaErrorCodes.PPT_SINTASSI_EXTRAXSD))
    }
  }

  private def createTipoElencoFlussiRendicontazione(rendicontazioni: Seq[(String, LocalDateTime)]) = {
    val tipiIdRendi = rendicontazioni.map(rendi => {
      Some(TipoIdRendicontazione(rendi._1, XmlUtil.StringXMLGregorianCalendarDate.format(rendi._2, XsdDatePattern.DATE_TIME)))
    })
    Future.successful(Some(TipoElencoFlussiRendicontazione(tipiIdRendi.size, tipiIdRendi)))
  }

  private def findRendicontazioni(ncefr: NodoChiediElencoFlussiRendicontazione) = {
    val idjIdIntPA = ddataMap.creditorInstitutionBrokers(ncefr.identificativoIntermediarioPA).brokerCode
    val idStazioniInt = ddataMap.stations.filter(_._2.brokerCode == idjIdIntPA).map(_._2.stationCode).toSeq
    val paStazPa =
      ddataMap.creditorInstitutionStations.filter(spa => idStazioniInt.contains(spa._2.stationCode)).map(_._2.creditorInstitutionCode).toSeq
    val domini = ddataMap.creditorInstitutions
      .filter(p => ncefr.identificativoDominio.forall(d => p._1 == d))
      .filter(pa => {
        paStazPa.contains(pa._2.creditorInstitutionCode)
      })
      .keys
      .toSeq
    repositories.fdrRepository.findRendicontazioni(domini, ncefr.identificativoPSP, dayLimit)
  }

  private def checks(ncefr: NodoChiediElencoFlussiRendicontazione) = {
    val identificativoDominio = ncefr.identificativoDominio
    Future.fromTry({
      for {
        staz <-
          if (identificativoDominio.isDefined) {
            DDataChecks
              .checkPaIntermediarioPaStazione(log, ddataMap, identificativoDominio.get, ncefr.identificativoIntermediarioPA, ncefr.identificativoStazioneIntermediarioPA, None, Some(ncefr.password))
              .map(_._3)
          } else {
            DDataChecks.checkIntermediarioPaStazionePassword(log, ddataMap, ncefr.identificativoIntermediarioPA, ncefr.identificativoStazioneIntermediarioPA, ncefr.password).map(_._2)
          }
        psp <-
          if (ncefr.identificativoPSP.isDefined) {
            DDataChecks.checkPsp(log, ddataMap, ncefr.identificativoPSP.get).map(p => Some(p))
          } else {
            Success(None)
          }
      } yield (staz, psp)
    })
  }

  private def wrapInBundleMessage(ncefrr: NodoChiediElencoFlussiRendicontazioneRisposta) = {
    for {
      respPayload <- XmlEnum.nodoChiediElencoFlussiRendicontazioneRisposta2Str_nodoperpa(ncefrr)
      _ <- XsdValid.checkOnly(respPayload, XmlEnum.NODO_CHIEDI_ELENCO_FLUSSI_RENDICONTAZIONE_RISPOSTA_NODOPERPA, outputXsdValid)
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
        esito = Some(EsitoRE.CAMBIO_STATO.toString),
        tipoEvento = Some(actorClassId),
        sottoTipoEvento = SottoTipoEvento.INTERN.toString,
        insertedTimestamp = soapRequest.timestamp,
        erogatore = Some(FaultId.NODO_DEI_PAGAMENTI_SPC),
        businessProcess = Some(actorClassId),
        erogatoreDescr = Some(FaultId.NODO_DEI_PAGAMENTI_SPC)
      )
    )
    log.info(NodoLogConstant.logSintattico(actorClassId))
    val pipeline = for {

      ncefr <- Future.fromTry(parseInput(soapRequest))

      _ = re = re.map(r =>
        r.copy(
          idDominio = ncefr.identificativoDominio,
          psp = ncefr.identificativoPSP,
          fruitore = Some(ncefr.identificativoStazioneIntermediarioPA),
          stazione = Some(ncefr.identificativoStazioneIntermediarioPA),
          esito = Some(EsitoRE.RICEVUTA.toString)
        )
      )

      _ = log.info(NodoLogConstant.logSemantico(actorClassId))
      (staz, psp) <- checks(ncefr)

      _ = re = re.map(r => r.copy(fruitoreDescr = Some(staz.stationCode), pspDescr = psp.flatMap(_.description)))

      _ = log.debug("Query rendicontazini valide")
      rendicontazioni <- findRendicontazioni(ncefr)

      rendicontazioniFiltered = rendicontazioni.groupBy(_._1).map(a => a._2.maxBy(_._2)(Ordering.by(_.toString)))

      _ = log.debug(s"Trovate ${rendicontazioniFiltered.size} Rendicontazioni")

      elencoFlussiRendicontazione <- createTipoElencoFlussiRendicontazione(rendicontazioniFiltered.toSeq)

      ncrfrResponse = NodoChiediElencoFlussiRendicontazioneRisposta(None, elencoFlussiRendicontazione)

      _ = log.debug("Creazione risposta")
      _ = log.info(NodoLogConstant.logGeneraPayload("nodoChiediElencoFlussiRendicontazioneRisposta"))
      env <- Future.fromTry(wrapInBundleMessage(ncrfrResponse))
    } yield SoapResponse(soapRequest.sessionId, Some(env), StatusCodes.OK.intValue, re, soapRequest.testCaseId, None)

    pipeline.recover({
      case e: DigitPaException =>
        log.warn(s"Creazione response negativa [${e.getMessage}]")
        log.info(NodoLogConstant.logGeneraPayload("nodoChiediElencoFlussiRendicontazioneRisposta"))
        errorHandler(req.sessionId, req.testCaseId, outputXsdValid, e, re)
      case e: Throwable =>
        log.warn(e, s"Creazione response negativa [${e.getMessage}]")
        log.info(NodoLogConstant.logGeneraPayload("nodoChiediElencoFlussiRendicontazioneRisposta"))
        errorHandler(req.sessionId, req.testCaseId, outputXsdValid, DigitPaErrorCodes.PPT_SYSTEM_ERROR, re)
    }) map (sr => {
      traceInterfaceRequest(soapRequest, re.get, soapRequest.reExtra, reEventFunc, ddataMap)
      log.info(NodoLogConstant.logEnd(actorClassId))
      replyTo ! sr
      complete()
    })
  }

}
