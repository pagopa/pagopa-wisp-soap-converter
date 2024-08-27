package it.gov.pagopa.actors

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import it.gov.pagopa.ActorProps
import it.gov.pagopa.common.actor.PerRequestActor
import it.gov.pagopa.common.exception
import it.gov.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException}
import it.gov.pagopa.common.message._
import it.gov.pagopa.common.repo.CosmosRepository
import it.gov.pagopa.common.util.Util.decodeAndDecompressGZIP
import it.gov.pagopa.common.util.azure.cosmos.{CategoriaEvento, Componente, Esito, SottoTipoEvento}
import it.gov.pagopa.common.util.{Constant, DDataChecks, FaultId, LogConstant}
import it.gov.pagopa.common.util.xml.{XmlUtil, XsdValid}
import it.gov.pagopa.commonxml.XmlEnum
import org.slf4j.MDC
import scalaxb.Base64Binary
import scalaxbmodel.nodoperpa.{FaultBean, NodoChiediCopiaRT, NodoChiediCopiaRTRisposta}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

case class NodoChiediCopiaRTActorPerRequest(cosmosRepository: CosmosRepository, actorProps: ActorProps) extends PerRequestActor {
  var req: SoapRequest = _
  var replyTo: ActorRef = _
  var re: Option[Re] = None

  val inputXsdValid: Boolean = true
  val outputXsdValid: Boolean = true

  override def receive: Receive = {
    case soapRequest: SoapRequest =>
      log.info("NodoChiediCopiaRT received")
      req = soapRequest
      replyTo = sender()

      re = Some(
        Re(
          componente = Componente.WISP_SOAP_CONVERTER,
          categoriaEvento = CategoriaEvento.INTERNAL,
          sessionId = None,
          sessionIdOriginal = Some(req.sessionId),
          payload = None,
          esito = Esito.EXECUTED_INTERNAL_STEP,
          tipoEvento = Some(actorClassId),
          sottoTipoEvento = SottoTipoEvento.INTERN,
          insertedTimestamp = soapRequest.timestamp,
          erogatore = Some(FaultId.NODO_DEI_PAGAMENTI_SPC),
          businessProcess = Some(actorClassId),
          erogatoreDescr = Some(FaultId.NODO_DEI_PAGAMENTI_SPC)
        )
      )

      // Attempt to parse the input
      log.info(LogConstant.logSintattico(actorClassId))
      val nodoChiediCopiaRTResult = parseInput(req.payload)

      nodoChiediCopiaRTResult match {
        case Success(nodoChiediCopiaRT) =>
          // Extract values from parsed input
          val ccp = nodoChiediCopiaRT.codiceContestoPagamento
          val iuv = nodoChiediCopiaRT.identificativoUnivocoVersamento
          val idDominio = nodoChiediCopiaRT.identificativoDominio

          MDC.put(Constant.MDCKey.ID_DOMINIO, idDominio)
          MDC.put(Constant.MDCKey.IUV, iuv)
          MDC.put(Constant.MDCKey.CCP, ccp)

          // Concatenate values and get rtKeyRaw (may have invalid characters)
          val rtKeyRaw = s"${idDominio}_${iuv}_${ccp}"
          // Remove illegal characters and get rtKey: ['/', '\', '#'] are illegals character for the cosmos entity id
          val rtKey = rtKeyRaw.replaceAll("[/\\\\#]", "")

          log.info(s"Get RT for the key: $rtKey")

          // Fetch the RT using the key
          fetchRT(rtKey).onComplete {
            case Success((Some(encodedXmlContent), Some(tipoFirma))) =>
              log.info(s"Successfully fetched and encoded RT")
              val ccrtr = generateChiediCopiaRTRisposta(Some(encodedXmlContent), Some(tipoFirma))
              val payload = makeResponseAndValid(ccrtr, outputXsdValid).get
              replyTo ! SoapResponse(req.sessionId, payload, StatusCodes.OK.intValue, re, req.testCaseId, None)

            case Failure(exception) =>
              log.error(exception, "Failed to fetch RT")
              val soap = makeFailureResponse(req.sessionId, req.testCaseId, exception, FaultId.NODO_DEI_PAGAMENTI_SPC, outputXsdValid, re)
              replyTo ! soap
          }

        case Failure(exception) =>
          // Handle the exception by logging and sending an error response
          log.error(exception, "Failed to parse input payload")
          actorError(exception.asInstanceOf[DigitPaException])
          val soap = makeFailureResponse(req.sessionId, req.testCaseId, exception, FaultId.NODO_DEI_PAGAMENTI_SPC, outputXsdValid, re)
          replyTo ! soap
          complete()
      }
  }

  override def actorError(e: DigitPaException): Unit = {
    log.error(e, "Error in NodoChiediCopiaRTActorPerRequest")
  }

  private def generateChiediCopiaRTRisposta(rt: Option[Base64Binary], tipoFirma: Option[String]): NodoChiediCopiaRTRisposta = {
    NodoChiediCopiaRTRisposta(None, tipoFirma, rt)
  }

  private def parseInput(payload: String): Try[NodoChiediCopiaRT] = {
    log.debug("parseInput")
    (for {
      _ <- XsdValid.checkOnly(payload, XmlEnum.NODO_CHIEDI_COPIA_RT_NODOPERPA, inputXsdValid)
      body <- XmlEnum.str2nodoChiediCopiaRT_nodoperpa(payload)
    } yield body) recoverWith { case e =>
      Failure(exception.DigitPaException(e.getMessage, DigitPaErrorCodes.PPT_SINTASSI_EXTRAXSD, e))
    }
  }

  private def fetchRT(rtKey: String): Future[(Option[Base64Binary], Option[String])] = {
    // Query cosmos repository by RT key
    cosmosRepository.getRtByKey(rtKey).flatMap {
      case Some(rtEntity) =>
        log.info(s"Found entity: ${rtEntity.id}")

        rtEntity.rt match {
          case Some(rt) =>
            try {
              // Decode and decompress rtEntity.rt
              val decompressedBytes = decodeAndDecompressGZIP(rt)
              // Encode the XML content as Base64
              val encodedXmlContent = XmlUtil.StringBase64Binary.encodeBase64(decompressedBytes)

              Future.successful((Some(encodedXmlContent), Some("")))
            } catch {
              case e: Exception =>
                log.error(e, "Failed to decode or decompress GZIP content")
                Future.failed(e)
            }

          case None =>
            log.info("RPT exists but RT in unknown")
            val digit = exception.DigitPaException("RT non disponibile, riprovare in un secondo momento", DigitPaErrorCodes.PPT_RT_NONDISPONIBILE)
            Future.failed(digit)
        }

      case None =>
        log.info("RPT doesn't exist: no item found with the given key")
        val digit = exception.DigitPaException(DigitPaErrorCodes.PPT_RT_SCONOSCIUTA)
        Future.failed(digit)
    }
  }

  def makeResponseAndValid(res: NodoChiediCopiaRTRisposta, outputXsdValid: Boolean): Try[String] = {
    (for {
      payload <- XmlEnum.nodoChiediCopiaRTRisposta2Str_nodoperpa(res)
      _ <- XsdValid.checkOnly(payload, XmlEnum.NODO_CHIEDI_COPIA_RT_RISPOSTA_NODOPERPA, outputXsdValid)
    } yield payload) recoverWith { case e =>
      val ex = exception.DigitPaException("Error while creating the response", DigitPaErrorCodes.PPT_SYSTEM_ERROR, e)
      log.error(ex, "Failed to validate output payload")
      Failure(ex)
    }
  }

  def makeFailureResponse(sessionId: String, tcid: Option[String], ex: Throwable, failureId: String, outputXsdValid: Boolean, re: Option[Re]): SoapResponse = {
    val dpe = ex match {
      case x: DigitPaException => x
      case x: Throwable        => exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, x)
    }
    val result = NodoChiediCopiaRTRisposta(Option(FaultBean(dpe.faultCode, dpe.faultString, failureId, Some(ex.getMessage))), None)
    makeResponseAndValid(result, outputXsdValid) match {
      case Success(_) =>
        SoapResponse(sessionId, XmlEnum.nodoChiediCopiaRTRisposta2Str_nodoperpa(result).get, StatusCodes.OK.intValue, re, tcid, Some(ex))
      case Failure(e) =>
        log.warn(s"the nodoChiediCopiaRTRisposta payload did not pass syntactic validation. " +
          s"It will still answer with [${e.getMessage}]")
        SoapResponse(sessionId, XmlEnum.nodoChiediCopiaRTRisposta2Str_nodoperpa(result).get, StatusCodes.OK.intValue, re, tcid, Some(ex))
    }
  }
}