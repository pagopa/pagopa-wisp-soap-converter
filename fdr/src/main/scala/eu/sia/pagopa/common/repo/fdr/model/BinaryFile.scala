package eu.sia.pagopa.common.repo.fdr.model

case class BinaryFile(objId: Long, fileSize: Long, fileContent: Option[Array[Byte]] = None, signatureType: Option[String] = None)
