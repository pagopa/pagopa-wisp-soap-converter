package eu.sia.pagopa.common.repo

import eu.sia.pagopa.common.util.NodoLogger
import slick.jdbc.{H2Profile, JdbcProfile}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait DBComponent {

  val driver: JdbcProfile

  import driver.api._

  val db: Database

  private final val DEFAULT_LIST_GROUPED = 1000

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
}
