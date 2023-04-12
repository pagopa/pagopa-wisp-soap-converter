package eu.sia.pagopa.common.repo.offline.enums

object FtpFileStatus extends Enumeration {
  val NEW, TO_UPLOAD, UPLOADED, TO_DOWNLOAD, DOWNLOADED, EXPIRED = Value
}
