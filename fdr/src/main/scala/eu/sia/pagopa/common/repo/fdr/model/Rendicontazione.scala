package eu.sia.pagopa.common.repo.fdr.model

import eu.sia.pagopa.common.repo.fdr.enums.RendicontazioneStatus

import java.time.LocalDateTime

case class Rendicontazione(
    objId: Long,
    stato: RendicontazioneStatus.Value,
    optlock: Long,
    psp: String,
    intermediarioPsp: Option[String],
    canale: Option[String],
    dominio: String,
    idFlusso: String,
    dataOraFlusso: LocalDateTime,
    fk_binary_file: Option[Long],
    fk_sftp_file: Option[Long]
)
