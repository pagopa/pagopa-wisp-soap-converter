package it.gov.pagopa.exception

import it.gov.pagopa.common.exception.DigitPaException
import it.gov.pagopa.common.util.RPTKey

case class RptFaultBeanException(
    digitPaException: DigitPaException,
    rptKey: Option[RPTKey] = None,
    //    errReporterId:     String                    = FaultId.NODO_DEI_PAGAMENTI_SPC,
    workflowErrorCode: Option[Enumeration#Value] = None
) extends Exception(s"digitPaException:${digitPaException.getMessage}, rptKey: ID_DOMINIO=${rptKey.map(_.idDominio)}, IUV=${rptKey.map(_.iuv)}, CCP=${rptKey.map(_.ccp)}", digitPaException)
