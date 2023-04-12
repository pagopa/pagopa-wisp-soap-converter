package eu.sia.pagopa.common.util.web

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import eu.sia.pagopa.common.util.Constant
import spray.json.{ DefaultJsonProtocol, JsNull, JsString, JsValue, JsonFormat, RootJsonFormat }

import java.time.LocalDateTime

object JobDto extends DefaultJsonProtocol with SprayJsonSupport {
  implicit object OptionDateJsonFormat extends RootJsonFormat[Option[LocalDateTime]] {
    override def write(obj: Option[LocalDateTime]) =
      obj.map(d => JsString(Constant.DTF_DATETIME.format(d))).getOrElse(JsNull)
    override def read(json: JsValue): Option[LocalDateTime] = json match {
      case JsString(s) => Option(LocalDateTime.parse(s, Constant.DTF_DATETIME))
      case _           => None
    }
  }
  implicit object DateJsonFormat extends RootJsonFormat[LocalDateTime] {
    override def write(obj: LocalDateTime) = JsString(Constant.DTF_DATETIME.format(obj))
    override def read(json: JsValue): LocalDateTime = json match {
      case JsString(s) => LocalDateTime.parse(s, Constant.DTF_DATETIME)
      case _           => null
    }
  }
  implicit val ctmapFormat2: JsonFormat[JobDto] = jsonFormat5(JobDto.apply)
}
case class JobDto(id: Long, key: String, start: LocalDateTime, end: Option[LocalDateTime], status: String)

object JobGroup extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val ctmapFormat1: JsonFormat[JobGroup] = jsonFormat4(JobGroup.apply)
}
case class JobGroup(name: String, jobName: String, jobs: Seq[JobDto], running: Long)
