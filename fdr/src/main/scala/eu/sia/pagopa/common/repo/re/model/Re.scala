package eu.sia.pagopa.common.repo.re.model

import eu.sia.pagopa.common.util.azurehubevent.Appfunction
import net.openhft.hashing.LongHashFunction

import java.time.LocalDateTime

case class Re(
    insertedTimestamp: LocalDateTime,
    componente: String,
    categoriaEvento: String,
    sottoTipoEvento: String,
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
    esito: Option[String] = None,
    sessionId: Option[String] = None,
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
    sessionIdOriginal: Option[String] = None
) {
  val dataOraEvento: String = Appfunction.formatDate(insertedTimestamp)
  val uniqueId: String = s"${dataOraEvento.substring(0, 10)}_${LongHashFunction
    .xx()
    .hashChars(s"$dataOraEvento$sessionId$sessionIdOriginal$status$sottoTipoEvento$erogatore$fruitore$stazione$noticeNumber$paymentToken$idDominio$iuv$ccp$info")}"

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
       |""".stripMargin

  }

}
