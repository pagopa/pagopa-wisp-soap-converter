package it.gov.pagopa.common.util.azure.cosmos

import play.api.libs.json._

case class RtEntity(
                     id: String,
                     partitionKey: String,
                     idDominio: String,
                     iuv: String,
                     ccp: String,
                     receiptType: String,
                     rt: String,
                     rtTimestamp: Long
                   )

object RtEntity {
  implicit val rtEntityFormat: OFormat[RtEntity] = Json.format[RtEntity]
}