package eu.sia.pagopa.rendicontazioni.actor.soap

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import eu.sia.pagopa.ActorProps
import eu.sia.pagopa.common.actor.{HttpSoapServiceManagement, PerRequestActor}
import eu.sia.pagopa.common.enums.EsitoRE
import eu.sia.pagopa.common.exception
import eu.sia.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException}
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.repo.Repositories
import eu.sia.pagopa.common.repo.fdr.model.{BinaryFile, Rendicontazione}
import eu.sia.pagopa.common.repo.re.model.Re
import eu.sia.pagopa.common.util._
import eu.sia.pagopa.common.util.xml.XmlUtil.StringBase64Binary
import eu.sia.pagopa.common.util.xml.XsdValid
import eu.sia.pagopa.commonxml.XmlEnum
import eu.sia.pagopa.rendicontazioni.actor.soap.response.NodoChiediFlussoRendicontazioneResponse
import it.pagopa.config.{CreditorInstitution, PaymentServiceProvider, Station}
import scalaxb.Base64Binary
import scalaxbmodel.nodoperpa.{NodoChiediFlussoRendicontazione, NodoChiediFlussoRendicontazioneRisposta}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

case class NodoChiediFlussoRendicontazioneActorPerRequest(repositories: Repositories, actorProps: ActorProps)
  extends PerRequestActor with ReUtil with NodoChiediFlussoRendicontazioneResponse {

  override def actorError(e: DigitPaException): Unit = {
    actorError(req, replyTo, ddataMap, e, reFlow)
  }

  var req: SoapRequest = _
  var replyTo: ActorRef = _

  val outputXsdValid: Boolean = DDataChecks.getConfigurationKeys(ddataMap, "validate_output").toBoolean
  val inputXsdValid: Boolean = DDataChecks.getConfigurationKeys(ddataMap, "validate_input").toBoolean

  private val callNexiToo: Boolean = Try(context.system.settings.config.getBoolean(s"callNexiToo")).getOrElse(false)

  val RESPONSE_NAME = "nodoChiediFlussoRendicontazioneRisposta"

  var reFlow: Option[Re] = None

  private def parseInput(br: SoapRequest): Try[NodoChiediFlussoRendicontazione] = {
    (for {
      _ <- XsdValid.checkOnly(br.payload, XmlEnum.NODO_CHIEDI_FLUSSO_RENDICONTAZIONE_NODOPERPA, inputXsdValid)
      body <- XmlEnum.str2nodoChiediFlussoRendicontazione_nodoperpa(br.payload)
    } yield body) recoverWith { case e =>
      log.error(e, e.getMessage)
      val cfb = exception.DigitPaException(e.getMessage, DigitPaErrorCodes.PPT_SINTASSI_EXTRAXSD, e)
      Failure(cfb)
    }
  }

  private def wrapInBundleMessage(ncefrr: NodoChiediFlussoRendicontazioneRisposta) = {
    for {
      respPayload <- XmlEnum.nodoChiediFlussoRendicontazioneRisposta2Str_nodoperpa(ncefrr)
      _ <- XsdValid.checkOnly(respPayload, XmlEnum.NODO_CHIEDI_FLUSSO_RENDICONTAZIONE_RISPOSTA_NODOPERPA, outputXsdValid)
    } yield respPayload
  }

  private def elaboraRisposta(binaryFileOption: Option[BinaryFile], paOpt: Option[CreditorInstitution]): Future[Option[Base64Binary]] = {
    paOpt match {
      case Some(pa) =>
        if (pa.reportingFtp) {
          log.info("FTP reporting")
          Future.successful(None)
        } else {
          log.info("NOT FTP reporting")
          if (binaryFileOption.isDefined) {
            val unzippedFilecontent = Util.unzipContent(binaryFileOption.get.fileContent.get) match {
              case Success(content) => content
              case Failure(e) => throw new exception.DigitPaException("Error during unzip xml content from db", DigitPaErrorCodes.PPT_SYSTEM_ERROR, e)
            }
            val resppayload = StringBase64Binary.encodeBase64ToBase64(unzippedFilecontent)
            Future.successful(Some(resppayload))
          } else {
            Future.successful(None)
          }
        }

      case None =>
        log.info("Identificativo dominio NON presente")
        if (binaryFileOption.isDefined) {
          val unzippedFilecontent = Util.unzipContent(binaryFileOption.get.fileContent.get) match {
            case Success(content) => content
            case Failure(e) => throw new exception.DigitPaException("Error during unzip xml content from db", DigitPaErrorCodes.PPT_SYSTEM_ERROR, e)
          }
          val resppayload = StringBase64Binary.encodeBase64ToBase64(unzippedFilecontent)
          Future.successful(Some(resppayload))
        } else {
          Future.successful(None)
        }
    }
  }

  private def checksSemanticiEDuplicati(
      ncfr: NodoChiediFlussoRendicontazione
  ): Future[(Rendicontazione, Option[BinaryFile], Option[String], Option[CreditorInstitution], Station, Option[PaymentServiceProvider])] = {
    val identificativoDominio = ncfr.identificativoDominio
    val rendiFuture =
      checkFlussoRendicontazione(ncfr.identificativoFlusso, ncfr.identificativoDominio, ncfr.identificativoPSP)
    rendiFuture flatMap {
      case Some(rendicontazione) =>
        log.debug(s"Flow: ${ncfr.identificativoFlusso} unknown")

        val checks = for {
          (pa, staz) <-
            if (identificativoDominio.isDefined) {
              DDataChecks
                .checkPaIntermediarioPaStazione(log, ddataMap, identificativoDominio.get, ncfr.identificativoIntermediarioPA, ncfr.identificativoStazioneIntermediarioPA, None, Some(ncfr.password))
                .map(x => {
                  (Some(x._1), x._3)
                })
            } else {
              DDataChecks
                .checkIntermediarioPaStazionePassword(log, ddataMap, ncfr.identificativoIntermediarioPA, ncfr.identificativoStazioneIntermediarioPA, ncfr.password)
                .map(x => {
                  (None, x._2)
                })
            }

          psp <-
            if (ncfr.identificativoPSP.isDefined) {
              DDataChecks.checkPsp(log, ddataMap, ncfr.identificativoPSP.get).map(p => Some(p))
            } else {
              Success(None)
            }

          _ <- checkDatiRendicontazione(rendicontazione, ncfr.identificativoDominio, ncfr.identificativoPSP)

        } yield (pa, staz, psp)

        checks match {
          case Success((pa, staz, psp)) =>
            log.debug("Validations passed")
            for {
              binaryFileOption <- rendicontazione.fk_binary_file match {
                case Some(fk) =>
                  repositories.fdrRepository.binaryFileById(fk)
                case None =>
                  Future.successful(None)
              }

            } yield (rendicontazione, binaryFileOption, ncfr.identificativoDominio, pa, staz, psp)
          case Failure(ex) =>
            log.error(ex, s"Validations failed")
            Future.failed(ex)
        }

      case None =>
        log.error(s"Flow ${ncfr.identificativoFlusso} unknown")
        Future.failed(exception.DigitPaException("Rendicontazione sconosciuta o non disponibile, riprovare in un secondo momento", DigitPaErrorCodes.PPT_ID_FLUSSO_SCONOSCIUTO))
    }
  }

  private def checkFlussoRendicontazione(idFlusso: String, idDominio: Option[String], idPsp: Option[String]): Future[Option[Rendicontazione]] = {
    repositories.fdrRepository.getRendicontazioneValidaByIfFlusso(idFlusso, idDominio, idPsp)
  }

  private def checkDatiRendicontazione(rendicontazione: Rendicontazione, idDominio: Option[String], idPsp: Option[String]) =
    Try({
      if (idDominio.isDefined && rendicontazione.dominio != idDominio.get || idPsp.isDefined && rendicontazione.psp != idPsp.get) {
        Failure(
          exception.DigitPaException(
            "Rendicontazione sconosciuta o non disponibile, riprovare in un secondo momento",
            DigitPaErrorCodes.PPT_ID_FLUSSO_SCONOSCIUTO
          )
        )
      } else {
        Success(())
      }
    })

  override def receive: Receive = { case soapRequest: SoapRequest =>
    req = soapRequest
    replyTo = sender()

    reFlow = Some(
      Re(
        componente = Componente.NDP_FDR.toString,
        categoriaEvento = CategoriaEvento.INTERNO.toString,
        sessionId = Some(req.sessionId),
        payload = None,
        esito = Some(EsitoRE.CAMBIO_STATO.toString),
        tipoEvento = Some(actorClassId),
        sottoTipoEvento = SottoTipoEvento.INTERN.toString,
        insertedTimestamp = soapRequest.timestamp,
        erogatore = Some(FaultId.NODO_DEI_PAGAMENTI_SPC),
        businessProcess = Some(actorClassId),
        erogatoreDescr = Some(FaultId.NODO_DEI_PAGAMENTI_SPC),
        flowAction = Some(req.primitive)
      )
    )
    log.info(FdrLogConstant.logSintattico(actorClassId))
    val pipeline = for {
      ncfr <- Future.fromTry(parseInput(soapRequest))

      _ = reFlow = reFlow.map(r =>
        r.copy(
          idDominio = ncfr.identificativoDominio,
          psp = ncfr.identificativoPSP,
          fruitore = Some(ncfr.identificativoStazioneIntermediarioPA),
          stazione = Some(ncfr.identificativoStazioneIntermediarioPA),
          esito = Some(EsitoRE.RICEVUTA.toString),
          flowName = Some(ncfr.identificativoFlusso)
        )
      )


      rendicontazioneNexi <- if (callNexiToo) {
        (for {
          _ <- Future.successful(())

          response <- HttpSoapServiceManagement.createRequestSoapAction(
            req.sessionId,
            req.testCaseId,
            req.primitive,
            SoapReceiverType.NEXI.toString,
            req.payload,
            actorProps,
            reFlow.get
          )

          ncfrResponse <- Future.fromTry(parseResponseNexi(response.payload.get))

          xmlRendicontazione <- if (ncfrResponse.isDefined) {
            for {
              _ <- Future.successful(())
              _ = ncfrResponse.get.fault.map(v => log.warn(s"Esito da ${SoapReceiverType.NEXI.toString}: faultCode=[${v.faultCode}, faultString=[${v.faultString}], description=[${v.description}]"))
            } yield ncfrResponse.get.xmlRendicontazione
          } else {
            Future.successful(None)
          }
        } yield xmlRendicontazione).recoverWith({
          case _ => Future.successful(None)
        })
      } else {
        Future.successful(None)
      }

      xmlrendicontazione <- if( rendicontazioneNexi.isDefined ) {
        log.info(s"Report [${ncfr.identificativoFlusso}] returned by ${SoapReceiverType.NEXI.toString}")
        Future.successful(rendicontazioneNexi)
      } else {
        log.info(s"No report returned by ${SoapReceiverType.NEXI.toString}")
        for {
          _ <- Future.successful(())
          _ = log.debug(s"Looking for reporting ${ncfr.identificativoFlusso} to db")
          (_, binaryFileOption, _, pa, staz, psp) <- checksSemanticiEDuplicati(ncfr)
          _ = reFlow = reFlow.map(r => r.copy(fruitoreDescr = Some(staz.stationCode), pspDescr = psp.flatMap(p => p.description)))
          _ = log.debug("Make response with reporting")
          rendicontazioneDb <- elaboraRisposta(binaryFileOption, pa)
        } yield rendicontazioneDb
      }
      _ = log.info(FdrLogConstant.logGeneraPayload(RESPONSE_NAME))
      ncfrResponse = NodoChiediFlussoRendicontazioneRisposta(None, xmlrendicontazione)

      resultMessage <- Future.fromTry(wrapInBundleMessage(ncfrResponse))
      sr = SoapResponse(req.sessionId, Some(resultMessage), StatusCodes.OK.intValue, reFlow, req.testCaseId, None)
    } yield sr

    pipeline.recover({
      case e: DigitPaException =>
        log.warn(e, FdrLogConstant.logGeneraPayload(s"negative $RESPONSE_NAME, [${e.getMessage}]"))
        errorHandler(req.sessionId, req.testCaseId, outputXsdValid, e, reFlow)
      case e: Throwable =>
        log.warn(e, FdrLogConstant.logGeneraPayload(s"negative $RESPONSE_NAME, [${e.getMessage}]"))
        errorHandler(req.sessionId, req.testCaseId, outputXsdValid, exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, e), reFlow)
    }) map (sr => {
      traceInterfaceRequest(soapRequest, reFlow.get, soapRequest.reExtra, reEventFunc, ddataMap)
      log.info(FdrLogConstant.logEnd(actorClassId))
      replyTo ! sr
      complete()
    })
  }

  private def parseResponseNexi(payloadResponse: String): Try[Option[NodoChiediFlussoRendicontazioneRisposta]] = {
    log.info(FdrLogConstant.logSintattico(s"${SoapReceiverType.NEXI.toString} $RESPONSE_NAME"))
    (for {
      _ <- XsdValid.checkOnly(payloadResponse, XmlEnum.NODO_CHIEDI_FLUSSO_RENDICONTAZIONE_RISPOSTA_NODOPERPA, inputXsdValid)
      body <- XmlEnum.str2nodoChiediFlussoRendicontazioneResponse_nodoperpa(payloadResponse)
    } yield Some(body)) recoverWith { case e =>
      log.error(e, e.getMessage)
      Success(None)
    }
  }

}
