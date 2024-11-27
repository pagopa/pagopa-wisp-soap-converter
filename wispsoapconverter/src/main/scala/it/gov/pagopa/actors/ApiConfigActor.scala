package it.gov.pagopa.actors

import it.gov.pagopa.ActorProps
import it.gov.pagopa.common.actor.BaseActor
import it.gov.pagopa.common.message._
import it.gov.pagopa.common.repo.CosmosRepository
import it.gov.pagopa.common.util.ConfigUtil

import java.util.UUID
import scala.concurrent.duration._
case class GetCache(override val sessionId: String, cacheId: String) extends BaseMessage
case class CheckCache(override val sessionId: String) extends BaseMessage
final case class ApiConfigActor(cosmosRepository: CosmosRepository,actorProps: ActorProps) extends BaseActor {

  implicit val system = context.system

  private val scheduleMinutes: FiniteDuration = context.system.settings.config.getInt("configScheduleMinutes").minute

  context.system.scheduler.scheduleOnce(scheduleMinutes, self, CheckCache(UUID.randomUUID().toString))

  override def receive: Receive = {
    case CheckCache(sid) =>
      log.debug("checking api-config cache. last id: " + actorProps.ddataMap.version)
      (for {
        lastCacheId <- ConfigUtil.getConfigVersion()
        _ =
          if (lastCacheId.isDefined && lastCacheId.get != actorProps.ddataMap.version) {
            self ! GetCache(sid, lastCacheId.get)
          } else {
            log.debug("no new cache version found")
            context.system.scheduler.scheduleOnce(scheduleMinutes, self, CheckCache(UUID.randomUUID().toString))
          }
      } yield ()).recover({ case e =>
        log.error(e, s"ConfigUtil.getConfigIdHttp error")
        context.system.scheduler.scheduleOnce(scheduleMinutes, self, CheckCache(UUID.randomUUID().toString))
      })
    case GetCache(_, cacheId) =>
      log.info(s"GetCache $cacheId requested")
      (for {
        cacheData <- ConfigUtil.getConfigHttp()
        _ = actorProps.ddataMap = cacheData
        _ = log.info(s"Cache $cacheId acquired")
      } yield ())
        .recover({ case e =>
          log.error(e, s"GetCache $cacheId error")
        })
        .map(_ => {
          context.system.scheduler.scheduleOnce(scheduleMinutes, self, CheckCache(UUID.randomUUID().toString))
        })

  }

}
