package eu.sia.pagopa.rendicontazioni.util

object RecordUtils {

  val FORMAT_RENDICONTAZIONE_BOLLO = "yyyy-MM-dd"
  val ALIGN_RIGHT = "R"
  val ALIGN_LEFT = "L"

  val rexExData = "([12]\\d{3}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\\d|3[01]))"

  def toFixedLength(record: Any, length: Int, align: String = ALIGN_LEFT, separator: String = " "): String = {
    val recordstring = record match {
      case s: String =>
        s
      case l: Long =>
        l.toString
      case Some(x) =>
        x match {
          case s: String => s
          case x: Any    => x.toString
        }
      case None => ""
    }
    align match {
      case ALIGN_LEFT =>
        s"$recordstring${separator * length}".substring(0, length)
      case ALIGN_RIGHT =>
        val x = s"${separator * length}$recordstring"
        x.substring(x.length - length)
    }
  }

}
