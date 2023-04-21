package eu.sia.pagopa.rendicontazioni.actor.rest

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import eu.sia.pagopa.common.actor.PerRequestActor
import eu.sia.pagopa.common.enums.EsitoRE
import eu.sia.pagopa.common.exception
import eu.sia.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException, RestException}
import eu.sia.pagopa.common.json.model.Error
import eu.sia.pagopa.common.json.model.rendicontazione._
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.repo.Repositories
import eu.sia.pagopa.common.repo.re.model.Re
import eu.sia.pagopa.common.util._
import eu.sia.pagopa.common.util.xml.XsdValid
import eu.sia.pagopa.commonxml.XmlEnum
import eu.sia.pagopa.rendicontazioni.actor.soap.response.NodoInviaFlussoRendicontazioneResponse
import eu.sia.pagopa.rendicontazioni.util.RendicontazioniUtil
import eu.sia.pagopa.{ActorProps, BootstrapUtil}
import org.slf4j.MDC
import scalaxbmodel.flussoriversamento.{CtFlussoRiversamento, Number0, Number1u461, Number3, StVersioneOggetto}
import scalaxbmodel.nodoperpsp.{NodoInviaFlussoRendicontazione, NodoInviaFlussoRendicontazioneRisposta}
import spray.json._

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}
import scala.concurrent.Future
import scala.util.{Failure, Try}

