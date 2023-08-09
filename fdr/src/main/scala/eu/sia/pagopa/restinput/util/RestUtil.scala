package eu.sia.pagopa.restinput.util

object RestUtil {

  def errorMessageJson(mex: String, ex: Option[Throwable] = None): String = {
    val stack = ex.map(e => s""","stack_trace" : "${e.getStackTrace.mkString("\\n\\t")}"""").getOrElse("")
    s"""{"error" : "$mex"$stack}""".stripMargin
  }

}
