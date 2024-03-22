package it.gov.pagopa.common.message

object CategoriaEvento extends Enumeration {
  val INTERNO, INTERFACCIA = Value
}
object SottoTipoEvento extends Enumeration {
  val REQ, RESP, INTERN = Value
}

object Componente extends Enumeration {
  val FESP = Value
}

case class ReExtra(uri: Option[String] = None, headers: Seq[(String, String)] = Nil, httpMethod: Option[String] = None, callRemoteAddress: Option[String] = None, statusCode: Option[Int] = None, elapsed: Option[Long] = None, soapProtocol: Boolean = false)

case class ReRequest(
                      override val sessionId: String,
                      override val testCaseId: Option[String] = None,
                      re: Re,
                      reExtra: Option[ReExtra] = None
                    ) extends BaseMessage
