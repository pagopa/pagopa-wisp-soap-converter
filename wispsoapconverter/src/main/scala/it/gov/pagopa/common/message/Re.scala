package it.gov.pagopa.common.message

import it.gov.pagopa.common.util.azure.Appfunction
import it.gov.pagopa.common.util.azure.cosmos.{Esito, EventCategory}
import net.openhft.hashing.LongHashFunction

import java.time.Instant

case class Re(
               insertedTimestamp: Instant,
               eventCategory: EventCategory.Value,
               cartId: Option[String] = None,
               domainId: Option[String] = None,
               iuv: Option[String] = None,
               ccp: Option[String] = None,
               psp: Option[String] = None,
               station: Option[String] = None,
               channel: Option[String] = None,
               outcome: Option[Esito.Value] = None,
               sessionId: Option[String] = None,
               status: Option[String] = None,
               requestPayload: Option[Array[Byte]] = None,
               responsePayload: Option[Array[Byte]] = None,
               info: Option[String] = None,
               businessProcess: Option[String] = None,
               noticeNumber: Option[String] = None,
               paymentToken: Option[String] = None
             ) {
  val dataOraEvento: String = Appfunction.formatDate(insertedTimestamp)
  val uniqueId: String = s"${dataOraEvento.substring(0, 10)}_${
    LongHashFunction
      .xx()
      .hashChars(s"$dataOraEvento$sessionId$status$station$noticeNumber$paymentToken$domainId$iuv$ccp$info")
  }"

  val version: String = "1"

  override def toString: String = {
    s"""dataOraEvento=[${dataOraEvento}]
       |categoriaEvento=[${eventCategory}]
       |stazione=[${station.getOrElse("N.D")}]
       |canale=[${channel.getOrElse("N.D")}]
       |idDominio=[${domainId.getOrElse("N.D")}]
       |iuv=[${iuv.getOrElse("N.D")}]
       |ccp=[${ccp.getOrElse("N.D")}]
       |sessionId=[${sessionId.getOrElse("N.D")}]
       |""".stripMargin

  }

}
