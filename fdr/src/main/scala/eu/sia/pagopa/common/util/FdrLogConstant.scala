package eu.sia.pagopa.common.util

object FdrLogConstant {

  def logSintattico(pr: String): String = s"Controlli sintattici [$pr]"
  def logSintattico[T](cl: Class[T]): String = s"Controlli sintattici [${cl.getName}]"
  def logSemantico(pr: String): String = s"Controlli semantici [$pr]"
  def logGeneraPayload(pr: String): String = s"Generazione payload [$pr]"
  def logStart(actorClass: String): String = s"Inizio processo [$actorClass]"
  def logEnd(actorClass: String): String = s"Fine processo [$actorClass]"
  def jobEnd(actorClass: String, key: String): String = s"Fine processo [$actorClass][$key]"
  def callBundle(bundle: String): String = s"Chiamata bundle [$bundle]"
  def forward(primitive: String): String = s"Forward [$primitive]"
  def callBundle(bundle: String, isInput: Boolean): String =
    s"Chiamata bundle [$bundle] [${if (isInput) {
      "INPUT"
    } else {
      "OUTPUT"
    }}]"
}
