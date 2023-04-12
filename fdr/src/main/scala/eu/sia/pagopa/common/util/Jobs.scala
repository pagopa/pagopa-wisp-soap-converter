package eu.sia.pagopa.common.util

case class Job(name: String, descr: String)
object Jobs {
  val FTP_UPLOAD_RETRY: Job = Job("ftpUpload", "FTP retry upload")

  def descr(name: String): String = {
    toSeq.find(_._1 == name).fold(name)(_._2)
  }
  val toSeq: Seq[(String, String)] = {
    Seq(
      FTP_UPLOAD_RETRY.name -> FTP_UPLOAD_RETRY.descr
    ).sortBy(_._2)
  }
}
