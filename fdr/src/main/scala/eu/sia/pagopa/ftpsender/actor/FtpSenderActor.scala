package eu.sia.pagopa.ftpsender.actor

import akka.actor.ActorRef
import eu.sia.pagopa.ActorProps
import eu.sia.pagopa.common.actor.PerRequestActor
import eu.sia.pagopa.common.exception
import eu.sia.pagopa.common.exception.{DigitPaErrorCodes, DigitPaException}
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.repo.Repositories
import eu.sia.pagopa.common.repo.offline.enums.FtpFileStatus
import eu.sia.pagopa.common.util._
import eu.sia.pagopa.ftpsender.util.{FTPFailureReason, FtpSenderException, SSHFuture}
import fr.janalyse.ssh.{SSH, SSHFtp, SSHOptions}
import org.slf4j.MDC

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

final case class FtpSenderActorPerRequest(repositories: Repositories, actorProps: ActorProps) extends PerRequestActor with FtpSenderResponse with ReUtil {

  val offlineRepository = repositories.offlineRepository

  var req: FTPRequest = _
  var replyTo: ActorRef = _

  val ftpConnectTimeout: Long = context.system.settings.config.getLong("config.ftp.connect-timeout")

  val envPath: String = DDataChecks.getConfigurationKeys(ddataMap, "ftp.env.path")

  def receive: Receive = { case r @ FTPRequest(sessionId, testCaseId, tipo, destinationPath, filename, fileId, ftpServerId) =>
    replyTo = sender()
    req = r
    implicit val ec: ExecutionContext = context.system.dispatcher

    log.debug(s"Ricevuta request $tipo -> $destinationPath - $filename")
    tipo match {
      case Constant.Sftp.RENDICONTAZIONI =>
        val sendto = sender()
        val ftpconfigopt = ddataMap.ftpServers.find(f => f._2.id == ftpServerId)

        if (ftpconfigopt.isEmpty) {
          throw FtpSenderException(s"Configurazione non trovata su database", FTPFailureReason.CONFIGURATION)
        }

        val pipeline = for {
          ftpconfig <- Future.successful(ftpconfigopt.get._2)
          _ = log.info(NodoLogConstant.logSemantico(Constant.KeyName.FTP_SENDER))
          _ <- Future(validateInput(filename))
          _ = log.debug(s"Recupero file da DB:fileId[$fileId]")
          file <- offlineRepository.findFtpFileById(fileId, tipo).flatMap {
            case Some(b) =>
              Future.successful(b)
            case None =>
              log.info("File non trovato su database")
              Future.failed(DigitPaException("File non trovato su database", DigitPaErrorCodes.PPT_SYSTEM_ERROR))
          }
          inPath =
            if (ftpconfig.inPath.endsWith("/")) {
              ftpconfig.inPath.substring(0, ftpconfig.inPath.lastIndexOf("/"))
            } else {
              ftpconfig.inPath
            }
          destpath = s"$inPath/$envPath$destinationPath"
          destfile = s"$destpath/$filename"
          _ = log.debug(s"File da caricare in [$destfile]")

          opts = SSHOptions(host = ftpconfig.host, port = ftpconfig.port, username = ftpconfig.username, password = ftpconfig.password, connectTimeout = ftpConnectTimeout)

          _ = log.debug(s"Connessione al server")

          ssh <- Future(new SSH(opts)).recover({ case e =>
            log.error(e, s"Impossibile stabilire una connessione col server sftp [$opts]")
            throw e
          })
          _ <- SSHFuture.ftp(ssh) { ftp: SSHFtp =>
            Try({
              log.debug(s"Controllo esistenza directory di destinazione")
              createDirs(destpath)(ftp)
              log.info(s"Caricamento file in corso")
              ftp.putBytes(file.content, destfile)
              log.debug(s"Caricamento file completato")
            }) match {
              case Success(_) =>
                log.debug(s"Aggiornamento stato file a UPLOADED")
                offlineRepository.updateFileStatus(file.id, FtpFileStatus.UPLOADED, tipo, actorClassId)
              case Failure(ex) =>
                log.error(ex, "Errore caricamento file")
                offlineRepository.updateFileRetry(file, tipo, actorClassId)
            }
          }
        } yield FTPResponse(sessionId, testCaseId, None)

        pipeline
          .recoverWith { case ex: Throwable =>
            log.warn(ex, s"errore ftp sender:${ex.getMessage}")
            Future.successful(FTPResponse(sessionId, testCaseId, Some(DigitPaErrorCodes.PPT_SYSTEM_ERROR)))
          }
          .map(resp => {
            log.info(NodoLogConstant.logEnd(actorClassId))
            sendto ! resp
            complete()
          })

      case m @ _ =>
        log.warn(s"Messagge Type non gestito: [$m]")
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

  //  protected def serializeFuture[A, B](l: Iterable[A])(fn: A => Future[B])(implicit ec: ExecutionContext): Future[Seq[B]] = {
  //    l.foldLeft(Future(Seq.empty[B])) {
  //      (previousFuture, next) =>
  //        for {
  //          previousResults <- previousFuture
  //          nextResult <- fn(next)
  //        } yield previousResults :+ nextResult
  //    }
  //  }

  override def actorError(dpe: DigitPaException): Unit = {
    MDC.put(Constant.MDCKey.SESSION_ID, req.sessionId)
    MDC.put(Constant.MDCKey.SERVICE_IDENTIFIER, Constant.SERVICE_IDENTIFIER)
    val response = FTPResponse(req.sessionId, Some(dpe.getMessage), None)
    replyTo ! response
    //MDC.remove(Constant.MDCKey.SESSION_ID)
  }

}
