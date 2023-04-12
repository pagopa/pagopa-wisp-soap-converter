package eu.sia.pagopa.common.message

import eu.sia.pagopa.common.repo.re.model.Re

object CategoriaEvento extends Enumeration {
  val INTERNO, INTERFACCIA = Value
}
object SottoTipoEvento extends Enumeration {
  val REQ, RESP, INTERN = Value
}
object SoapReceiverType extends Enumeration {
  val PA, PSP, CD, PM, ATPOS, PAGOPA = Value
}

object Componente extends Enumeration {
  val FESP, WFESP = Value
}

case class ReExtra(uri: Option[String] = None, headers: Seq[(String, String)] = Nil, httpMethod: Option[String] = None, callRemoteAddress: Option[String] = None, statusCode: Option[Int] = None, elapsed: Option[Long] = None, soapProtocol: Boolean = false)

case class ReRequest(override val sessionId: String, override val testCaseId: Option[String] = None, re: Re, reExtra: Option[ReExtra] = None) extends BaseMessage

//case class ReResponse(
//    override val sessionId:  String,
//    override val testCaseId: Option[String],
//    re : Re,
//    reExtra : Option[ReExtra],
//    throwable:               Option[Throwable]) extends BaseMessage
