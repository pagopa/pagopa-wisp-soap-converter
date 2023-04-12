package eu.sia.pagopa.common.repo

import eu.sia.pagopa.common.util.{NodoLogger, Util}
import slick.dbio.DBIOAction
import slick.jdbc.{H2Profile, JdbcProfile}
import slick.sql.SqlAction

import java.time.{LocalDate, LocalDateTime, LocalTime}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait DBComponent {

  val driver: JdbcProfile

  import driver.api._

  val db: Database

  val MDC_QUERY_ID = "queryId"

  private final val DEFAULT_LIST_GROUPED = 1000

  val trunc = SimpleFunction.unary[LocalDateTime, LocalDate]("TRUNC")
  val truncOpt = SimpleFunction.unary[Option[LocalDateTime], Option[LocalDate]]("TRUNC")

  def adjustName(originalName: String): String = {
    slickProfile match {
      case H2Profile =>
        originalName.toUpperCase()
      case _ =>
        originalName.toLowerCase()
    }
  }
  def testQuery()={
    slickProfile match {
      case H2Profile =>
        db.run(sql"select 1 from dual".as[Int])

      case _ =>
        db.run(sql"select 1".as[Int])

    }
  }

  def toStartDate(date: LocalDateTime) = {
    date.toLocalDate.atStartOfDay()
  }

  def toEndDate(date: LocalDateTime) = {
    LocalDateTime.of(date.toLocalDate, LocalTime.MAX)
  }

  def toStartDate(date: LocalDate) = {
    date.atStartOfDay()
  }

  def toEndDate(date: LocalDate) = {
    LocalDateTime.of(date, LocalTime.MAX)
  }

  def runAction[M](action: DBIO[M]): Future[M] = {
    db.run(action)
  }

  implicit class ActionRunner[M](action: DBIO[M]) {
    def run: Future[M] = db.run(action)
  }

  def run[R](methodName: String, param: Seq[(String, Any)], action: DBIO[R])(implicit log: NodoLogger, ec: ExecutionContext): Future[R] = {

    val qid = UUID.randomUUID().toString

    if (log.isDebugEnabled) {
      log.debug(s"#################### Start query method [$methodName] [$qid] ##################")
      if (param.isEmpty) {
        log.debug(s"NO Query params")
      } else {
        log.debug(s"Query params -> ${param.map(a => s"\n[${a._1}] = {${a._2.toString}}").mkString("")}")
      }
    }

    db.run(action)
      .map(res => {
        log.debug(s"############### End query method [$methodName] [$qid] (success) ###############")
        res
      })
      .recoverWith({ case e: Throwable =>
        log.debug(s"############### End query method [$methodName] [$qid] (failed) ###############")
        Future.failed(e)
      })
  }

  def insetFix[T](list: Iterable[T], fn: (Iterable[T]) => Rep[Boolean]): driver.api.Rep[Boolean] = {
    //oracle fix for clause IN
    list.grouped(DEFAULT_LIST_GROUPED).map(fn).reduce(_ || _)
  }
  def insetFixOpt[T](list: Iterable[T], fn: (Iterable[T]) => Rep[Option[Boolean]]): driver.api.Rep[Option[Boolean]] = {
    //oracle fix for clause IN
    list.grouped(DEFAULT_LIST_GROUPED).map(fn).reduce(_ || _)
  }

  def getStartEndDates(time: LocalDateTime): (LocalDateTime, LocalDateTime) = {
    val startOfDay = toStartDate(time)
    val endOfDay = toEndDate(startOfDay)
    startOfDay -> endOfDay
  }

  def getStartEndDatesYesterday(time: LocalDateTime): (LocalDateTime, LocalDateTime) = {
    val startOfDay = toStartDate(time.minusDays(1))
    val endOfDay = toEndDate(startOfDay)
    startOfDay -> endOfDay
  }

}
