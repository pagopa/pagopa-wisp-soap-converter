package eu.sia.pagopa.common.util

import org.slf4j.MDC

import java.util.UUID
import scala.concurrent.{ ExecutionContext, Future }

class MdcExecutionContext(executionContext: ExecutionContext) extends ExecutionContext {
  override def execute(runnable: Runnable): Unit = {
    val callerMdc = MDC.getCopyOfContextMap
    executionContext.execute(() => {
      // copy caller thread diagnostic context to execution thread
      if (callerMdc != null) MDC.setContextMap(callerMdc)
      try {
        runnable.run()
      } finally {
        // the thread might be reused, so we clean up for the next use
        MDC.clear()
      }
    })
  }

  override def reportFailure(cause: Throwable): Unit = executionContext.reportFailure(cause)
}

object FutureUtils {

  def groupedSerializeFuture[A, B](log: NodoLogger, l: Iterable[A], grouped: Int)(fn: A => Future[B])(implicit ec: ExecutionContext): Future[Seq[B]] = {
    val k: Seq[(Iterable[A], Int)] = l.grouped(grouped).zipWithIndex.toSeq
    val id = UUID.randomUUID().toString
    log.debug(s"Start processo ${k.size} Blocchi ($id)")
    val kk: Future[Seq[B]] = serializeFuture(k)(h => {
      log.debug(s"Blocco ${h._2}/${k.size} ($id)")
      Future.sequence(h._1.toSeq.map(g => fn(g)))
    }).map(r => {
      log.debug(s"Fine processo ${k.size} Blocchi ($id)")
      r.flatten
    })
    kk
  }

  def serializeFuture[A, B](l: Iterable[A])(fn: A => Future[B])(implicit ec: ExecutionContext): Future[Seq[B]] = {
    l.foldLeft(Future(Seq.empty[B])) { (previousFuture, next) =>
      for {
        previousResults <- previousFuture
        nextResult <- fn(next)
      } yield previousResults :+ nextResult
    }
  }
}
