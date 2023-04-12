package eu.sia.pagopa.common.repo.offline.table

import eu.sia.pagopa.common.repo.DBComponent
import eu.sia.pagopa.common.repo.offline.OfflineMapping
import eu.sia.pagopa.common.repo.offline.enums.RendicontazioneStatus
import eu.sia.pagopa.common.repo.offline.model.Rendicontazione

import java.time.LocalDateTime

trait RendicontazioneTable { self: DBComponent with OfflineMapping =>

  import driver.api._

  class Rendicontazioni(tag: Tag) extends Table[Rendicontazione](tag, adjustName("RENDICONTAZIONE")) {
    override def * =
      (objId, stato, optlock, psp, intermediarioPsp, canale, dominio, idFlusso, dataOraFlusso, fk_binary_file, fk_sftp_file) <> (Rendicontazione.tupled, Rendicontazione.unapply)

    def objId = column[Long](adjustName("ID"), O.PrimaryKey, O.AutoInc)
    def stato = column[RendicontazioneStatus.Value](adjustName("STATO"))
    def optlock = column[Long](adjustName("OPTLOCK"))
    def psp = column[String](adjustName("PSP"))
    def intermediarioPsp = column[Option[String]](adjustName("INTERMEDIARIO"))
    def canale = column[Option[String]](adjustName("CANALE"))
    def dominio = column[String](adjustName("DOMINIO"))
    def idFlusso = column[String](adjustName("ID_FLUSSO"))
    def dataOraFlusso = column[LocalDateTime](adjustName("DATA_ORA_FLUSSO"))
    def fk_binary_file = column[Option[Long]](adjustName("FK_BINARY_FILE"))
    def fk_sftp_file = column[Option[Long]](adjustName("FK_SFTP_FILE"))
    //def binaryFile = foreignKey("fk_binary_file", FK_BINARY_FILE, binaryFiles)(_.objId)

  }

  def rendicontazioni = TableQuery[Rendicontazioni]

}
