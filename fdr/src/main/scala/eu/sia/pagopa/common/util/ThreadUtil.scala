package eu.sia.pagopa.common.util

import akka.actor.ActorSystem

import java.util.concurrent.Executors
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, ExecutionContextExecutorService, Future }
import scala.util.Try

object ThreadUtil {

  private def funcWrapper[A, B](futureFunc: A => Future[B], intN: A, ecFixed: ExecutionContext, log: NodoLogger): () => Future[B] = { () =>
    Future {
      log.debug(s"THREAD start - $intN - ${Thread.currentThread().getName}")
      val res = Await.result(futureFunc.apply(intN), Duration.Inf)
      log.debug(s"THREAD end - $intN - ${Thread.currentThread().getName}")
      res
    }(ecFixed)
  }

  def parallel[A, B](log: NodoLogger, list: Iterable[A], nThreads: Int)(futureFunc: A => Future[B])(implicit ec: ExecutionContext, system: ActorSystem): Future[Iterable[B]] = {
    log.debug(s"CREATE EXECUTOR")
    val executionContextFixed: ExecutionContextExecutorService =
      ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(nThreads))

    Try({
      val mdcExecutionContextFixed: MdcExecutionContext = new MdcExecutionContext(executionContextFixed)

      val wrappedFunction: Iterable[() => Future[B]] =
        list.map(intN => funcWrapper(futureFunc, intN, mdcExecutionContextFixed, log))

      Future
        .sequence(wrappedFunction.map(f => f.apply()))
        .map(a => {
          log.debug(s"DESTROY EXECUTOR")
          executionContextFixed.shutdown()
          a
        })
        .recoverWith({ case e =>
          log.error(e, "errore wrapped function")
          log.debug(s"DESTROY EXECUTOR")
          executionContextFixed.shutdown()
          Future.failed(e)
        })
    }).recover({ case e =>
      log.error(e, "errore creazione executionContextFixed")
      log.debug(s"DESTROY EXECUTOR")
      executionContextFixed.shutdown()
      Future.failed(e)
    }).get
  }

  /*
    def parallel[A, B](log: NodoLogger, list: Iterable[A], nThreads: Int, timeoutForSingleProcess: FiniteDuration)(
        fn: A => Future[B])(implicit ec: ExecutionContext): Future[Iterable[B]] = {

      val timeout = timeoutForSingleProcess
      val tp = Executors.newFixedThreadPool(nThreads)
      val pool = new MdcExecutionContext(ExecutionContext.fromExecutor(tp))
      val res = list.map(value => {
        val promise = Promise[B]()
        pool.execute(() => {
          Try(
            Await.result(
              fn(value)
                .map(g => {
                  promise.success(g)
                })
                .recover { case e: Throwable =>
                  log.warn(e, "Process failed")
                  tp.shutdownNow()
                  if (!promise.isCompleted) promise.failure(e)
                },
              timeout)).recover({ case e: Throwable =>
            log.error(e, s"Timeout single process [$timeout]")
            Try({
              if (!tp.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                tp.shutdownNow
              }
            }).recover({ case _: InterruptedException =>
              tp.shutdownNow
            })
            if (!promise.isCompleted) promise.failure(e)
          })
        })
        promise.future
      })
      Future
        .sequence(res)
        .map(s => {
          log.debug("closing thread pool")
          Try({
            if (!tp.awaitTermination(800, TimeUnit.MILLISECONDS)) {
              tp.shutdownNow
            }
          }).recover({ case _: InterruptedException =>
            tp.shutdownNow
          })
          s
        })
        .recoverWith({ case e =>
          Try({
            if (!tp.awaitTermination(800, TimeUnit.MILLISECONDS)) {
              tp.shutdownNow
            }
          }).recover({ case _: InterruptedException =>
            tp.shutdownNow
          })
          Future.failed(e)
        })
  }*/

}
