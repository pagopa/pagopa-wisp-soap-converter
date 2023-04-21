package eu.sia.pagopa.ftpsender.actor

import akka.actor.ActorRef
import eu.sia.pagopa.ActorProps
import eu.sia.pagopa.common.actor.PerRequestActor
import eu.sia.pagopa.common.exception.DigitPaException
import eu.sia.pagopa.common.message._
import eu.sia.pagopa.common.repo.Repositories
import eu.sia.pagopa.common.repo.fdr.enums.{FtpFileStatus, SchedulerFireExitStatus}
import eu.sia.pagopa.common.util._
import eu.sia.pagopa.ftpsender.util.{FTPFailureReason, FtpSenderException, SSHFuture}
import fr.janalyse.ssh.{SSH, SSHFtp, SSHOptions}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

case class FTPRetryRequest(sessionId: String, messageType: String, ftpServerId: Long, fileIds: Seq[Long])

final case class FtpRetryActorPerRequest(repositories: Repositories, actorProps: ActorProps) extends PerRequestActor {

  val fdrRepository = repositories.fdrRepository

  override def actorError(dpe: DigitPaException): Unit = {
    log.error(dpe, s"${dpe.getMessage}")
    context.stop(context.self)
  }

  val ftpConnectTimeout: Long = context.system.settings.config.getLong("config.ftp.connect-timeout")

  var req: FTPRetryRequest = _
  var replyTo: ActorRef = _

  val maxRetry: Int = DDataChecks.getConfigurationKeys(ddataMap, "scheduler.ftpUploadRetryPollerMaxRetry").toInt
  val envPath: String = DDataChecks.getConfigurationKeys(ddataMap, "ftp.env.path")

  val limitJobsDays: Long = Try(context.system.settings.config.getLong("limitDays")).getOrElse(90)

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

  override def receive: Receive = { case sch: WorkRequest =>
    log.info(s"Ricevuto messaggio FTPRetry[${sch.sessionId}]")
    replyTo = sender()

    val jobName = Jobs.FTP_UPLOAD_RETRY.name
    val maxRetry: Int =
      DDataChecks.getConfigurationKeys(actorProps.ddataMap, "scheduler.ftpUploadRetryPollerMaxRetry").toInt

    val enabled = JobUtil.isJobEnabled(log, ddataMap, jobName)
    val suspend = JobUtil.areJobsSuspended(log, ddataMap)

    val pipeline = if (enabled && !suspend) {
      for {
        status <- fdrRepository.fireJobIfNotRunning(jobName, Constant.KeyName.EMPTY_KEY)
        _ <- status match {
          case SchedulerFireExitStatus.JOB_STARTED =>
            val timeNow = Util.now()
            val timeLimit = timeNow.minusDays(limitJobsDays)
            for {
              filesIdServerId <- fdrRepository.findToRetry(Constant.Sftp.RENDICONTAZIONI, maxRetry, timeLimit)
              grouped = filesIdServerId.groupBy(_._1)
              _ = log.info(s"Riepilogo file trovati da caricare:\n${grouped.map(g => s"Server[${g._1}],numero files[${g._2.size}]").mkString("\n")}")
              subreqs = grouped.map(f => FTPRetryRequest(sch.sessionId, Constant.Sftp.RENDICONTAZIONI, f._1, f._2.map(_._2)))
              _ <- FutureUtils.groupedSerializeFuture(log, subreqs, 50)(d => uploadFile(d.sessionId, d.messageType, d.ftpServerId, d.fileIds))
              _ <- fdrRepository
                .stopRunningJob(jobName, Constant.KeyName.EMPTY_KEY)
                .recover({ case e =>
                  log.error(e, "Errore allo stop del job")
                })
            } yield ()
          case SchedulerFireExitStatus.JOB_RUNNING =>
            Future.successful(())
        }
      } yield ()
    } else {
      Future.successful(())
    }

    pipeline
      .recoverWith({ case cause: Throwable =>
        log.warn(cause, s"Errore durante ${actorClassId}, message: [${cause.getMessage}]")
        for {
          _ <- fdrRepository
            .stopRunningJob(jobName, Constant.KeyName.EMPTY_KEY)
            .recover({ case e =>
              log.error(e, "Errore allo stop del job")
            })
        } yield ()
      })
      .map({ _ =>
        replyTo ! WorkResponse(sch.sessionId, sch.testCaseId, None, None)
        complete()
      })
  }

  def uploadFile(sessionId: String, tipo: String, ftpServerId: Long, fileIds: Seq[Long]): Future[Unit] = {
    log.info(FdrLogConstant.logStart(actorClassId) + "-per-request")

    log.debug(s"Ricevuta request FTPRetryRequest[${sessionId}][$tipo]")
    val ftpconfigopt =
      ddataMap.ftpServers.find(f => f._2.id == ftpServerId)

    if (ftpconfigopt.isEmpty) {
      throw FtpSenderException(s"Configurazione non trovata su database id[${ftpServerId}]", FTPFailureReason.CONFIGURATION)
    }

    val pipeline = for {
      ftpconfig <- Future.successful(ftpconfigopt.get._2)
      _ = log.info(FdrLogConstant.logSemantico(Constant.KeyName.FTP_RETRY) + "-per-request")
      _ = log.debug(s"Recupero files da DB")
      files <- fdrRepository.findFtpFilesByIds(fileIds, tipo)

      _ = log.debug(s"Recuperati ${files.size} files")
      inPath =
        if (ftpconfig.inPath.endsWith("/")) {
          ftpconfig.inPath.substring(0, ftpconfig.inPath.lastIndexOf("/"))
        } else {
          ftpconfig.inPath
        }

      filesWithData = files.map(f => {
        val destpath = s"$inPath/$envPath${f.path}"
        val destfile = s"$destpath/${f.fileName}"
        (f, destpath, destfile)
      })

      opts = SSHOptions(host = ftpconfig.host, port = ftpconfig.port, username = ftpconfig.username, password = ftpconfig.password, connectTimeout = ftpConnectTimeout, timeout = 1)

      _ = log.debug(s"Connessione al server")

      ssh <- Future(new SSH(opts))
      _ <- SSHFuture.ftp(ssh) { ftp: SSHFtp =>
        Future.sequence(filesWithData.map(f => {
          Try {
            log.info(s"File [${f._3}]")
            log.debug(s"Controllo esistenza directory di destinazione")
            createDirs(f._2)(ftp)
            log.info(s"Caricamento file in corso")
            ftp.putBytes(f._1.content, f._3)
            log.debug(s"Caricamento file completato")
          } match {
            case Success(_) =>
              log.debug(s"Aggiornamento stato file a ${FtpFileStatus.UPLOADED}")
              fdrRepository.updateFileStatus(f._1.id, FtpFileStatus.UPLOADED, tipo, actorClassId)
            case Failure(e) =>
              log.error(e, s"Errore upload file [id:${f._1.id},path:${f._3}]")
              fdrRepository.updateFileRetry(f._1, tipo, actorClassId)
          }
        }))
      }
    } yield ()
    pipeline.recover({ case e =>
      log.error(e, "Errore upload SFTP")
    })
  }

}
