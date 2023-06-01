package eu.sia.pagopa.common.json

import eu.sia.pagopa.common.exception.JsonException
import eu.sia.pagopa.common.json.schema.drafts.Version7
import eu.sia.pagopa.common.json.schema.{JsonSource, SchemaFormat, SchemaValidator}
import eu.sia.pagopa.common.util.Constant
import eu.sia.pagopa.common.util.xml.XmlUtil
import play.api.libs.json.{JsObject, JsResult, JsValue}

import scala.io.Source
import scala.util.{Failure, Success, Try}

object JsonValid {
  self =>

  def check(strJson: String, primitive: JsonEnum.Value, toValid: Boolean = true): Try[Unit] = {
    if (toValid) {
      JsonValid.validate(strJson, loadSchema(primitive))
    } else {
      Success(())
    }
  }

  private var singleton = Map.empty[JsonEnum.Value, String]

  //noinspection ScalaStyle
  private def loadSchema(jsonEnum: JsonEnum.Value): String =
    singleton.getOrElse(
      jsonEnum, {
        val path = jsonEnum match {
          case JsonEnum.ERROR         => "/json-schemas/error.json"
          case JsonEnum.NOTIFY_FLOW   => "/json-schemas/notifyFlowRendicontazione.json"
        }

        val fileStream = getClass.getResourceAsStream(path)
        val res = Source.fromInputStream(fileStream, Constant.UTF_8.name()).getLines().mkString("\n")
        singleton = singleton + (jsonEnum -> res)
        res
      }
    )

  val base64: SchemaFormat = new SchemaFormat {
    override def name: String = "base64"

    override def validate(json: play.api.libs.json.JsValue): Boolean = json match {
      case play.api.libs.json.JsString(s) =>
        Try(XmlUtil.StringBase64Binary.decodeBase64ToByteArray(s)) match {
          case Success(_) => true
          case Failure(_) => false
        }
      case _ =>
        false
    }
  }

  private def validate(strJson: String, schema: String): Try[Unit] = {
    JsonSource.fromString(strJson) match {
      case Success(value) =>
        import Version7._ // since 0.9.5 necessary
        val validator = SchemaValidator(Some(Version7))
          .addFormat(base64)
        val schemaObj = JsonSource.schemaFromString(schema).get
        val result: JsResult[JsValue] = validator.validate(schemaObj, value)
        if (result.isSuccess) {
          Success(())
        } else {
          Failure(firstErrorOf(value, result))
        }
      case Failure(e) =>
        Failure(JsonException(e.getMessage, ""))
    }
  }

  private def firstErrorOf[A](value: JsValue, res: JsResult[A]): JsonException = {
    res.asEither match {
      case Left(eee) =>
        val errors = eee.groupBy(_._1.path.size).map(_._2.reverse).flatten
        val path = if (errors.head._1.path.isEmpty) {
          s"/${errors.head._2.head.messages.head.split(" ")(1)}"
        } else {
          errors.head._1.path.mkString
        }
        val firstError = errors.head._2.head.messages.head
        JsonException(s"Path [${if (path.isEmpty) "No path" else path.mkString}] -> [$firstError]", path.mkString, Some(value.asInstanceOf[JsObject]))
      case Right(_) =>
        JsonException("", "", Some(value.asInstanceOf[JsObject]))
    }

  }

}
