package eu.sia.pagopa.common.util

import scala.util.{ Success, Try }

object TryUtils {

  def serializeTry[A, B](l: Iterable[A], results: Seq[B] = Nil)(fn: A => Try[B]): Try[Seq[B]] = {
    if (l.isEmpty) {
      Success(results)
    } else {
      fn(l.head).flatMap(r => {
        serializeTry(l.tail, results :+ r)(fn)
      })
    }
//    l.foldLeft(Try(Seq.empty[B])) { (previousTry, next) =>
//      for {
//        previousResults <- previousTry
//        nextResult <- fn(next)
//      } yield previousResults :+ nextResult
//    }
  }
}
