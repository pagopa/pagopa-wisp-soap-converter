package eu.sia.pagopa.ftpsender.util

import fr.janalyse.ssh._

import scala.concurrent.{ ExecutionContext, Future }
import scala.language.{ implicitConversions, reflectiveCalls }

object SSHFuture {

  private def using[R](resource: SSHFtp)(block: SSHFtp => Future[R])(implicit ec: ExecutionContext): Future[R] = {
    block(resource)
      .flatMap(a => {
        resource.close()
        Future.successful(a)
      })
      .recoverWith({ case e: Throwable =>
        resource.close()
        Future.failed(e)
      })
  }

  private def using[R](resource: SSH)(block: SSH => Future[R])(implicit ec: ExecutionContext): Future[R] = {
    block(resource)
      .flatMap(a => {
        resource.close()
        Future.successful(a)
      })
      .recoverWith({ case e: Throwable =>
        resource.close()
        Future.failed(e)
      })
  }

  def ftp[T](ssh: SSH)(withftp: SSHFtp => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    using(ssh) { ssh =>
      using(new SSHFtp()(ssh)) { ftp =>
        withftp(ftp)
      }
    }
  }

}
