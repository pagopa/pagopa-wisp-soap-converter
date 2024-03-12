import java.io.{ File, PrintWriter }
import java.nio.file.{ Files, Paths, StandardCopyOption }
import scala.xml.{ Node, XML }

case class Element(name: String, `type`: String, namespace: String, schemas: Seq[String])
case class Message(name: String, parts: Seq[MessagePart], schemas: Seq[String]) {
  lazy val body = parts.find(_.name != "header").get
}
case class MessagePart(name: String, element: Element)

object WsdlBuild extends App {

  val srcManagedDir = s"${args.head}/sbt-xsd/it/gov/pagopa/commonxml"
  val schemadir = "/xml-schema"
  val resManagerdDir = s"${args(1)}$schemadir"
  if (!new File(srcManagedDir).exists()) {
    Files.createDirectories(Paths.get(srcManagedDir))
  }
  if (!new File(resManagerdDir).exists()) {
    //    println(s"creazione $resManagerdDir")
    Files.createDirectories(Paths.get(resManagerdDir))
  }

  val srcManaged = new File(srcManagedDir)
  val targetResources = new File(resManagerdDir)

  val scalaxbElementWsdlMap = scala.collection.mutable.Map[String, String]()
  val scalaxbElementXsdMap = scala.collection.mutable.Map[String, String]()

  val targetNamespaceMap = scala.collection.mutable.Map[String, Option[String]]()

  val partsMap = scala.collection.mutable.Map[String, (String, Option[String], String, Option[String])]()

  val foldersMap = scala.collection.mutable.Map[String, String]()
  val rootFolder = new File("common-xml/target/wsdl-schema")

  val objXsdFileMap = scala.collection.mutable.Map[String, Seq[String]]()

  def decap(string: String) = Character.toLowerCase(string.charAt(0)) + string.substring(1)
  def capWithUnderscores(string: String) =
    string
      .replaceAll("([a-z])([A-Z]+)", "$1_$2")
      .replaceAll("RT([A-Z])", "RT_$1")
      .replaceAll("RPT([A-Z])", "RPT_$1")
      .replaceAll("PSP([A-Z])", "PSP_$1")
      .replaceAll("PA([A-Z])", "PA_$1")
      .toUpperCase

