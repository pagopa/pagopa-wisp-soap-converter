package eu.sia.pagopa.common.repo.fdr.table

import eu.sia.pagopa.common.repo.DBComponent
import eu.sia.pagopa.common.repo.fdr.model.BinaryFile

trait BinaryFileTable { self: DBComponent =>

  import driver.api._

  class BinaryFileTable(tag: Tag) extends Table[BinaryFile](tag, adjustName("BINARY_FILE")) {
    def objId = column[Long](adjustName("ID"), O.PrimaryKey, O.AutoInc)
    def fileSize = column[Long](adjustName("FILE_SIZE"))
    def fileContent = column[Option[Array[Byte]]](adjustName("FILE_CONTENT"))
    def signatureType = column[Option[String]](adjustName("SIGNATURE_TYPE"))
//    def xmlFileContent = column[Option[String]](adjustName("XML_FILE_CONTENT"))

    override def * =
      (objId, fileSize, fileContent, signatureType) <> (BinaryFile.tupled, BinaryFile.unapply)
  }

  def binaryFilesTable = TableQuery[BinaryFileTable]
}
