package eu.sia.pagopa.common.json.schema

import eu.sia.pagopa.common.json.schema.internal.serialization.{SchemaReads, SchemaWrites}

trait SchemaVersion extends SchemaReads with SchemaWrites {
  def schemaLocation: String
  def options: SchemaConfigOptions
}
