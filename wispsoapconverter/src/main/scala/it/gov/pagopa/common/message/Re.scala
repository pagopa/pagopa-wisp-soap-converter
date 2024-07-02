package it.gov.pagopa.common.message

import it.gov.pagopa.common.util.azure.Appfunction
import it.gov.pagopa.common.util.azure.cosmos.{CategoriaEvento, Componente, Esito, SottoTipoEvento}
import net.openhft.hashing.LongHashFunction

import java.time.Instant

case class Re(
               insertedTimestamp: Instant,
               componente: Componente.Value,
               categoriaEvento: CategoriaEvento.Value,
               sottoTipoEvento: SottoTipoEvento.Value,
               idCarrello: Option[String] = None,
               idDominio: Option[String] = None,
               iuv: Option[String] = None,
               ccp: Option[String] = None,
               psp: Option[String] = None,
               tipoVersamento: Option[String] = None,
               tipoEvento: Option[String] = None,
               fruitore: Option[String] = None,
               erogatore: Option[String] = None,
               stazione: Option[String] = None,
               canale: Option[String] = None,
               parametriSpecificiInterfaccia: Option[String] = None,
               esito: Esito.Value,
               sessionId: Option[String] = None,
               flowId: Option[String] = None,
               status: Option[String] = None,
               payload: Option[Array[Byte]] = None,
               info: Option[String] = None,
               businessProcess: Option[String] = None,
               fruitoreDescr: Option[String] = None,
               erogatoreDescr: Option[String] = None,
               pspDescr: Option[String] = None,
               noticeNumber: Option[String] = None,
               creditorReferenceId: Option[String] = None,
               paymentToken: Option[String] = None,
               sessionIdOriginal: Option[String] = None,
               standIn: Option[Boolean] = None
             ) {
  val dataOraEvento: String = Appfunction.formatDate(insertedTimestamp)
  val uniqueId: String = s"${dataOraEvento.substring(0, 10)}_${
    LongHashFunction
      .xx()
      .hashChars(s"$dataOraEvento$sessionId$sessionIdOriginal$status$sottoTipoEvento$erogatore$fruitore$stazione$noticeNumber$paymentToken$idDominio$iuv$ccp$info")
  }"

  val version: String = "1"

  override def toString: String = {
    s"""dataOraEvento=[${dataOraEvento}]
       |tipoEvento=[${tipoEvento}]
       |sottoTipoEvento=[${sottoTipoEvento}]
       |categoriaEvento=[${categoriaEvento}]
       |fruitore=[${fruitore.getOrElse("N.D")}]
       |erogatore=[${erogatore.getOrElse("N.D")}]
       |stazione=[${stazione.getOrElse("N.D")}]
       |canale=[${canale.getOrElse("N.D")}]
       |idDominio=[${idDominio.getOrElse("N.D")}]
       |iuv=[${iuv.getOrElse("N.D")}]
       |ccp=[${ccp.getOrElse("N.D")}]
       |sessionId=[${sessionId.getOrElse("N.D")}]
       |standIn=[${standIn.getOrElse("N.D")}]
       |""".stripMargin

  }

  //  def toProperties(): java.util.Map[java.lang.String, java.lang.Object] = {
  //    Seq(
  //      Some("inserted_timestamp" -> this.insertedTimestamp),
  //      Some("componente" -> this.componente),
  //      Some("categoria_evento" -> this.categoriaEvento),
  //      Some("sotto_tipo_evento" -> this.sottoTipoEvento),
  //      this.idDominio.map(v=>"id_dominio" -> v),
  //      this.iuv.map(v=>"iuv" -> v),
  //      this.ccp.map(v=>"ccp" -> v),
  //      this.psp.map(v=>"psp" -> v),
  //      this.tipoVersamento.map(v=>"tipo_versamento" -> v),
  //      this.tipoEvento.map(v=>"tipo_evento" -> v),
  //      this.fruitore.map(v=>"fruitore" -> v),
  //      this.erogatore.map(v=>"erogatore" -> v),
  //      this.stazione.map(v=>"stazione" -> v),
  //      this.canale.map(v=>"canale" -> v),
  //      this.parametriSpecificiInterfaccia.map(v=>"parametri_specifici_interfaccia" -> v),
  //      this.esito.map(v=>"esito" -> v),
  //      this.sessionId.map(v=>"session_id" -> v),
  //      this.status.map(v=>"status" -> v),
  //      this.info.map(v=>"info" -> v),
  //      this.businessProcess.map(v=>"business_process" -> v),
  //      this.fruitoreDescr.map(v=>"fruitore_descr" -> v),
  //      this.erogatoreDescr.map(v=>"erogatore_descr" -> v),
  //      this.pspDescr.map(v=>"psp_descr" -> v),
  //      this.noticeNumber.map(v=>"notice_number" -> v),
  //      this.creditorReferenceId.map(v=>"creditor_reference_id" -> v),
  //      this.paymentToken.map(v=>"payment_token" -> v)
  //  ).filter(s=>s.nonEmpty).flatten.toMap.asJava.asInstanceOf[java.util.Map[java.lang.String, java.lang.Object]]
  //  }

}
