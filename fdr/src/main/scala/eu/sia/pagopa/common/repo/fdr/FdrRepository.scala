package eu.sia.pagopa.common.repo.fdr

import eu.sia.pagopa.common.message.SchedulerStatus
import eu.sia.pagopa.common.repo.DBComponent
import eu.sia.pagopa.common.repo.fdr.enums._
import eu.sia.pagopa.common.repo.fdr.model._
import eu.sia.pagopa.common.repo.fdr.table._
import eu.sia.pagopa.common.util.{Constant, NodoLogger, Util}
import slick.dbio.DBIOAction
import slick.jdbc.{JdbcBackend, JdbcProfile}

import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.{ExecutionContext, Future}

case class FdrRepository(override val driver: JdbcProfile, override val db: JdbcBackend#DatabaseDef)(implicit ec: ExecutionContext)
    extends DBComponent
    with FdrMapping
    with BinaryFileTable
    with SftpFilesTable
    with SchedulerFireCheckTable
    with SchedulerTraceTable
    with RendicontazioneTable {

  import driver.api._

  private val insertBinaryFile =
    binaryFilesTable returning binaryFilesTable.map(_.objId) into ((item, id) => item.copy(objId = id))
  private final val MINUS_MINUTES = 10

  val compBinaryFileById = Compiled((id: Rep[Long]) => binaryFilesTable.filter(_.objId === id))
  def binaryFileById(id: Long)(implicit log: NodoLogger): Future[Option[BinaryFile]] = {
    val param = Seq("id" -> id.toString)

    val action = for {
      b <- compBinaryFileById(id).result.headOption
    } yield b
    run("binaryFileById", param, action)
  }

  val compFindToRetryRend =
    Compiled((ftpFileStatus: Rep[FtpFileStatus.Value], nowMinus10Minutes: Rep[LocalDateTime], retries: Rep[Int], timeLimit: Rep[LocalDateTime]) =>
      sendRendicontazione
        .filter(f => f.stato === ftpFileStatus)
        .filter(f => f.insertedDate < nowMinus10Minutes && f.insertedDate >= timeLimit)
        .filter(f => f.retry < retries)
        .map(f => f.serverId -> f.id)
    )
  val compFindToRetryRendExists =
    Compiled((ftpFileStatus: Rep[FtpFileStatus.Value], nowMinus10Minutes: Rep[LocalDateTime], retries: Rep[Int], timeLimit: Rep[LocalDateTime]) =>
      sendRendicontazione.filter(f => f.stato === ftpFileStatus).filter(f => f.insertedDate < nowMinus10Minutes && f.insertedDate >= timeLimit).filter(f => f.retry < retries).exists
    )

  def findToRetry(tipo: String, retries: Int, timeLimit: LocalDateTime)(implicit log: NodoLogger): Future[Seq[(Long, Long)]] = {
    val nowMinus10Minutes = Util.now().minusMinutes(MINUS_MINUTES)
    val ftpFileStatus = FtpFileStatus.TO_UPLOAD
    val param = Seq("tipo" -> tipo, "retries" -> retries, "nowMinus10Minutes" -> nowMinus10Minutes, "ftpFileStatus" -> ftpFileStatus)

    val action = for {
      q <- compFindToRetryRend(ftpFileStatus, nowMinus10Minutes, retries, timeLimit).result
    } yield q
    run("findToRetry", param, action)
  }

  def existsToRetry(retries: Int, timeLimit: LocalDateTime)(implicit log: NodoLogger): Future[Boolean] = {
    val nowMinus10Minutes = Util.now().minusMinutes(MINUS_MINUTES)
    val ftpFileStatus = FtpFileStatus.TO_UPLOAD
    val param = Seq("retries" -> retries, "nowMinus10Minutes" -> nowMinus10Minutes, "ftpFileStatus" -> ftpFileStatus, "timeLimit" -> timeLimit)

    val action = for {
      q <- compFindToRetryRendExists(ftpFileStatus, nowMinus10Minutes, retries, timeLimit).result
    } yield q

    run("existsToRetry", param, action)
  }

  val compFindByIdRend = Compiled((idBf: Rep[Long]) => sendRendicontazione.filter(_.id === idBf))
  def findFtpFileById(idBf: Long, tipo: String)(implicit log: NodoLogger): Future[Option[FtpFile]] = {
    val param = Seq("tipo" -> tipo, "id" -> idBf)
    val action = for {
      q <- compFindByIdRend(idBf).result.headOption
    } yield q
    run("findFtpFileById", param, action)
  }

  def findFtpFilesByIds(ids: Seq[Long], tipo: String)(implicit log: NodoLogger): Future[Seq[FtpFile]] = {
    val param = Seq("tipo" -> tipo, "ids" -> ids)
    val action = for {
      q <- sendRendicontazione.filter(s => insetFix[Long](ids, a => s.id inSet a)).result
    } yield q
    run("findFtpFilesByIds", param, action)
  }

  val compUpdateFileStatusRend = Compiled((idBf: Rep[Long]) => sendRendicontazione.filter(_.id === idBf).map(f => (f.stato, f.updatedBy, f.updatedDate)))

  def updateFileStatus(idBf: Long, status: FtpFileStatus.Value, tipo: String, actorClassId: String)(implicit log: NodoLogger): Future[Int] = {
    val now = Util.now()
    val param = Seq("tipo" -> tipo, "idBf" -> idBf, "status" -> status, "now" -> now, "actorClassId" -> actorClassId)

    val action = for {
      q <- compUpdateFileStatusRend(idBf).update(status, actorClassId, now)
    } yield q
    run("updateFileStatus", param, action)
  }

  val compUpdateFileRetryRend =
    Compiled((sftpFileId: Rep[Long]) => sendRendicontazione.filter(_.id === sftpFileId).map(f => (f.retry, f.updatedBy, f.updatedDate)))

  def updateFileRetry(sftpFile: FtpFile, tipo: String, actorClassId: String)(implicit log: NodoLogger): Future[Int] = {
    val now = Util.now()
    val retry = sftpFile.retry + 1
    val param =
      Seq("tipo" -> tipo, "sftpFile" -> sftpFile, "now" -> now, "retry" -> retry, "actorClassId" -> actorClassId)

    val action = for {
      q <- compUpdateFileRetryRend(sftpFile.id).update(retry, actorClassId, now)
    } yield q
    run("updateFileRetry", param, action)
  }

  val compGetFileFromSendQueue = Compiled((name: Rep[String]) => sendRendicontazione.filter(_.fileName === name))

  private val insertRendicontazioni =
    rendicontazioni returning rendicontazioni.map(_.objId) into ((item, id) => item.copy(objId = id))

  def saveRendicontazioneAndBinaryFile(rendicontazione: Rendicontazione, binaryFile: BinaryFile)(implicit log: NodoLogger): Future[(Rendicontazione, BinaryFile)] = {
    val param = Seq("rendicontazione" -> rendicontazione, "binaryFile" -> binaryFile)
    val action = for {
      bf <- insertBinaryFile += binaryFile
      r <- insertRendicontazioni += rendicontazione.copy(fk_binary_file = Some(bf.objId))
    } yield (r, bf)
    run("saveRendicontazioneAndBinaryFile", param, action.transactionally)
  }

  private val insertSftpFilesRendicontazione =
    sendRendicontazione returning sendRendicontazione.map(_.id) into ((item, id) => item.copy(id = id))

  val compSave1 =
    Compiled((idFlusso: Rep[String], dataOraFlusso: Rep[LocalDateTime], status: Rep[FtpFileStatus.Value]) =>
      rendicontazioni.filter(_.idFlusso === idFlusso).filter(_.dataOraFlusso < dataOraFlusso).join(sendRendicontazione).on(_.fk_sftp_file === _.id).filter(_._2.stato === status).map(_._2.id)
    )
  def save(rendicontazione: Rendicontazione, sftpFile: FtpFile)(implicit log: NodoLogger): Future[(Rendicontazione, FtpFile)] = {
    val ftpFileStatus = FtpFileStatus.TO_UPLOAD
    val sftpStatusTo = FtpFileStatus.EXPIRED

    val param = Seq("rendicontazione" -> rendicontazione, "sftpFile" -> sftpFile, "ftpFileStatus" -> ftpFileStatus, "sftpStatusTo" -> sftpStatusTo)

    val action = for {
      //metto in expired i file vecchi dello stesso id flusso https://corporate.sia.eu/jira/browse/NODO4-745
      expiredToUpload <- compSave1(rendicontazione.idFlusso, rendicontazione.dataOraFlusso, ftpFileStatus).result
      _ <-
        if (expiredToUpload.nonEmpty) {
          sendRendicontazione.filter(s => insetFix[Long](expiredToUpload, a => s.id inSet a)).map(_.stato).update(sftpStatusTo)
        } else {
          DBIO.successful(())
        }
      bf <- insertSftpFilesRendicontazione += sftpFile
      res <- insertRendicontazioni += rendicontazione.copy(fk_sftp_file = Some(bf.id))
    } yield (res, bf)

    run("save", param, action.transactionally)
  }

  def findRendicontazioni(idPaSeq: Iterable[String], idPsp: Option[String], dayLimit: Long)(implicit log: NodoLogger): Future[Seq[(String, LocalDateTime)]] = {
    val rendicontazioneStatus = RendicontazioneStatus.VALID
    val param = Seq("idPaSeq" -> idPaSeq, "idPsp" -> idPsp, "rendicontazioneStatus" -> rendicontazioneStatus)

    val action = for {
      r <- rendicontazioni
        .filter(_.stato === rendicontazioneStatus)
        .filter(_.dataOraFlusso >= LocalDate.now().minusDays(dayLimit).atStartOfDay())
        .filter(s => insetFix[String](idPaSeq, a => s.dominio inSet a))
        .filterOpt(idPsp)((r, get) => r.psp === get)
        .map(r => r.idFlusso -> r.dataOraFlusso)
        .result
    } yield r

    run("findRendicontazioni", param, action)
  }

  val compFindRendicontazioniByIdFlusso =
    Compiled((stato: Rep[RendicontazioneStatus.Value], idFlusso: Rep[String], psp: Rep[String], dateStart: Rep[LocalDateTime], dateEnd: Rep[LocalDateTime]) =>
      rendicontazioni.filter(_.stato === stato).filter(_.idFlusso === idFlusso).filter(_.psp === psp).filter(_.dataOraFlusso between (dateStart, dateEnd)).sortBy(_.dataOraFlusso.desc).take(1)
    )
  def findRendicontazioniByIdFlusso(idPsp: String, idFlusso: String, dateStart: LocalDateTime, dateEnd: LocalDateTime)(implicit log: NodoLogger): Future[Option[Rendicontazione]] = {
    val rendicontazioneStatus = RendicontazioneStatus.VALID
    val param = Seq("idPsp" -> idPsp, "idFlusso" -> idFlusso, "rendicontazioneStatus" -> rendicontazioneStatus, "dateStart" -> dateStart, "dateEnd" -> dateEnd)

    val action = for {
      r <- compFindRendicontazioniByIdFlusso(rendicontazioneStatus, idFlusso, idPsp, dateStart, dateEnd).result.headOption
    } yield r
    run("findRendicontazioniByIdFlusso", param, action)
  }

  val compFindValidByIdFlussoAndIdPspEqualsAndDate =
    Compiled((stato: Rep[RendicontazioneStatus.Value], idFlusso: Rep[String], idPsp: Rep[String], dataOraFlusso: Rep[LocalDateTime]) =>
      rendicontazioni.filter(_.stato === stato).filter(_.idFlusso === idFlusso).filter(_.psp === idPsp).filter(_.dataOraFlusso === dataOraFlusso).sortBy(_.dataOraFlusso.desc).take(1)
    )

  def findValidByIdFlussoAndIdPspEqualsAndDate(idFlusso: String, idPsp: String, dataOraFusso: LocalDateTime)(implicit log: NodoLogger): Future[Option[Rendicontazione]] = {
    val rendicontazioneStatus = RendicontazioneStatus.VALID
    val param = Seq("idFlusso" -> idFlusso, "idPsp" -> idPsp, "dataOraFusso" -> dataOraFusso, "rendicontazioneStatus" -> rendicontazioneStatus)

    val action = for {
      r <- compFindValidByIdFlussoAndIdPspEqualsAndDate(rendicontazioneStatus, idFlusso, idPsp, dataOraFusso).result.headOption
    } yield r
    run("findValidByIdFlussoAndIdPspEqualsAndDate", param, action)
  }

  def getRendicontazioneValidaByIfFlusso(idFlusso: String, idDominio: Option[String], idPsp: Option[String])(implicit log: NodoLogger): Future[Option[Rendicontazione]] = {
    val rendicontazioneStatus = RendicontazioneStatus.VALID
    val param = Seq("idFlusso" -> idFlusso, "idPsp" -> idPsp, "idDominio" -> idDominio, "rendicontazioneStatus" -> rendicontazioneStatus)

    val action = for {
      r <- rendicontazioni
        .filter(_.stato === rendicontazioneStatus)
        .filter(_.idFlusso === idFlusso)
        .filterOpt(idDominio)((r, get) => r.dominio === get)
        .filterOpt(idPsp)((r, get) => r.psp === get)
        .sortBy(_.dataOraFlusso.desc)
        .take(1)
        .result
        .headOption
    } yield r
    run("getRendicontazioneValidaByIfFlusso", param, action)
  }

  def save(rendicontazione: Rendicontazione)(implicit log: NodoLogger): Future[Rendicontazione] = {
    val param = Seq("rendicontazione" -> rendicontazione)
    val action = for {
      r <- insertRendicontazioni += rendicontazione
    } yield r
    run("save", param, action)
  }

  val compiledFindJob =
    Compiled((jobName: Rep[String], extraKey: Rep[String]) => schedulerFireCheck.filter(_.jobName === jobName).filter(_.extraKey === extraKey))
  val compiledFindNotRunningJobForUpdate = Compiled((jobName: Rep[String], extraKey: Rep[String]) =>
    schedulerFireCheck.filter(_.status === SchedulerFireCheckStatus.WAIT_TO_NEXT_FIRE).filter(_.jobName === jobName).filter(_.extraKey === extraKey).map(s => (s.start, s.end, s.status))
  )

  def fireJobIfNotRunning(jobName: String, extraKey: String = Constant.KeyName.EMPTY_KEY)(implicit log: NodoLogger): Future[SchedulerFireExitStatus.Value] = {

    val now = Util.now()
    val param = Seq("jobName" -> jobName, "extraKey" -> extraKey, "now" -> now)

    val action = for {
      updated <- compiledFindNotRunningJobForUpdate(jobName, extraKey).update(now, None, SchedulerFireCheckStatus.RUNNING)
      status <-
        if (updated > 0) {
          DBIOAction.successful(SchedulerFireExitStatus.JOB_STARTED)
        } else {
          for {
            existing <- compiledFindJob(jobName, extraKey).result.headOption
            status <-
              if (existing.isDefined) {
                DBIO.successful(SchedulerFireExitStatus.JOB_RUNNING)
              } else {
                val sf = SchedulerFireCheck(0, jobName, extraKey, now, SchedulerFireCheckStatus.RUNNING)
                (schedulerFireCheck ++= Seq(sf)).flatMap(_ => DBIO.successful(SchedulerFireExitStatus.JOB_STARTED))
              }
          } yield status
        }
    } yield status

    run("fireJobIfNotRunning", param, action)
  }

  val compStopRunningJob = Compiled((jobName: Rep[String], extraKey: Rep[String]) => schedulerFireCheck.filter(_.jobName === jobName).filter(_.extraKey === extraKey).map(s => s.status -> s.end))

  def stopRunningJob(jobName: String, extraKey: String = Constant.KeyName.EMPTY_KEY)(implicit log: NodoLogger): Future[Int] = {
    val status = SchedulerFireCheckStatus.WAIT_TO_NEXT_FIRE
    val now = Util.now()
    val param = Seq("jobName" -> jobName, "extraKey" -> extraKey, "status" -> status, "end" -> now)

    val action = for {
      r <- compStopRunningJob(jobName, extraKey).update(status, Some(now))
    } yield r

    run("stopRunningJob", param, action)
  }

  val compCheckRunningJobs =
    Compiled((status: Rep[SchedulerFireCheckStatus.Value]) => schedulerFireCheck.filter(_.status === status).length)
  def checkRunningJobs()(implicit log: NodoLogger): Future[Int] = {
    val status = SchedulerFireCheckStatus.RUNNING
    val param = Seq("status" -> status)

    val action = for {
      r <- compCheckRunningJobs(status).result
    } yield r

    run("checkRunningJobs", param, action)
  }

  val compResetRunningJobs =
    Compiled((status: Rep[SchedulerFireCheckStatus.Value]) => schedulerFireCheck.filter(_.status === status).map(d => d.status -> d.end))
  def resetRunningJobs()(implicit log: NodoLogger): Future[Int] = {
    val statusFrom = SchedulerFireCheckStatus.RUNNING
    val statusTo = SchedulerFireCheckStatus.WAIT_TO_NEXT_FIRE
    val now = Util.now()
    val param = Seq("now" -> now, "statusFrom" -> statusFrom, "statusTo" -> statusTo)

    val action = for {
      r <- compResetRunningJobs(statusFrom).update(statusTo, Some(now))
    } yield r
    run("resetRunningJobs", param, action)
  }

  val compiledFindJobsByDate = Compiled((limitDate: Rep[LocalDateTime]) => {
    schedulerFireCheck.filter(_.start > limitDate).sortBy(_.extraKey.asc).sortBy(_.start.desc)
  })
  val compiledFindJobsByKey = Compiled((keyFilter: Rep[String]) => {
    schedulerFireCheck.filter(_.extraKey.toLowerCase like s"%${keyFilter}%").sortBy(_.extraKey.asc).sortBy(_.start.desc)
  })
  val compiledFindJobsByDateAndKey = Compiled((limitDate: Rep[LocalDateTime], keyFilter: Rep[String]) => {
    schedulerFireCheck.filter(_.start > limitDate).filter(_.extraKey.toLowerCase like s"%${keyFilter}%").sortBy(_.extraKey.asc).sortBy(_.start.desc)
  })

  def findJobs(limitDate: Option[LocalDateTime], keyFilter: Option[String])(implicit log: NodoLogger) = {
    val param = Seq()
    val action = (limitDate, keyFilter) match {
      case (Some(date), Some(filter)) => compiledFindJobsByDateAndKey(date, filter.toLowerCase).result
      case (Some(date), None)         => compiledFindJobsByDate(date).result
      case (None, Some(filter))       => compiledFindJobsByKey(filter.toLowerCase).result
      case (None, None)               => schedulerFireCheck.sortBy(_.extraKey.asc).sortBy(_.start.desc).result
    }
    run("findJobs", param, action)
  }

  def resetJob(id: Long)(implicit log: NodoLogger) = {
    val param = Seq()
    val action = for {
      r <- schedulerFireCheck.filter(_.id === id).map(d => d.status -> d.end).update(SchedulerFireCheckStatus.WAIT_TO_NEXT_FIRE, Some(Util.now()))
    } yield r

    run("resetJob", param, action)
  }

  private val insertScheduler =
    schedulerTrace returning schedulerTrace.map(_.id) into ((item, id) => item.copy(id = id))
  def insertSchedulerTrace(sid: String, job: String, cron: Option[String], status: SchedulerStatus.Value, message: Option[String] = None)(implicit log: NodoLogger): Future[SchedulerTrace] = {
    val st = SchedulerTrace(0, sid, job, Util.now(), cron.map(_ => SchedulerFire.CRON).getOrElse(SchedulerFire.MANUAL), cron, Some(status), message)
    val param = Seq("st" -> st)

    val action = for {
      s <- insertScheduler += st
    } yield s

    run("insertSchedulerTrace", param, action)
  }

}
