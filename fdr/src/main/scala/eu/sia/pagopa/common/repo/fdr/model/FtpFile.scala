package eu.sia.pagopa.common.repo.fdr.model

import eu.sia.pagopa.common.repo.fdr.enums.FtpFileStatus

import java.time.LocalDateTime

case class FtpFile(
    id: Long,
    fileSize: Long,
    content: Array[Byte],
    hash: String,
    fileName: String,
    path: String,
    stato: FtpFileStatus.Value = FtpFileStatus.NEW,
    serverId: Long,
    hostName: String,
    port: Int,
    retry: Int,
    insertDate: LocalDateTime,
    updateDate: LocalDateTime,
    insertedBy: String,
    updatedBy: String
)
