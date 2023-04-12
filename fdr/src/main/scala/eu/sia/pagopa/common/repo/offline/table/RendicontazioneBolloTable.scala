package eu.sia.pagopa.common.repo.offline.table

import eu.sia.pagopa.common.repo.DBComponent
import eu.sia.pagopa.common.repo.offline.OfflineMapping
import eu.sia.pagopa.common.repo.offline.enums.RendicontazioneBolloStatus
import eu.sia.pagopa.common.repo.offline.model.{ RendicontazioneBollo, RendicontazioneVersamentoBollo }

import java.time.LocalDateTime

trait RendicontazioneBolloTable { self: DBComponent with OfflineMapping =>

  import driver.api._

  class RendicontazioniVersamentoBollo(tag: Tag) extends Table[RendicontazioneVersamentoBollo](tag, adjustName("RENDICONTAZIONE_BOLLO_VERSAMENTO")) {
    def fkRendicontazione = column[Long](adjustName("FK_RENDICONTAZIONE_BOLLO"))
    def fkRtVersamentoBollo = column[Long](adjustName("FK_VERSAMENTO_BOLLO"))

    override def * = (fkRendicontazione, fkRtVersamentoBollo) <> (RendicontazioneVersamentoBollo.tupled, RendicontazioneVersamentoBollo.unapply)
  }

  class RendicontazioniBollo(tag: Tag) extends Table[RendicontazioneBollo](tag, adjustName("RENDICONTAZIONE_BOLLO")) {
    def objId = column[Long](adjustName("ID"), O.PrimaryKey, O.AutoInc)
    def idPa = column[Option[String]](adjustName("ID_DOMINIO"))
    def fkSftpFile = column[Option[Long]](adjustName("FK_SFTP_FILE"))
    def timestampInserimento = column[LocalDateTime](adjustName("TIMESTAMP_INS"))
    def timestampStartOfTheWeek = column[LocalDateTime](adjustName("TIMESTAMP_START_WEEK"))
    def timestampEndOfTheWeek = column[LocalDateTime](adjustName("TIMESTAMP_END_WEEK"))
    def progressive = column[Long](adjustName("RENDICONTAZIONE_BOLLO_PROGRESSIVE"))
    def fileName = column[String](adjustName("FILE_NAME"))
    def rendicontazioneBolloStatus = column[RendicontazioneBolloStatus.Value](adjustName("STATUS"))
    def fileNameEsito = column[Option[String]](adjustName("FILE_NAME_ESITO"))
    def timestampInvioFlussoMarcheDigitali = column[Option[LocalDateTime]](adjustName("TIMESTAMP_INVIO_FLUSSO_MARCHE_DIGITALI"))
    def timestampDataEsitoFlusso = column[Option[LocalDateTime]](adjustName("TIMESTAMP_DATA_ESITO_FLUSSO"))
    def timestampRicezioneEsito = column[Option[LocalDateTime]](adjustName("TIMESTAMP_RICEZIONE_ESITO"))
    def fkSftpFileEsito = column[Option[Long]](adjustName("FK_SFTP_FILE_ESITO"))

    override def * =
      (
        objId,
        idPa,
        fkSftpFile,
        timestampInserimento,
        timestampStartOfTheWeek,
        timestampEndOfTheWeek,
        progressive,
        fileName,
        rendicontazioneBolloStatus,
        fileNameEsito,
        timestampInvioFlussoMarcheDigitali,
        timestampDataEsitoFlusso,
        timestampRicezioneEsito,
        fkSftpFileEsito
      ) <> (RendicontazioneBollo.tupled, RendicontazioneBollo.unapply)
  }

  def rendicontazioniBollo = TableQuery[RendicontazioniBollo]
  def rendicontazioniVersamentoBollo = TableQuery[RendicontazioniVersamentoBollo]
}