final case class InviaFlussoRendicontazioneActorPerRequest(repositories: Repositories, actorProps: ActorProps)
  extends PerRequestActor with NodoInviaFlussoRendicontazioneResponse {

  var replyTo: ActorRef = _
  var req: RestRequest = _

  val checkUTF8: Boolean = context.system.settings.config.getBoolean("bundle.checkUTF8")
  var re: Option[Re] = None

  override def receive: Receive = {
    case restRequest: RestRequest =>
      req = restRequest
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
          insertedTimestamp = restRequest.timestamp,
          erogatore = Some(FaultId.FDR),
          businessProcess = Some(actorClassId),
          erogatoreDescr = Some(FaultId.FDR)
        )
      )

      val pipeline = for {

        _ <- Future.successful(())

        _ = log.debug("Parserizzazione input")
        _ = log.info(FdrLogConstant.logSintattico(actorClassId))
        nodoInviaFlussoRendicontazione <- Future.fromTry(parseInput(restRequest.payload.get, false))
        _ = log.debug("Input parserizzato correttamente")

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

        _ = log.debug("Check semantici input req")
        _ = log.info(FdrLogConstant.logSemantico(actorClassId))


        _ = log.debug("Check xml rendicontazione e salvataggio")
//        (esito, _, sftpFile, flussoRiversamento) <- validateAndSaveRendicontazione(nodoInviaFlussoRendicontazione, pa)

//        _ <- if (callNewServiceFdr) {
//          translateNifrFdrNewAndCallIt(nodoInviaFlussoRendicontazione, flussoRiversamento)
//        } else {
//          Future.successful(())
//        }

        _ = log.info(FdrLogConstant.logGeneraPayload("nodoInviaFlussoRendicontazioneRisposta"))
        nodoInviaFlussoRisposta = NodoInviaFlussoRendicontazioneRisposta(None, "")
        _ = log.info(FdrLogConstant.logSintattico("nodoInviaFlussoRendicontazioneRisposta"))
        sr = SoapResponse(req.sessionId, None, StatusCodes.OK.intValue, re, req.testCaseId, None)
      } yield sr

      pipeline.recover({
        case rex: RestException =>
          Future.successful(generateResponse(Some(rex)))
        case cause: Throwable =>
          val pmae = RestException(DigitPaErrorCodes.description(DigitPaErrorCodes.PPT_SYSTEM_ERROR), StatusCodes.InternalServerError.intValue, cause)
          Future.successful(generateResponse(Some(pmae)))
      }) map (sr => {
        log.info(FdrLogConstant.logEnd(actorClassId))
        replyTo ! sr
        complete()
      })

  }

  override def actorError(dpe: DigitPaException): Unit = {
    actorError(replyTo, req, dpe, re)
  }

  def actorError(replyTo: ActorRef, req: RestRequest, dpe: DigitPaException, re: Option[Re]): Unit = {
    MDC.put(Constant.MDCKey.SESSION_ID, req.sessionId)
    val dpa = RestException(dpe.getMessage, StatusCodes.InternalServerError.intValue, dpe)
    val response = makeFailureResponse(req.sessionId, req.testCaseId, dpa, re)
    replyTo ! response

  }

  private def makeFailureResponse(sessionId: String, tcid: Option[String], restException: RestException, re: Option[Re]): RestResponse = {
    import spray.json._
    log.error(restException, s"Errore generico: ${restException.message}")
    val err = Error(restException.message).toJson.toString()
    RestResponse(sessionId, Some(err), restException.statusCode, re, tcid, Some(restException))
  }

  private def generateResponse(exception: Option[RestException]) = {
    log.info(FdrLogConstant.logGeneraPayload(actorClassId + "Risposta"))
    val httpStatusCode = exception.map(_.statusCode).getOrElse(StatusCodes.OK.intValue)
    log.debug(s"Generazione risposta $httpStatusCode")
    RestResponse(req.sessionId, None, httpStatusCode, re, req.testCaseId, None)
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

  private def sendToNodoInviaFlussoRendicontazioneSoap(): Unit = {
    actorProps.routers(BootstrapUtil.actorRouter("nodoInviaFlussoRendicontazione")).tell("", replyTo)
  }

  private def translateRestToSoap(inviaFlussoRendicontazione: InviaFlussoRendicontazioneRequest) = {
    (for {
      _ <- Future.successful(())
      _ = log.info(FdrLogConstant.logGeneraPayload(s"nodoInviaFlussoRendicontazione SOAP"))

//      flussoRiversamento = CtFlussoRiversamento(
//        Number1u461,
//        inviaFlussoRendicontazione.reportingFlow,
//        inviaFlussoRendicontazione.dateReportingFlow,
//        inviaFlussoRendicontazione.
//      )
//
//      nodoInviaFlussoRendicontazione = NodoInviaFlussoRendicontazione(
//        inviaFlussoRendicontazione.sender.idPsp,
//        inviaFlussoRendicontazione.sender.idBroker,
//        inviaFlussoRendicontazione.sender.idChannel,
//        "", //TODO
//        inviaFlussoRendicontazione.receiver.idEc,
//        inviaFlussoRendicontazione.reportingFlow,
//        inviaFlussoRendicontazione.dateReportingFlow,
//        inviaFlussoRendicontazione.payments.map(p => {
//          CtFlussoRiversamento(
//
//          )
//        })
//      )

//      nifrRequest = InviaFlussoRendicontazioneRequest(
//        nodoInviaFlussoRendicontazione.identificativoFlusso,
//        nodoInviaFlussoRendicontazione.dataOraFlusso.toGregorianCalendar.toZonedDateTime.toLocalDateTime.format(DateTimeFormatter.ISO_DATE_TIME),
//        Sender(
//          nodoInviaFlussoRendicontazione.identificativoPSP,
//          nodoInviaFlussoRendicontazione.identificativoIntermediarioPSP,
//          nodoInviaFlussoRendicontazione.identificativoCanale,
//          nodoInviaFlussoRendicontazione.password,
//          flussoRiversamento.istitutoMittente.identificativoUnivocoMittente.tipoIdentificativoUnivoco match {
//            case scalaxbmodel.flussoriversamento.GValue => SenderTypeEnum.PERSONA_GIURIDICA
//            case scalaxbmodel.flussoriversamento.A      => SenderTypeEnum.CODICE_ABI
//            case _                                      => SenderTypeEnum.CODICE_BIC
//          },
//          flussoRiversamento.istitutoMittente.denominazioneMittente,
//          flussoRiversamento.istitutoMittente.identificativoUnivocoMittente.codiceIdentificativoUnivoco
//        ),
//        Receiver(
//          nodoInviaFlussoRendicontazione.identificativoDominio,
//          flussoRiversamento.istitutoRicevente.identificativoUnivocoRicevente.codiceIdentificativoUnivoco,
//          flussoRiversamento.istitutoRicevente.denominazioneRicevente
//        ),
//        flussoRiversamento.identificativoUnivocoRegolamento,
//        flussoRiversamento.dataRegolamento.toGregorianCalendar.toZonedDateTime.toLocalDateTime.format(DateTimeFormatter.ISO_DATE_TIME),
//        flussoRiversamento.codiceBicBancaDiRiversamento,
//        flussoRiversamento.datiSingoliPagamenti.map(p => {
//          Payment(
//            p.identificativoUnivocoVersamento,
//            p.identificativoUnivocoRiscossione,
//            p.indiceDatiSingoloPagamento.map(_.intValue),
//            p.singoloImportoPagato,
//            p.codiceEsitoSingoloPagamento match {
//              case Number0  => CodiceEsitoSingoloPagamentoEnum.PAGAMENTO_ESEGUITO
//              case Number3  => CodiceEsitoSingoloPagamentoEnum.PAGAMENTO_REVOCATO
//              case _        => CodiceEsitoSingoloPagamentoEnum.PAGAMENTO_NO_RPT
//            },
//            p.dataEsitoSingoloPagamento.toGregorianCalendar.toZonedDateTime.toLocalDateTime.format(DateTimeFormatter.ISO_DATE_TIME)
//          )
//        })
//      ).toJson.toString

//      nifrResponse <- RendicontazioniUtil.callPrimitiveNew(
//        req.sessionId,
//        req.testCaseId,
//        req.primitive,
//        SoapReceiverType.FDRNEW.toString,
//        nifrRequest,
//        actorProps
//      )
    } yield ()).recoverWith({
      case _ => Future.successful(())
    })
  }
}
