package eu.sia.pagopa.ftpsender.actor

import akka.actor.ActorRef
import eu.sia.pagopa.ActorProps
import eu.sia.pagopa.common.actor.PerRequestActor
import eu.sia.pagopa.common.exception
import eu.sia.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException}
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.repo.Repositories
import eu.sia.pagopa.common.repo.fdr.enums.FtpFileStatus
import eu.sia.pagopa.common.util._
import eu.sia.pagopa.ftpsender.util.{FTPFailureReason, FtpSenderException, SSHFuture}
import fr.janalyse.ssh.{SSH, SSHFtp, SSHOptions}
import org.slf4j.MDC

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

final case class FtpSenderActorPerRequest(repositories: Repositories, actorProps: ActorProps) extends PerRequestActor with FtpSenderResponse {

  var req: FTPRequest = _
  var replyTo: ActorRef = _

  val ftpConnectTimeout: Long = context.system.settings.config.getLong("config.ftp.connect-timeout")

  val envPath: String = DDataChecks.getConfigurationKeys(ddataMap, "ftp.env.path")

  def receive: Receive = { case r @ FTPRequest(sessionId, testCaseId, tipo, destinationPath, filename, fileId, ftpServerId) =>
    replyTo = sender()
    req = r
    implicit val ec: ExecutionContext = context.system.dispatcher

    log.debug(s"Received [$tipo] -> $destinationPath - $filename")
    tipo match {
      case Constant.Sftp.RENDICONTAZIONI =>
        val sendto = sender()
        val ftpconfigopt = ddataMap.ftpServers.find(f => f._2.id == ftpServerId)

        if (ftpconfigopt.isEmpty) {
          throw FtpSenderException(s"FTP configuration not found", FTPFailureReason.CONFIGURATION)
        }

        val pipeline = for {
          ftpconfig <- Future.successful(ftpconfigopt.get._2)
          _ = log.info(FdrLogConstant.logSemantico(Constant.KeyName.FTP_SENDER))
          _ <- Future(validateInput(filename))
          _ = log.debug(s"File recovery from DB with fileId=[$fileId]")
          file <- repositories.fdrRepository.findFtpFileById(fileId, tipo).flatMap {
            case Some(b) =>
              Future.successful(b)
            case None =>
              val message = s"File not found on database"
              log.info(message)
              Future.failed(DigitPaException(message, DigitPaErrorCodes.PPT_SYSTEM_ERROR))
          }
          inPath =
            if (ftpconfig.inPath.endsWith("/")) {
              ftpconfig.inPath.substring(0, ftpconfig.inPath.lastIndexOf("/"))
            } else {
              ftpconfig.inPath
            }
          destpath = s"$inPath/$envPath$destinationPath"
          destfile = s"$destpath/$filename"
          _ = log.debug(s"File to upload to [$destfile]")

          opts = SSHOptions(host = ftpconfig.host, port = ftpconfig.port, username = ftpconfig.username, password = ftpconfig.password, connectTimeout = ftpConnectTimeout)

          _ = log.debug(s"Connecting to the server [${opts.host}]")

          ssh <- Future(new SSH(opts)).recover({ case e =>
            log.error(e, s"Unable to establish connection to SFTP server [$opts]")
            throw e
          })
          _ <- SSHFuture.ftp(ssh) { ftp: SSHFtp =>
            Try({
              log.debug(s"Destination directory existence check")
              createDirs(destpath)(ftp)
              log.info(s"File upload in progress")
              ftp.putBytes(file.content, destfile)
              log.debug(s"File upload completed")
            }) match {
              case Success(_) =>
                log.debug(s"File status update to UPLOADED")
                repositories.fdrRepository.updateFileStatus(file.id, FtpFileStatus.UPLOADED, tipo, actorClassId)
              case Failure(ex) =>
                log.error(ex, "File upload error")
                repositories.fdrRepository.updateFileRetry(file, tipo, actorClassId)
            }
          }
        } yield FTPResponse(sessionId, testCaseId, None)

        pipeline
          .recoverWith { case ex: Throwable =>
            log.warn(ex, s"FTP sender error:${ex.getMessage}")
            Future.successful(FTPResponse(sessionId, testCaseId, Some(DigitPaErrorCodes.PPT_SYSTEM_ERROR)))
          }
          .map(resp => {
            log.info(FdrLogConstant.logEnd(actorClassId))
            sendto ! resp
            complete()
          })

      case m @ _ =>
        log.warn(s"Messagge Type not managed: [$m]")
        replyTo ! FTPResponse(sessionId, testCaseId, Some(exception.DigitPaException("Tipo messaggio non gestito: [$m]", DigitPaErrorCodes.PPT_SYSTEM_ERROR)))
        complete()
    }

  }

  def validateInput(filename: String): Unit =
    if (filename.isEmpty) throw FtpSenderException("Filename non valido", FTPFailureReason.VALIDATION)

  private def createDirs(destPath: String)(implicit ftp: SSHFtp): Unit = {
    destPath
      .split("/")
      .map(dir => {
        if (dir.nonEmpty) {
          Try({
            ftp.mkdir(dir)
            ftp.cd(dir)
          }).recoverWith({ case _: Throwable =>
            ftp.cd(dir)
            Success(false)
          }).map(_ => true)
        } else {
          Success(false)
        }
      })
    ftp.cd("/")
  }

  override def actorError(dpe: DigitPaException): Unit = {
    MDC.put(Constant.MDCKey.SESSION_ID, req.sessionId)
    val response = FTPResponse(req.sessionId, Some(dpe.getMessage), None)
    replyTo ! response
  }

}
