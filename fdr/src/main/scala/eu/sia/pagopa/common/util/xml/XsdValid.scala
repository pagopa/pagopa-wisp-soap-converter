package eu.sia.pagopa.common.util.xml

import com.sun.org.apache.xerces.internal.impl.Constants
import eu.sia.pagopa.common.exception.XSDException
import eu.sia.pagopa.commonxml.XmlEnum
import eu.sia.pagopa.commonxml.XmlEnum.getClass
import org.w3c.dom.Node
import org.xml.sax.InputSource

import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.transform.Source
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import scala.util.{ Failure, Success, Try }
import scala.xml.SAXParseException

object XsdValid {
  private val soapenvNS = "http://schemas.xmlsoap.org/soap/envelope/"

  def checkOnly(strXml: String, primitive: eu.sia.pagopa.commonxml.XmlEnum.Value, toValid: Boolean = true): Try[Unit] = {
    if (toValid) {
      val loader: Array[Source] = eu.sia.pagopa.commonxml.XmlEnum.loadSchema(primitive)
      validate(strXml, loader, Some(primitive))
    } else {
      Success(())
    }
  }

  def checkOnly(strXml: String, xsd: String): Try[Unit] = {
    val loader = xsd match {
      case "TassaAutomobilistica_1_1_0.xsd" =>
        Array[Source](new StreamSource(getClass.getResourceAsStream(s"/xml-schema/tassaautomobilistica/$xsd")))
      case _ =>
        Array[Source]()
    }

    validate(strXml, loader)
  }

  private def getFirstChildElement(node: Option[Node], strict: Boolean): Try[Option[Node]] = node match {
    case None => if (strict) Failure(new RuntimeException("Errore validazione XML: Body vuoto")) else Success(None)
    case a @ Some(n) =>
      n.getNodeType match {
        case Node.ELEMENT_NODE => Success(a)
        case _                 => getFirstChildElement(Option(n.getNextSibling), strict)
      }
  }

  def validate(input: String, sources: Array[Source], primitive: Option[eu.sia.pagopa.commonxml.XmlEnum.Value] = None): Try[Unit] =
    Try({
      val factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
      primitive.map(p => {
        factory.setResourceResolver(new XsdResourceResolver(s"/xml-schema/${XmlEnum.getXsdFolder(p)}"))
      })

      val schema = factory.newSchema(sources)
      val validator = schema.newValidator
      val inputSource = new InputSource(new StringReader(input))

      try {
        val parsedDocument = XmlEnum.documentBuilderFactory.parse(inputSource)

        //validate document root
        val documentSource = new DOMSource(parsedDocument)
        validator.validate(documentSource)

        //validate body and header content if any
        val contents =
          List((parsedDocument.getElementsByTagNameNS(soapenvNS, "Header"), false), (parsedDocument.getElementsByTagNameNS(soapenvNS, "Body"), true))

        contents
          .filter(_._1.getLength == 1)
          .foreach(n =>
            getFirstChildElement(Option(n._1.item(0).getFirstChild), n._2) match {
              case Success(None) => None
              case Success(a)    => validator.validate(new DOMSource(a.get))
              case Failure(e)    => throw e
            }
          )
      } catch {
        case ex: SAXParseException =>
          validator.getProperty(Constants.XERCES_PROPERTY_PREFIX + Constants.CURRENT_ELEMENT_NODE_PROPERTY) match {
            case el: Node => throw XSDException("Errore validazione XML", ex, Some(el))
            case _        => throw XSDException("Errore validazione XML", ex, None)
          }
      }
    })

}
