package eu.sia.pagopa.common.message

import eu.sia.pagopa.common.repo.re.model.Re

import java.time.LocalDateTime

object CategoriaEvento extends Enumeration {
  val INTERNO, INTERFACCIA = Value
}
object SottoTipoEvento extends Enumeration {
  val REQ, RESP, INTERN = Value
}
object SottoTipoEventoEvh extends Enumeration {
  val REQ, RES = Value
}
object CategoriaEventoEvh extends Enumeration {
  val INTERFACE, INTERNAL = Value
}
object SoapReceiverType extends Enumeration {
  val NEXI, FDR = Value
}

object Componente extends Enumeration {
  val NDP_FDR = Value
}

case class ReExtra(uri: Option[String] = None, headers: Seq[(String, String)] = Nil, httpMethod: Option[String] = None, callRemoteAddress: Option[String] = None, statusCode: Option[Int] = None, elapsed: Option[Long] = None, soapProtocol: Boolean = false)

case class ReRequest(
                      override val sessionId: String,
                      override val testCaseId: Option[String] = None,
                      re: Re,
                      reExtra: Option[ReExtra] = None
                    ) extends BaseMessage

case class ReEventHub(
                       appVersion: String,
                       uniqueId: String,
                       created: LocalDateTime,
                       sessionId: Option[String] = None,
                       eventType: String,
                       flowName: Option[String] = None,
                       pspId: Option[String] = None,
                       organizationId: Option[String] = None,
                       flowAction: Option[String] = None,
                       httpType: String,
                       httpMethod: Option[String],
                       httpUrl: Option[String] = None,
                       blobBodyRef: Option[BlobBodyRef] = None,
                       header: Map[String, Seq[String]]
                     )

case class BlobBodyRef(
                        storageAccount: Option[String],
                        containerName: Option[String],
                        fileName: Option[String],
                        fileLength: Int
                      )
