package eu.sia.pagopa.common.exception

case class RestException(message: String, jsonMessage: String, statusCode: Int, throwable: Throwable = None.orNull) extends Exception(message, throwable) {

  override def getMessage: String = {
    if (message.isEmpty) {
      super.getMessage
    } else {
      message
    }
  }
}

object RestException {
  def apply(jsonMessage: String, statusCode: Int, throwable: Throwable): RestException =
    RestException(jsonMessage, jsonMessage, statusCode, throwable)
  def apply(jsonMessage: String, statusCode: Int): RestException = RestException(jsonMessage, jsonMessage, statusCode)
  def apply(e: Exception, jsonMessage: String, statusCode: Int): RestException =
    RestException(e.getMessage, jsonMessage, statusCode, e)
}
