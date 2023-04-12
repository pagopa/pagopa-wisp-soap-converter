package eu.sia.pagopa.common.repo.offline.model

import eu.sia.pagopa.common.repo.offline.enums.RendicontazioneBolloStatus

import java.time.LocalDateTime

case class RendicontazioneBollo(
    objId: Long,
    idPa: Option[String] = None,
    fkSftpFile: Option[Long] = None,
    timestampInserimento: LocalDateTime,
    timestampStartOfTheWeek: LocalDateTime,
    timestampEndOfTheWeek: LocalDateTime,
    progressive: Long,
    fileName: String,
    rendicontazioneBolloStatus: RendicontazioneBolloStatus.Value,
    fileNameEsito: Option[String] = None,
    timestampInvioFlussoMarcheDigitali: Option[LocalDateTime] = None,
    timestampDataEsitoFlusso: Option[LocalDateTime] = None,
    timestampRicezioneEsito: Option[LocalDateTime] = None,
    fkSftpFileEsito: Option[Long] = None
)
