package eu.sia.pagopa.rendicontazioni.actor

import eu.sia.pagopa.BootstrapUtil
import eu.sia.pagopa.Main.ConfigData
import eu.sia.pagopa.common.actor.PerRequestActor
import eu.sia.pagopa.common.exception
import eu.sia.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException}
import eu.sia.pagopa.common.message.{FTPRequest, FTPResponse}
import eu.sia.pagopa.common.repo.fdr.enums.{FtpFileStatus, RendicontazioneStatus}
import eu.sia.pagopa.common.repo.fdr.model.{BinaryFile, FtpFile, Rendicontazione}
import eu.sia.pagopa.common.repo.re.model.Re
import eu.sia.pagopa.common.util._
import eu.sia.pagopa.common.util.xml.XsdValid
import eu.sia.pagopa.commonxml.XmlEnum
import eu.sia.pagopa.rendicontazioni.util.CheckRendicontazioni
import it.pagopa.config.CreditorInstitution
import scalaxbmodel.flussoriversamento.CtFlussoRiversamento
import scalaxbmodel.nodoperpsp.NodoInviaFlussoRendicontazione

import java.io.{File, FileOutputStream}
import java.nio.file.{Files, Paths}
import java.security.MessageDigest
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait BaseFlussiRendicontazioneActor extends PerRequestActor {

  val fdrRepository = repositories.fdrRepository
  val checkUTF8: Boolean = context.system.settings.config.getBoolean("bundle.checkUTF8")
  val inputXsdValid: Boolean = Try(DDataChecks.getConfigurationKeys(ddataMap, "validate_input").toBoolean).getOrElse(false)
  val outputXsdValid: Boolean = Try(DDataChecks.getConfigurationKeys(ddataMap, "validate_output").toBoolean).getOrElse(false)

  def validateRendicontazione(
                               nifr: NodoInviaFlussoRendicontazione,
                               checkUTF8: Boolean,
                               inputXsdValid: Boolean
                             )(implicit log: NodoLogger, ec: ExecutionContext) = {
    log.debug("Check 'Flusso riversamento' element validity")

    for {
      content <- Future.fromTry(StringUtils.getStringDecoded(nifr.xmlRendicontazione, checkUTF8))
      r <- XsdValid.checkOnly(content, XmlEnum.FLUSSO_RIVERSAMENTO_FLUSSORIVERSAMENTO, inputXsdValid) match {
        case Success(_) =>
          log.debug("Saving valid report")

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
          log.warn(e, "Invalid spill stream 'Flusso riversamento' element")
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

  def saveRendicontazione(
                          identificativoFlusso: String,
                          identificativoPSP: String,
                          identificativoIntermediarioPSP: String,
                          identificativoCanale: String,
                          identificativoDominio: String,
                          dataOraFlusso: javax.xml.datatype.XMLGregorianCalendar,
                          xmlRendicontazione: scalaxb.Base64Binary,
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
          log.error("No FTP server configured")
          throw exception.DigitPaException(DigitPaErrorCodes.PPT_SYSTEM_ERROR)
        }
        val ftpServer = ftpServerConf.get._2

        val normalizedIdFlusso = s"${CheckRendicontazioni.normalizeIdFlusso(identificativoFlusso)}.xml"
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
          identificativoPSP,
          Some(identificativoIntermediarioPSP),
          Some(identificativoCanale),
          identificativoDominio,
          identificativoFlusso,
          LocalDateTime.parse(dataOraFlusso.toString, DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.systemDefault())),
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
          identificativoPSP,
          Some(identificativoIntermediarioPSP),
          Some(identificativoCanale),
          identificativoDominio,
          identificativoFlusso,
          LocalDateTime.parse(dataOraFlusso.toString, DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.systemDefault())),
          None,
          None
        )
        val bf = BinaryFile(0, xmlRendicontazione.length, Some(xmlRendicontazione.toArray), None, Some(content))
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

//  protected def inviaFlussoRendicontazioneRest2Soap(inviaFlussoRendicontazione: Flow)(implicit log: NodoLogger, ec: ExecutionContext) = {
//    for {
//      _ <- Future.successful(())
//      _ = log.info(FdrLogConstant.logGeneraPayload(s"nodoInviaFlussoRendicontazione SOAP"))
//
//      flussoRiversamento = CtFlussoRiversamento(
//        Number1u461,
//        inviaFlussoRendicontazione.reportingFlowName,
//        DatatypeFactory.newInstance().newXMLGregorianCalendar(inviaFlussoRendicontazione.reportingFlowDate),
//        inviaFlussoRendicontazione.regulation,
//        DatatypeFactory.newInstance().newXMLGregorianCalendar(inviaFlussoRendicontazione.regulationDate),
//        CtIstitutoMittente(
//          CtIdentificativoUnivoco(
//            inviaFlussoRendicontazione.sender._type match {
//              case SenderTypeEnum.ABI_CODE => scalaxbmodel.flussoriversamento.A
//              case SenderTypeEnum.BIC_CODE => scalaxbmodel.flussoriversamento.B
//              case _ => scalaxbmodel.flussoriversamento.GValue
//            },
//            inviaFlussoRendicontazione.sender.id
//          ),
//          Some(inviaFlussoRendicontazione.sender.pspName)
//        ),
//        Some(inviaFlussoRendicontazione.bicCodePouringBank),
//        CtIstitutoRicevente(
//          CtIdentificativoUnivocoPersonaG(
//            scalaxbmodel.flussoriversamento.G,
//            inviaFlussoRendicontazione.receiver.id
//          ),
//          Some(inviaFlussoRendicontazione.receiver.ecName)
//        ),0,0
////        inviaFlussoRendicontazione.payments.size,
////        inviaFlussoRendicontazione.payments.map(_.singoloImportoPagato).sum
//      )
//
//      flussoRiversamentoEncoded <- Future.fromTry(XmlEnum.FlussoRiversamento2Str_flussoriversamento(flussoRiversamento))
//
//      nodoInviaFlussoRendicontazione = NodoInviaFlussoRendicontazione(
//        inviaFlussoRendicontazione.sender.pspId,
//        inviaFlussoRendicontazione.sender.brokerId,
//        inviaFlussoRendicontazione.sender.channelId,
//        inviaFlussoRendicontazione.sender.password,
//        inviaFlussoRendicontazione.receiver.ecId,
//        inviaFlussoRendicontazione.reportingFlowName,
//        DatatypeFactory.newInstance().newXMLGregorianCalendar(inviaFlussoRendicontazione.reportingFlowDate),
//        XmlUtil.StringBase64Binary.encodeBase64(flussoRiversamentoEncoded)
//      )
//
//      requestString <- Future.fromTry(XmlEnum.nodoInviaFlussoRendicontazione2Str_nodoperpsp(nodoInviaFlussoRendicontazione))
//
//    } yield requestString
//  }

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
          checkPassword
        )
        .map(pc => pc._1 -> pc._3)
      pa <- DDataChecks.checkPA(log, ddataMap, nodoInviaFlussoRendicontazione.identificativoDominio)
    } yield (pa, psp, canale)

    paaa.recoverWith({
      case ex: DigitPaException =>
        Failure(ex)
      case ex@_ =>
        Failure(exception.DigitPaException(ex.getMessage, DigitPaErrorCodes.PPT_SINTASSI_XSD, ex))
    })
  }

  def checkFormatoIdFlussoRendicontazione(identificativoFlusso: String, idPsp: String) = {
    log.info(FdrLogConstant.logSemantico(actorClassId))
    (for {
      _ <- CheckRendicontazioni.checkFormatoIdFlussoRendicontazione(identificativoFlusso, idPsp)
    } yield ()).recoverWith({
      case ex: DigitPaException =>
        Failure(ex)
      case ex@_ =>
        Failure(exception.DigitPaException(ex.getMessage, DigitPaErrorCodes.PPT_SINTASSI_XSD, ex))
    })
  }

  protected def notifySFTPSender(pa: CreditorInstitution, sessionId: String, testCaseId: Option[String], file: FtpFile): Future[FTPResponse] = {
    log.info(s"SFTP Request pushFile")

    val ftpServerConf = ddataMap.ftpServers.find(s => {
      s._2.service == Constant.KeyName.RENDICONTAZIONI
    }).get

    askBundle[FTPRequest, FTPResponse](
      actorProps.routers(BootstrapUtil.actorRouter(Constant.KeyName.FTP_SENDER)),
      FTPRequest(sessionId, testCaseId, "pushFileRendicontazioni", pa.creditorInstitutionCode, file.fileName, file.id, ftpServerConf._2.id)
    )
  }

}
