package it.gov.pagopa.tests.testutil

import org.scalactic.{Bool, Prettifier}

import scala.reflect.io.File
import scala.util.Try

object SpecsUtils {
  object ContentType extends Enumeration {
    val XML, JSON, TEXT, MULTIPART_FORM_DATA = Value
  }

  implicit val prettifier: Prettifier = Prettifier.default

  def minifyXMLString(s: String): String = s.replaceAll(""">[ \n\t]+<""", "><").trim

  implicit class Boolcomp(x: Any) {
    def cmp(y: Any): Bool = {
      Bool.binaryMacroBool(x, "==", y, x == y, prettifier)
    }
    def cmpPayload(y: String, t: ContentType.Value): Bool = {
      val (xx, yy) = t match {
        case ContentType.XML  => (minifyXMLString(x.toString), minifyXMLString(y.toString))
        case ContentType.JSON => (x.toString, y)
        case ContentType.TEXT => (x, y)
      }
      Bool.binaryMacroBool(xx, "==", yy, xx == yy, prettifier)
    }
  }

  def loadTestXML(resourcePath: String): String = {
    Try(File(getClass.getResource(resourcePath).getPath).slurp()).getOrElse(throw new RuntimeException(s"$resourcePath non trovato"))
  }
  def loadTestXMLOption(resourcePath: String): Option[String] = {
    Try(File(getClass.getResource(resourcePath).getPath).slurp()).toOption
  }

  def loadTestJSON(resourcePath: String): String = {
    Try(File(getClass.getResource(resourcePath).getPath).slurp()).getOrElse(throw new RuntimeException(s"$resourcePath non trovato"))
  }

  def loadTestXMLOrJSON(resourcePath: String): String = {
    Try(loadTestXML(s"$resourcePath.xml"))
      .recover({ case e =>
        loadTestJSON(s"${resourcePath}.json")
      })
      .getOrElse(throw new RuntimeException(s"$resourcePath xml o json non trovato"))
  }

}
