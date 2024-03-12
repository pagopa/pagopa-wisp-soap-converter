package it.gov.pagopa.common.util.xml

import com.sun.org.apache.xerces.internal.dom.DOMInputImpl
import it.gov.pagopa.common.util.Constant
import org.w3c.dom.ls.{LSInput, LSResourceResolver}

import java.io.InputStreamReader

class XsdResourceResolver(pkg: String) extends LSResourceResolver {
  override def resolveResource(`type`: String, namespaceURI: String, publicId: String, systemId: String, baseURI: String): LSInput = {
    val resource = getClass.getResourceAsStream(s"$pkg/$systemId")
    val input = new DOMInputImpl()
    input.setPublicId(publicId)
    input.setSystemId(systemId)
    input.setBaseURI(baseURI)
    input.setCharacterStream(new InputStreamReader(resource, Constant.UTF_8))
    input
  }
}
