package eu.sia.pagopa.rendicontazioni.actor

import eu.sia.pagopa.Main.ConfigData
import eu.sia.pagopa.common.actor.NodoLogging
import eu.sia.pagopa.common.exception
import eu.sia.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException}
import eu.sia.pagopa.common.repo.fdr.FdrRepository
import eu.sia.pagopa.common.repo.fdr.enums.{FtpFileStatus, RendicontazioneStatus}
import eu.sia.pagopa.common.repo.fdr.model.{BinaryFile, FtpFile, Rendicontazione}
import eu.sia.pagopa.common.util._
import eu.sia.pagopa.common.util.xml.XmlUtil.StringBase64Binary
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
import scala.util.{Failure, Success}

trait BaseFlussiRendicontazioneActor { this: NodoLogging =>

  def validateRendicontazione(
                               nifr: NodoInviaFlussoRendicontazione,
                               checkUTF8: Boolean,
                               inputXsdValid: Boolean,
                               fdrRepository: FdrRepository
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
                          actorClassId: String,
                          fdrRepository: FdrRepository)(implicit log: NodoLogger, ec: ExecutionContext) = {

    for {
      r <- if (pa.reportingFtp) {
        val ftpServerConf = ddataMap.ftpServers
          .find(s => {
            s._2.service == Constant.KeyName.RENDICONTAZIONI
          })
        if (ftpServerConf.isEmpty) {
          log.error("No FTP server configured")
          throw exception.DigitPaException("No FTP server configured", DigitPaErrorCodes.PPT_SYSTEM_ERROR)
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
        val bf = BinaryFile(0, xmlRendicontazione.length, Some(Util.zipContent(xmlRendicontazione.toArray)), None, Some(StringBase64Binary.encodeBase64(Util.zipContent(content.getBytes)).toString))
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

  def checkFormatoIdFlussoRendicontazione(identificativoFlusso: String, idPsp: String, actorClassId: String)(implicit log: NodoLogger) = {
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

}