  //  println(s"copying all xsd files")
  val wsdls: Seq[(String, (Seq[Element], Seq[Message]))] = rootFolder.listFiles.toList
    .filter(_.isDirectory)
    .flatMap(folder => {
      val foldername = folder.getName
      //copia tutti gli xsd nella cartella target
      var xsdfiles = folder.listFiles
        .filter(f => f.getName.endsWith(".xsd"))
        .map(f => {
          if (!targetResources.toPath.resolve(s"${foldername}").toFile.exists()) {
            Files.createDirectories(targetResources.toPath.resolve(s"${foldername}"))
          }
          Files.copy(f.toPath, targetResources.toPath.resolve(s"${foldername}/${f.getName}"), StandardCopyOption.REPLACE_EXISTING)
          f
        })
        .toSeq

      if (folder.listFiles.exists(_.getName.endsWith(".wsdl"))) {

        var neededXsdFiles: Seq[String] = Seq()

        folder.listFiles
          .filter(f => f.getName.endsWith(".wsdl"))
          .map(wsdlfile => {
            //          println(s"reading ${wsdlfile.getName}")
            //          println(s"extracting wsdl inline schemas")
            val wsdl = XML.loadFile(wsdlfile)
            val schemas = wsdl \ "types" \ "schema"
            schemas.zipWithIndex.foreach(s => {
              val tns = s._1.\@("targetNamespace")
              if (tns.nonEmpty) {
                val filename = wsdlfile.getName.replace(".wsdl", s"_${s._2 + 1}.xsd")
                if (!new File(s"$targetResources/${folder.getName}").exists()) {
                  Files.createDirectories(Paths.get(s"$targetResources/${folder.getName}"))
                }
                val newxsd = new File(s"$targetResources/${folder.getName}/$filename")
                val pw = new PrintWriter(newxsd)
                pw.write(s._1.toString())
                pw.close()
                xsdfiles = xsdfiles.+:(newxsd)
              }
            })

            //prendo tutti gli xsd
            //trovo solo quelli che non sono importati  da altri
            val imported = xsdfiles.flatMap(x => {
              val xsd = XML.loadFile(x)
              val imports = xsd \ "import"
              val includes = xsd \ "include"
              imports.map(_.\@("schemaLocation")) ++ includes.map(_.\@("schemaLocation"))
            })

            val toplevelxsds = xsdfiles.filter(f => !imported.contains(f.getName))

            //          println(s"reading elements from xsds:\n[${toplevelxsds.mkString("\n")}]")
            val folderElements = xsdfiles
              .flatMap(xsdfile => {
                val xsd = XML.loadFile(xsdfile)
                val ns = xsd.\@("targetNamespace")
                val elements = xsd \ "element"
                elements.map(el => {
                  Element(el.\@("name"), getElementType(el), ns, Seq(xsdfile.getName))
                })
              })
              .toSeq

            //          println("reading messages from wsdl")

            val folderMessages = (wsdl \ "message").map(mex => {
              val name = mex.\@("name")
              val parts = (mex \ "part").map(p => {
                val messagePartElement = p.\@("element")
                val split = messagePartElement.split(":")
                val pre = split.head
                val elm = split.last
                val ns = wsdl.getNamespace(pre)

                val fe = folderElements.find(fe => fe.name == elm && fe.namespace == ns)

                if (fe.isEmpty) throw new RuntimeException(s"$elm $ns not found message: ${p.\@("name")}")
                MessagePart(p.\@("name"), fe.get)
              })
              Message(name, parts, (Set("envelope.xsd") ++ toplevelxsds.map(_.getName).toSet).toSeq)
            })

            foldername -> (Seq(), folderMessages)
          })
      } else {

        //prendo tutti gli xsd
        val xsds = folder.listFiles.filter(f => f.getName.endsWith(".xsd"))
        //trovo solo quelli che non sono importati  da altri
        val imported = xsds.flatMap(x => {
          val xsd = XML.loadFile(x)
          val imports = xsd \ "import"
          val includes = xsd \ "include"
          imports.map(_.\@("schemaLocation")) ++ includes.map(_.\@("schemaLocation"))
        })

        val toplevelxsds = xsds.filter(f => !imported.contains(f.getName))

        val folderElements = toplevelxsds
          .flatMap(xsdfile => {
            //          println(s"reading ${xsdfile.getName}")
            //          println(s"reading elements from xsds:\n[${xsdfile}]")
            val xsd = XML.loadFile(xsdfile)
            val ns = xsd.\@("targetNamespace")
            val elements = xsd \ "element"
            elements.map(el => {
              Element(el.\@("name"), getElementType(el), ns, Seq(xsdfile.getName))
            })
          })
          .toSeq
        Seq(foldername -> (folderElements, Seq()))
      }
    })

