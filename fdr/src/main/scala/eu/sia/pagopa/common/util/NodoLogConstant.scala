package eu.sia.pagopa.common.util

object NodoLogConstant {

  def logSintattico(pr: String): String = s"Controlli sintattici [$pr] in corso"
  def logSintattico[T](cl: Class[T]): String = s"Controlli sintattici [${cl.getName}] in corso"
  def logSemantico(pr: String): String = s"Controlli semantici [$pr] in corso"
  def logGeneraPayload(pr: String): String = s"Generazione payload [$pr] in corso"
  def logGeneraEntity(entity: String): String = s"Generazione entity [$entity] in corso"
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
