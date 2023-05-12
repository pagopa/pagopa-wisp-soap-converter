package eu.sia.pagopa.testutil

import akka.actor.ActorSystem
import eu.sia.pagopa.ActorProps
import eu.sia.pagopa.common.actor.ActorUtility
import eu.sia.pagopa.common.message.{SimpleHttpReq, SimpleHttpRes}
import eu.sia.pagopa.common.util.{NodoLogger, RandomStringUtils}

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.Try

class ActorUtilityTest() extends ActorUtility {

  val requests: scala.collection.mutable.Map[String, String] = new mutable.HashMap[String, String]()

  var configuration: Map[String, (String, String) => String] = Map()
  var configurationResponses: Map[String, (String, String) => String] = Map()

  def configureMocker(config: (String, (String, String) => String)) = {
    configuration = configuration.+(config)
  }

  def configureMockResponse(config: (String, (String, String) => String)) = {
    configurationResponses = configurationResponses.+(config)
  }

  def mocker(log: NodoLogger, messageType: String, payload: Option[String], testCaseId: Option[String], sessionId: String): SimpleHttpRes = {
    requests.put(s"${sessionId}", payload.getOrElse(""))
    val testCase_ = testCaseId
      .flatMap(tcid =>
        configuration
          .get(tcid)
          .map(f => {
            f.apply(messageType, payload.get)
          })
          .orElse(Some(tcid))
      )
      .getOrElse("OK")
    val testCase = if (testCase_.startsWith("delay_")) {
      val d = testCase_.split("_")
      Thread.sleep(d(1).toInt)
      d.last
    } else {
      testCase_
    }
    if (testCase == "timeout") {
      SimpleHttpRes(sessionId, 200, Nil, None, None, testCaseId)
    } else if (testCase == "irraggiungibile") {
      SimpleHttpRes(sessionId, 200, Nil, None, None, testCaseId)
    } else {
      val responsePayload = Try({
        val pr = SpecsUtils.loadTestXMLOrJSON(s"/mocks/$messageType/$testCase").replace("{RANDOM}", RandomStringUtils.randomAlphanumeric(30))
        configurationResponses
          .get(testCase)
          .map(fun => {
            configurationResponses = configurationResponses.-(testCase)
            fun(messageType, pr)
          })
          .getOrElse(pr) -> 200
      }).getOrElse({
        log.warn(s"mock file not found /mocks/$messageType/$testCase.xml")
        s"mock file not found /mocks/$messageType/$testCase.xml" -> 500
      })
      SimpleHttpRes(sessionId, 200, Nil, Some(responsePayload._1), None, testCaseId)
    }

  }

  override def callHttp(req: SimpleHttpReq, actorProps: ActorProps, isSoapProtocol: Boolean)(implicit log: NodoLogger, system: ActorSystem): Future[SimpleHttpRes] = {
    val testCase_ = req.testCaseId
      .flatMap(tcid =>
        configuration
          .get(tcid)
          .map(f => {
            f.apply(req.messageType, req.payload.getOrElse(""))
          })
          .orElse(Some(tcid))
      )
      .getOrElse("")
    val res = if (testCase_.contains("timeout")) {
      SimpleHttpRes(req.sessionId, 408, Nil, None, None, req.testCaseId)
    } else if (testCase_.contains("irraggiungibile")) {
      SimpleHttpRes(req.sessionId, 408, Nil, None, None, req.testCaseId)
    } else if (testCase_.contains("bad_request")) {
      SimpleHttpRes(req.sessionId, 400, Nil, None, None, req.testCaseId)
    } else {
      val res = mocker(log, req.messageType, req.payload, req.testCaseId, req.sessionId)
      SimpleHttpRes(res.sessionId, res.statusCode, Nil, res.payload, res.throwable, res.testCaseId)
    }
    Future.successful(res)
  }

}