  val xmlenum =
    s"""
       |package it.gov.pagopa.commonxml
       |
       |import javax.xml.parsers.{DocumentBuilder, DocumentBuilderFactory, SAXParserFactory}
       |import javax.xml.transform.Source
       |import javax.xml.transform.stream.StreamSource
       |import scalaxb.DataRecord
       |import soapenvelope11.{Body, Envelope, Header}
       |
       |import scala.util.Try
       |import scala.xml.{Elem, NamespaceBinding, NodeSeq, SAXParser, XML}
       |/*autogenerated*/
       |object XmlEnum extends Enumeration {
       |  private val xmlTop: String = \"\"\"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>"\"\"
       |
       |

       |  private def saxParser: SAXParser = {
       |    val parserFactory = SAXParserFactory.newInstance()
       |    parserFactory.setNamespaceAware(false)
       |    parserFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
       |    parserFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
       |    parserFactory.newSAXParser()
       |  }
       |
       |  def documentBuilderFactory: DocumentBuilder = {
       |    val parserFactory = DocumentBuilderFactory.newInstance
       |    parserFactory.setNamespaceAware(true)
       |    parserFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
       |    parserFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
       |    parserFactory.newDocumentBuilder()
       |  }
       |
       |  def parseXmlString(xml:String) = {
       |    XML.withSAXParser(saxParser).loadString(xml)
       |  }
       |
       |  def transform(xmlStr : String): String ={
       |    val xml = XML.loadString(xmlStr)
       |    val namespaceToDelete: Map[String, String] = findAllNamespaceBinding(xml.scope, Map())
       |      .filter(n => n._2 != null && n._2.nonEmpty)
       |      .filter(v => !xmlStr.contains(s"$${v._2}:"))
       |
       |    var repl = xmlStr
       |    namespaceToDelete.foreach(ntd => {
       |      val strToReplace = s\"\"\" xmlns:$${ntd._2}="$${ntd._1}"\"\"\"
       |      repl = xmlStr.replaceAll(strToReplace,"")
       |    })
       |
       |    xmlTop + XML.loadString(repl).toString()
       |  }
       |
       |  private def findAllNamespaceBinding(nb : NamespaceBinding, map : Map[String,String]) : Map[String,String] ={
       |    val prefix = nb.prefix
       |    val uri = nb.uri
       |    val newMap: Map[String,String] = map + (uri -> prefix)
       |    if(nb.parent.uri != null && nb.parent.uri.nonEmpty){
       |      findAllNamespaceBinding(nb.parent, newMap)
       |    } else { newMap }
       |  }
       |
       |  private def getHeader(xml: Elem): Try[NodeSeq] = Try({
       |    val node: NodeSeq = xml \\ "Header"
       |    if (node.size > 1) {
       |      throw new IllegalArgumentException("Intestazione ripetuta")
       |    } else if (node.isEmpty) {
       |      throw new IllegalArgumentException("Intestazione mancante")
       |    }
       |    node.head.child.filter(_.isInstanceOf[scala.xml.Elem]).head
       |  })
       |
       |  private def getBody(xml: Elem): Try[NodeSeq] = Try({
       |    val node: NodeSeq = xml \\ "Body"
       |    if (node.size > 1) {
       |      throw new IllegalArgumentException("Body ripetuto")
       |    } else if (node.isEmpty) {
       |      throw new IllegalArgumentException("Body mancante")
       |    }
       |    node.head.child.filter(_.isInstanceOf[scala.xml.Elem]).head
       |  })
       |
       |  private def createEnvelope(scope: NamespaceBinding, body: Seq[DataRecord[Any]], header: Option[Seq[DataRecord[Any]]] = None): NodeSeq = {
       |    val uriEnv = "http://schemas.xmlsoap.org/soap/envelope/"
       |    val prefixEnv = "soapenv"
       |    val nameEnv = "Envelope"
       |    val scopeToAdd : Map[String, String] = findAllNamespaceBinding(scope, Map())
       |    val all : Map[String, String] = findAllNamespaceBinding(soapenvelope11.defaultScope, Map()) ++ scopeToAdd
       |    val replaced: Map[Option[String], String] = all.map(f => {
       |      if(uriEnv==f._1) { Some(prefixEnv) -> f._1 }
       |      else { Some(f._2) -> f._1 }
       |    })
       |    val defaultScope = scalaxb.toScope(replaced.toSeq:_*)
       |    val envelope = Envelope(header.map(Header(_, Map())), Body(body, Map()), Nil, Map())
       |    scalaxb.toXML[Envelope](envelope, Some(uriEnv), Some(nameEnv), defaultScope)
       |  }
       |
       |
       |
       |  ${wsdls
      .flatMap(f => {
        if (f._2._2.nonEmpty) {
          f._2._2.map(mex => s"val ${capWithUnderscores(mex.body.element.`type` + "_" + f._1)} = Value")
        } else {
          f._2._1.map(elm => s"val ${capWithUnderscores(elm.name + "_" + f._1)} = Value")
        }
      })
      .mkString("\n\t")}
       |
       |  def getXsdFolder(xmlEnum:XmlEnum.Value): String = {
       |    xmlEnum match {
       |      ${wsdls
      .flatMap(f => {
        if (f._2._2.nonEmpty) {
          f._2._2.map(mex => {
            s"""case ${capWithUnderscores(mex.body.element.`type` + "_" + f._1)} => "${f._1}"""".stripMargin
          })
        } else {
          f._2._1.map(elm => {
            s"""case ${capWithUnderscores(elm.name + "_" + f._1)} => "${f._1}"""".stripMargin
          })
        }
      })
      .mkString("\n\t")}
       |    }
       |  }
       |  
       |  def loadSchema(xmlEnum: XmlEnum.Value): Array[Source] = {
       |    xmlEnum match {
       |      ${wsdls
      .flatMap(f => {
        if (f._2._2.nonEmpty) {
          f._2._2.map(mex => {
            s"""case ${capWithUnderscores(mex.body.element.`type` + "_" + f._1)} =>
               |      Array[Source](
               |          ${mex.schemas.map(s => s"""new StreamSource(getClass.getResourceAsStream("/xml-schema/${f._1}/${s}"))""").mkString(",\n\t\t\t\t\t")}
               |        )
               |""".stripMargin
          })
        } else {
          f._2._1.map(elm => {
            s"""case ${capWithUnderscores(elm.name + "_" + f._1)} =>
               |      Array[Source](
               |          ${elm.schemas.map(s => s"""new StreamSource(getClass.getResourceAsStream("/xml-schema/${f._1}/${s}"))""").mkString(",\n\t\t\t\t\t")}
               |        )
               |""".stripMargin
          })
        }
      })
      .mkString("\n\t")}
       |    }
       |  }
       |
       |
       |
       |
       |${wsdls
      .flatMap(f => {
        if (f._2._2.nonEmpty) {

          f._2._2.map(mex => {
            val head = mex.parts.find(_.name == "header")
            val body = mex.parts.find(_.name != "header").get
            val scalaxbclassbody = s"scalaxbmodel.${f._1}.${body.element.`type`.capitalize}"

            s"""def ${mex.body.element.`type`}${head.map(_ => "WithHeader").getOrElse("")}2Str_${f._1}(${head
              .map(h => s"header:scalaxbmodel.${f._1}.${h.element.`type`.capitalize},")
              .getOrElse("")}body: ${scalaxbclassbody}): Try[String] = {
               |    Try{
               |      ${head
              .map(h => {
                s"""val scopeHeader = Some("${h.element.namespace}")
                   |      val headerDr = DataRecord(scopeHeader, Some("${h.element.name}"),header)""".stripMargin
              })
              .getOrElse("")}
               |      val scopeBody = Some("${body.element.namespace}")
               |      val bodyDr = DataRecord(scopeBody, Some("${body.element.name}"),body)
               |      val scopeEnv = scalaxbmodel.${f._1}.defaultScope
               |      val envelope = createEnvelope(scopeEnv, Seq(bodyDr)${head.map(_ => ",Some(Seq(headerDr))").getOrElse("")})
               |      transform(envelope.toString)
               |    }
               |  }
               |""".stripMargin
          })
        } else {
          f._2._1.map(elm => {
            val scalaxbclass = s"scalaxbmodel.${f._1}.${elm.`type`.capitalize}"
            s"""  def ${elm.name}2Str_${f._1}(obj: ${scalaxbclass}): Try[String] = {
               |    val v = Try(scalaxb.toXML[${scalaxbclass}](obj.asInstanceOf[${scalaxbclass}], "${elm.name}", scalaxbmodel.${f._1}.defaultScope))
               |    v.map(v => transform(v.toString))
               |  }""".stripMargin
          })
        }
      })
      .mkString("\n\t")}
       |
       |
       |${wsdls
      .flatMap(f => {
        if (f._2._2.nonEmpty) {

          f._2._2.map(mex => {
            val head = mex.parts.find(_.name == "header")
            val body = mex.parts.find(_.name != "header").get
            val scalaxbclassbody = s"scalaxbmodel.${f._1}.${body.element.`type`.capitalize}"

            s"""  def str2${mex.name}${head.map(_ => "WithHeader").getOrElse("")}_${f._1}(xml: String): Try[${head
              .map(h => s"(${s"scalaxbmodel.${f._1}.${h.element.`type`.capitalize}"},${scalaxbclassbody})")
              .getOrElse(scalaxbclassbody)}] = {
               |    for {
               |      x <- Try(XML.withSAXParser(saxParser).loadString(xml))
               |      ${head
              .map(h => {
                val scalaxbclasshead = s"scalaxbmodel.${f._1}.${h.element.`type`.capitalize}"
                s"""headerTry <- getHeader(x)
                   |header <- Try(scalaxb.fromXML[${scalaxbclasshead}](headerTry).asInstanceOf[${scalaxbclasshead}])
                   |""".stripMargin
              })
              .getOrElse("")}
               |      bodyTry <- getBody(x)
               |      body <- Try(scalaxb.fromXML[${scalaxbclassbody}](bodyTry).asInstanceOf[${scalaxbclassbody}])
               |    } yield ${head.map(_ => "(header,body)").getOrElse("body")}
               |  }""".stripMargin
          })
        } else {
          f._2._1.map(elm => {
            val scalaxbclass = s"scalaxbmodel.${f._1}.${elm.`type`.capitalize}"
            s"""  def str2${elm.name}_${f._1}(xml:String): Try[${scalaxbclass}] = {
               |  for{
               |    nodeSeq <- Try(XML.withSAXParser(saxParser).loadString(xml))
               |    element <- Try(scalaxb.fromXML[${scalaxbclass}](nodeSeq).asInstanceOf[${scalaxbclass}])
               |  } yield element
               |  }""".stripMargin
          })
        }
      })
      .mkString("\n\t")}
       |
       |}""".stripMargin

  //  println("writing file")
  val pw = new PrintWriter(new File(s"$srcManaged/XmlEnum.scala"))
  pw.write(xmlenum)
  pw.close()
  def getElementType(node: Node) = {
    val split = node.attribute("type").getOrElse(node.attribute("name").get).toString().split(":")
    if (split.size > 1) {
      split(1) //.capitalize
    } else {
      split.head //.capitalize
    }
  }

  Nil
}
