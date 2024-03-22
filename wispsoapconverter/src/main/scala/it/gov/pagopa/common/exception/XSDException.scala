package it.gov.pagopa.common.exception

import org.w3c.dom.Node

import scala.xml.SAXParseException

case class XSDException(message: String, saxException: SAXParseException, failedElement: Option[Node] = None) extends Exception(message, saxException) {

  private def getXPath(node: Node): List[String] = {
    if (node.getParentNode == null) {
      Nil
    } else {
      var countPrevSibling = 0
      var prev: Node = node.getPreviousSibling
      while (prev != null) {
        if (prev.getLocalName == node.getLocalName) {
          countPrevSibling = countPrevSibling + 1
        }
        prev = prev.getPreviousSibling
      }
      if (countPrevSibling != 0) {
        s"${node.getLocalName}[$countPrevSibling]" :: getXPath(node.getParentNode)
      } else {
        var countNextSibling = 0
        var next: Node = node.getNextSibling
        while (next != null) {
          if (next.getLocalName == node.getLocalName) {
            countNextSibling = countNextSibling + 1
          }
          next = next.getNextSibling
        }
        if (countNextSibling == 0) {
          // nel caso in cui c'è un solo elemento ma in realtà potrebbe essere un lista,
          // per convenzione non mettiamo l'indice
          node.getLocalName :: getXPath(node.getParentNode)
        } else {
          s"${node.getLocalName}[0]" :: getXPath(node.getParentNode)
        }
      }
    }
  }

  val xPath: String = this.failedElement.map(getXPath(_).reverse.mkString("/")).getOrElse("")

  override def getMessage: String =
    if (message.isEmpty) {
      super.getMessage
    } else {
      s"$message${if (xPath.nonEmpty) s" [$xPath]" else " "} - ${saxException.getMessage}"
    }

}
