package eu.sia.pagopa.common.repo.offline.table

import eu.sia.pagopa.common.repo.DBComponent
import eu.sia.pagopa.common.repo.offline.OfflineMapping
import eu.sia.pagopa.common.repo.offline.enums.FtpFileStatus
import eu.sia.pagopa.common.repo.offline.model.FtpFile
import slick.lifted.ProvenShape

import java.time.LocalDateTime

trait SftpFilesTable { self: DBComponent with OfflineMapping =>

  import driver.api._

  class SendQueueRendicontazione(tag: Tag) extends Table[FtpFile](tag, adjustName("RENDICONTAZIONE_SFTP_SEND_QUEUE")) {
    def id: Rep[Long] = column[Long](adjustName("ID"), O.PrimaryKey, O.AutoInc)
    def fileSize: Rep[Long] = column[Long](adjustName("FILE_SIZE"))
    def content: Rep[Array[Byte]] = column[Array[Byte]](adjustName("CONTENT"))
    def hash: Rep[String] = column[String](adjustName("HASH"))
    def fileName: Rep[String] = column[String](adjustName("FILE_NAME"))
    def path: Rep[String] = column[String](adjustName("PATH"))
    def stato: Rep[FtpFileStatus.Value] = column[FtpFileStatus.Value](adjustName("STATUS"))
    def serverId: Rep[Long] = column[Long](adjustName("SERVER_ID"))
    def hostName: Rep[String] = column[String](adjustName("HOST_NAME"))
    def port: Rep[Int] = column[Int](adjustName("PORT"))
    def retry: Rep[Int] = column[Int](adjustName("RETRY"))
    def insertedDate: Rep[LocalDateTime] = column[LocalDateTime](adjustName("INSERTED_TIMESTAMP"))
    def updatedDate: Rep[LocalDateTime] = column[LocalDateTime](adjustName("UPDATED_TIMESTAMP"))
    def insertedBy: Rep[String] = column[String](adjustName("INSERTED_BY"))
    def updatedBy: Rep[String] = column[String](adjustName("UPDATED_BY"))

    override def * : ProvenShape[FtpFile] =
      (id, fileSize, content, hash, fileName, path, stato, serverId, hostName, port, retry, insertedDate, updatedDate, insertedBy, updatedBy) <> (FtpFile.tupled, FtpFile.unapply)
  }

  class ReceiveQueueRendicontazione(tag: Tag) extends Table[FtpFile](tag, adjustName("RENDICONTAZIONE_SFTP_RECEIVE_QUEUE")) {
    def id: Rep[Long] = column[Long](adjustName("ID"), O.PrimaryKey, O.AutoInc)
    def fileSize: Rep[Long] = column[Long](adjustName("FILE_SIZE"))
    def content: Rep[Array[Byte]] = column[Array[Byte]](adjustName("CONTENT"))
    def hash: Rep[String] = column[String](adjustName("HASH"))
    def fileName: Rep[String] = column[String](adjustName("FILE_NAME"))
    def path: Rep[String] = column[String](adjustName("PATH"))
    def stato: Rep[FtpFileStatus.Value] = column[FtpFileStatus.Value](adjustName("STATUS"))
    def serverId: Rep[Long] = column[Long](adjustName("SERVER_ID"))
    def hostName: Rep[String] = column[String](adjustName("HOST_NAME"))
    def port: Rep[Int] = column[Int](adjustName("PORT"))
    //    def retry: Rep[Int] = column[Int](adjustName("RETRY"))
    def insertedDate: Rep[LocalDateTime] = column[LocalDateTime](adjustName("INSERTED_TIMESTAMP"))
    def updatedDate: Rep[LocalDateTime] = column[LocalDateTime](adjustName("UPDATED_TIMESTAMP"))
    def insertedBy: Rep[String] = column[String](adjustName("INSERTED_BY"))
    def updatedBy: Rep[String] = column[String](adjustName("UPDATED_BY"))

    override def * : ProvenShape[FtpFile] =
      (id, fileSize, content, hash, fileName, path, stato, serverId, hostName, port, 0, insertedDate, updatedDate, insertedBy, updatedBy) <> (FtpFile.tupled, FtpFile.unapply)
  }

  def sendRendicontazione: TableQuery[SendQueueRendicontazione] = TableQuery[SendQueueRendicontazione]
  def receiveRendicontazione: TableQuery[ReceiveQueueRendicontazione] = TableQuery[ReceiveQueueRendicontazione]

}
