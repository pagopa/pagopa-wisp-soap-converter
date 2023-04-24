package eu.sia.pagopa.rendicontazioni.actor

import eu.sia.pagopa.Main.ConfigData
import eu.sia.pagopa.common.actor.PerRequestActor
import eu.sia.pagopa.common.exception
import eu.sia.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException}
import eu.sia.pagopa.common.json.model.rendicontazione.{InviaFlussoRendicontazioneRequest, SenderTypeEnum}
import eu.sia.pagopa.common.repo.fdr.enums.{FtpFileStatus, RendicontazioneStatus}
import eu.sia.pagopa.common.repo.fdr.model.{BinaryFile, FtpFile, Rendicontazione}
import eu.sia.pagopa.common.repo.re.model.Re
import eu.sia.pagopa.common.util._
import eu.sia.pagopa.common.util.xml.{XmlUtil, XsdValid}
import eu.sia.pagopa.commonxml.XmlEnum
import eu.sia.pagopa.rendicontazioni.util.CheckRendicontazioni
import it.pagopa.config.CreditorInstitution
import scalaxbmodel.flussoriversamento.{CtFlussoRiversamento, CtIdentificativoUnivoco, CtIdentificativoUnivocoPersonaG, CtIstitutoMittente, CtIstitutoRicevente, Number1u461}
import scalaxbmodel.nodoperpsp.NodoInviaFlussoRendicontazione

