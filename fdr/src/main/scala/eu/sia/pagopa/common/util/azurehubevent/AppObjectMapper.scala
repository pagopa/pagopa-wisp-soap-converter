package eu.sia.pagopa.common.util.azurehubevent

import com.fasterxml.jackson.databind.{ ObjectMapper, SerializationFeature }

import java.text.SimpleDateFormat

object AppObjectMapper {

  lazy val objectMapper: ObjectMapper = {
    val objectMapper = new ObjectMapper()
    objectMapper.findAndRegisterModules()
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    val df = new SimpleDateFormat("dd-MM-yyyy hh:mm:ss:SSSSSS")
    objectMapper.setDateFormat(df)
    objectMapper
  }

}
