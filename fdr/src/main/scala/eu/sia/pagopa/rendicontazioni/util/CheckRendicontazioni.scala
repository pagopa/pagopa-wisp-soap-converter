package eu.sia.pagopa.rendicontazioni.util

import eu.sia.pagopa.common.exception
import eu.sia.pagopa.common.exception.{ DigitPaErrorCodes, DigitPaException }
import eu.sia.pagopa.common.repo.offline.OfflineRepository
import eu.sia.pagopa.common.repo.offline.model.Rendicontazione
import eu.sia.pagopa.common.util.{ NodoLogger, Util }

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

object CheckRendicontazioni {

  val ldtformatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

  val IDENTIFICATIVO_FLUSSO_IDX_START = 0
  val IDENTIFICATIVO_FLUSSO_IDX_END = 10
  val IDENTIFICATIVO_FLUSSO_MIN_LEN = 10
  val IDENTIFICATIVO_FLUSSO_MAX_LEN = 35

  def normalizeIdFlusso(idFlusso: String): String = {
    idFlusso.replaceAll("[*:!?|\\\\\\/]", "_") + s".${ldtformatter.format(Util.now())}"
  }

  def checkFlussoRendicontazioneNotPresentOnSamePsp(repo: OfflineRepository, identificativoFlusso: String, identificativoPSP: String, dataOraFlusso: LocalDateTime)(implicit
      log: NodoLogger
  ): Future[Option[Rendicontazione]] = {
    repo.findValidByIdFlussoAndIdPspEqualsAndDate(identificativoFlusso, identificativoPSP, dataOraFlusso)
  }

  def checkFormatoIdFlussoRendicontazione(identificativoFlusso: String, idPsp: String): Try[Unit] = {

    if (identificativoFlusso.length > IDENTIFICATIVO_FLUSSO_MAX_LEN) {
      Failure(
        exception.DigitPaException(
          s"""identificativo flusso di rendicontazione ($identificativoFlusso) non conforme al formato previsto dalle specifiche (<YYYY-MM-DD><istitutoMittente>-<flusso>) con lunghezza massima di 35 caratteri""",
          DigitPaErrorCodes.PPT_SEMANTICA
        )
      )
    } else if (identificativoFlusso.length < IDENTIFICATIVO_FLUSSO_MIN_LEN) {
      Failure(
        exception.DigitPaException(
          s"""identificativo flusso di rendicontazione ($identificativoFlusso) non conforme al formato previsto dalle specifiche (<YYYY-MM-DD><istitutoMittente>-<flusso>)""",
          DigitPaErrorCodes.PPT_SEMANTICA
        )
      )
    } else {
      Try({
        scalaxb.XMLCalendar(identificativoFlusso.substring(IDENTIFICATIVO_FLUSSO_IDX_START, IDENTIFICATIVO_FLUSSO_IDX_END))
      }) match {
        case Failure(_) =>
          Failure(
            exception.DigitPaException(
              s"""identificativo flusso di rendicontazione ($identificativoFlusso) non conforme al formato previsto dalle specifiche (<YYYY-MM-DD><istitutoMittente>-<flusso>)""",
              DigitPaErrorCodes.PPT_SEMANTICA
            )
          )

        case Success(_) =>
          if (!identificativoFlusso.substring(IDENTIFICATIVO_FLUSSO_IDX_END).startsWith(s"""$idPsp-""")) {
            Failure(
              exception.DigitPaException(
                s"""identificativo flusso di rendicontazione ($identificativoFlusso) non conforme al formato previsto dalle specifiche (<YYYY-MM-DD><istitutoMittente>-<flusso>)""",
                DigitPaErrorCodes.PPT_SEMANTICA
              )
            )
          } else {
            Success(())
          }
      }
    }
  }

}