import java.io.{File, FileOutputStream}
import java.nio.file.{Files, Paths}
import java.security.MessageDigest
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}
import java.util.zip.{ZipEntry, ZipOutputStream}
import javax.xml.datatype.DatatypeFactory
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait BaseInviaFlussoRendicontazioneActor extends PerRequestActor {

  val fdrRepository = repositories.fdrRepository
  val checkUTF8: Boolean = context.system.settings.config.getBoolean("bundle.checkUTF8")
  val inputXsdValid: Boolean = DDataChecks.getConfigurationKeys(ddataMap, "validate_input").toBoolean
  val outputXsdValid: Boolean = DDataChecks.getConfigurationKeys(ddataMap, "validate_output").toBoolean
  var re: Option[Re] = None


  def validateRendicontazione(
                               nifr: NodoInviaFlussoRendicontazione,
                               checkUTF8: Boolean,
                               inputXsdValid: Boolean
                             )(implicit log: NodoLogger, ec: ExecutionContext) = {
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
          Future.successful(flussoRiversamento, content)

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
          fdrRepository
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

  def saveRendicontazione(nifr: NodoInviaFlussoRendicontazione,
                          content: String,
                          flussoRiversamento: CtFlussoRiversamento,
                          pa: CreditorInstitution,
                          ddataMap: ConfigData,
                          actorClassId: String)(implicit log: NodoLogger) = {

    for {
      r <- if (pa.reportingFtp) {
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
        fdrRepository
          .save(rendi, sftpFile)
          .recoverWith({ case e =>
            Future.failed(exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR, e))
          })
          .flatMap(data => {
            Future.successful((Constant.OK, data._1, Some(data._2), flussoRiversamento))
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
        fdrRepository
          .saveRendicontazioneAndBinaryFile(rendi, bf)
          .recoverWith({ case e =>
            Future.failed(exception.DigitPaException("Errore salvataggio rendicontazione", DigitPaErrorCodes.PPT_SYSTEM_ERROR, e))
          })
          .flatMap(data => {
            Future.successful((Constant.OK, data._1, None, flussoRiversamento))
          })
      }
    } yield r

  }

  def translateInviaFlussoRendicontazioneRest2Soap(inviaFlussoRendicontazione: InviaFlussoRendicontazioneRequest)(implicit log: NodoLogger, ec: ExecutionContext) = {
    for {
      _ <- Future.successful(())
      _ = log.info(FdrLogConstant.logGeneraPayload(s"nodoInviaFlussoRendicontazione SOAP"))

      flussoRiversamento = CtFlussoRiversamento(
        Number1u461,
        inviaFlussoRendicontazione.reportingFlow,
        DatatypeFactory.newInstance().newXMLGregorianCalendar(inviaFlussoRendicontazione.dateReportingFlow),
        inviaFlussoRendicontazione.regulation,
        DatatypeFactory.newInstance().newXMLGregorianCalendar(inviaFlussoRendicontazione.dateRegulation),
        CtIstitutoMittente(
          CtIdentificativoUnivoco(
            inviaFlussoRendicontazione.sender._type match {
              case SenderTypeEnum.CODICE_ABI => scalaxbmodel.flussoriversamento.A
              case SenderTypeEnum.CODICE_BIC => scalaxbmodel.flussoriversamento.B
              case _ => scalaxbmodel.flussoriversamento.GValue
            },
            inviaFlussoRendicontazione.sender.id
          ),
          inviaFlussoRendicontazione.sender.name
        ),
        inviaFlussoRendicontazione.bicCodePouringBank,
        CtIstitutoRicevente(
          CtIdentificativoUnivocoPersonaG(
            scalaxbmodel.flussoriversamento.G,
            inviaFlussoRendicontazione.receiver.id
          ),
          inviaFlussoRendicontazione.receiver.nameEc
        ),
        inviaFlussoRendicontazione.payments.size,
        inviaFlussoRendicontazione.payments.map(_.singoloImportoPagato).sum
      )

      flussoRiversamentoEncoded <- Future.fromTry(XmlEnum.FlussoRiversamento2Str_flussoriversamento(flussoRiversamento))

      nodoInviaFlussoRendicontazione = NodoInviaFlussoRendicontazione(
        inviaFlussoRendicontazione.sender.idPsp,
        inviaFlussoRendicontazione.sender.idBroker,
        inviaFlussoRendicontazione.sender.idChannel,
        "", //TODO
        inviaFlussoRendicontazione.receiver.idEc,
        inviaFlussoRendicontazione.reportingFlow,
        DatatypeFactory.newInstance().newXMLGregorianCalendar(inviaFlussoRendicontazione.dateReportingFlow),
        XmlUtil.StringBase64Binary.encodeBase64(flussoRiversamentoEncoded)
      )

      requestString <- Future.fromTry(XmlEnum.nodoInviaFlussoRendicontazione2Str_nodoperpsp(nodoInviaFlussoRendicontazione))

    } yield requestString
  }

  def parseNodoInviaFlussoRendicontazione(payload: String, inputXsdValid: Boolean)(implicit log: NodoLogger) = {
    log.debug("Parserizzazione input")
    (for {
      _ <- XsdValid.checkOnly(payload, XmlEnum.NODO_INVIA_FLUSSO_RENDICONTAZIONE_NODOPERPSP, inputXsdValid)
      body <- XmlEnum.str2nodoInviaFlussoRendicontazione_nodoperpsp(payload)
      _ = log.debug("Payload parserizzato correttamente")
    } yield body) recoverWith { case e =>
      log.warn(e, s"${e.getMessage}")
      val cfb = exception.DigitPaException(e.getMessage, DigitPaErrorCodes.PPT_SINTASSI_EXTRAXSD, e)
      Failure(cfb)
    }
  }

  def checks(ddataMap: ConfigData, nodoInviaFlussoRendicontazione: NodoInviaFlussoRendicontazione, checkPassword: Boolean, actorClassId: String)(implicit log: NodoLogger) = {
    log.info(FdrLogConstant.logSemantico(actorClassId))
    val paaa = for {
      (psp, canale) <- DDataChecks
        .checkPspIntermediarioPspCanale(
          log,
          ddataMap,
          Some(nodoInviaFlussoRendicontazione.identificativoPSP),
          nodoInviaFlussoRendicontazione.identificativoIntermediarioPSP,
          Some(nodoInviaFlussoRendicontazione.identificativoCanale),
          Some(nodoInviaFlussoRendicontazione.password),
          None,
          checkPassword
        )
        .map(pc => pc._1 -> pc._3)
      pa <- DDataChecks.checkPA(log, ddataMap, nodoInviaFlussoRendicontazione.identificativoDominio)
      _ <- CheckRendicontazioni.checkFormatoIdFlussoRendicontazione(nodoInviaFlussoRendicontazione.identificativoFlusso, nodoInviaFlussoRendicontazione.identificativoPSP)
    } yield (pa, psp, canale)

    paaa.recoverWith({
      case ex: DigitPaException =>
        Failure(ex)
      case ex@_ =>
        Failure(exception.DigitPaException(ex.getMessage, DigitPaErrorCodes.PPT_SINTASSI_XSD, ex))
    })
  }

  def parseInput(payload: String, inputXsdValid: Boolean): Try[NodoInviaFlussoRendicontazione] = {
    log.info(FdrLogConstant.logSintattico(actorClassId))
    (for {
      _ <- XsdValid.checkOnly(payload, XmlEnum.NODO_INVIA_FLUSSO_RENDICONTAZIONE_NODOPERPSP, inputXsdValid)
      body <- XmlEnum.str2nodoInviaFlussoRendicontazione_nodoperpsp(payload)
      _ = log.debug("Richiesta validata correttamente")
    } yield body) recoverWith { case e =>
      log.warn(e, s"${e.getMessage}")
      val cfb = exception.DigitPaException(e.getMessage, DigitPaErrorCodes.PPT_SINTASSI_EXTRAXSD, e)
      Failure(cfb)
    }
  }

}
