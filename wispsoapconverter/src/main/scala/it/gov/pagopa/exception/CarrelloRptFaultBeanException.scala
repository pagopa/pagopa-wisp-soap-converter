package it.gov.pagopa.exception

import it.gov.pagopa.common.exception.DigitPaException
import it.gov.pagopa.common.util.RPTKey

case class CarrelloRptFaultBeanException(
    digitPaException: DigitPaException,
    idCarrello: Option[String] = None,
    rptKeys: Option[Seq[RPTKey]] = None,
    workflowErrorCode: Option[WorkflowExceptionErrorCodes.Value] = None,
    detailErrors: Option[Seq[(Int, RptFaultBeanException)]] = None,
    idCanale: Option[String]
) extends Exception(s"DigitPaException:${digitPaException.getMessage}", digitPaException)

object WorkflowExceptionErrorCodes extends Enumeration {
  val CARRELLO_ERRORE_SEMANTICO, CARRELLO_ERRORE_INVIO_PSP, RPT_ERRORE_SEMANTICO, RPT_ERRORE_INVIO_PSP, RIFIUTATO_PSP, RPT_ERRORE_INVIO_CD = Value
}
