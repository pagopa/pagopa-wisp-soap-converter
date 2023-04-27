package eu.sia.pagopa.common.util

object FdrLogConstant {

  def logSintattico(pr: String): String = s"Syntax check [$pr]"
  def logSintattico[T](cl: Class[T]): String = s"Syntax check [${cl.getName}]"
  def logSemantico(pr: String): String = s"Semantic check [$pr]"
  def logGeneraPayload(pr: String): String = s"Make payload [$pr]"
  def logStart(actorClass: String): String = s"Start process [$actorClass]"
  def logEnd(actorClass: String): String = s"End process [$actorClass]"
  def jobEnd(actorClass: String, key: String): String = s"End process [$actorClass][$key]"
  def callBundle(bundle: String): String = s"Call bundle [$bundle]"
  def forward(primitive: String): String = s"Forward [$primitive]"
  def callBundle(bundle: String, isInput: Boolean): String =
    s"Call bundle [$bundle] [${if (isInput) {
      "INPUT"
    } else {
      "OUTPUT"
    }}]"
}
