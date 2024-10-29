package it.gov.pagopa.common.util.azure.cosmos

import play.api.libs.json._

case class RtEntity(
                     id: String,
                     partitionKey: String,
                     domainId: String,
                     iuv: String,
                     ccp: String,
                     receiptType: Option[String],
                     rt: Option[String],
                     rtTimestamp: Option[Long]
                   )

object RtEntity {
  implicit val rtEntityFormat: OFormat[RtEntity] = Json.format[RtEntity]
}