package eu.sia.pagopa.common.repo.fdr.enums

object FtpFileStatus extends Enumeration {
  val NEW, TO_UPLOAD, UPLOADED, TO_DOWNLOAD, DOWNLOADED, EXPIRED = Value
}
