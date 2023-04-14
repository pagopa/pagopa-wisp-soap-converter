package eu.sia.pagopa.common.repo.fdr

import eu.sia.pagopa.common.message.SchedulerStatus
import eu.sia.pagopa.common.repo.fdr.enums.{FtpFileStatus, RendicontazioneStatus, SchedulerFire, SchedulerFireCheckStatus}
import eu.sia.pagopa.common.repo.util.YNBoolean
import eu.sia.pagopa.common.repo.util.YNBoolean.YNBoolean
import eu.sia.pagopa.common.repo.{BigDecimal2, DBComponent}
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

trait FdrMapping { this: DBComponent =>

  import driver.api._

  implicit val boolColumnType = MappedColumnType.base[YNBoolean, String](
    { case YNBoolean.True => "Y"; case YNBoolean.False => "N" }, // map Bool to NUMBER
    { i =>
      if (i == "Y") YNBoolean.True else YNBoolean.False
    } // map NUMBER to Bool
  )

  implicit val FtpFileStatusMapper: JdbcType[FtpFileStatus.Value] with BaseTypedType[FtpFileStatus.Value] =
    MappedColumnType.base[FtpFileStatus.Value, String](e => e.toString, s => FtpFileStatus.withName(s))

  implicit val SchedulerFireMapper: JdbcType[SchedulerFire.Value] with BaseTypedType[SchedulerFire.Value] =
    MappedColumnType.base[SchedulerFire.Value, String](e => e.toString, s => SchedulerFire.withName(s))

  implicit val SchedulerStatusMapper: JdbcType[SchedulerStatus.Value] with BaseTypedType[SchedulerStatus.Value] =
    MappedColumnType.base[SchedulerStatus.Value, String](e => e.toString, s => SchedulerStatus.withName(s))

  implicit val SchedulerFireCheckStatusMapper: JdbcType[SchedulerFireCheckStatus.Value] with BaseTypedType[SchedulerFireCheckStatus.Value] =
    MappedColumnType.base[SchedulerFireCheckStatus.Value, String](e => e.toString, s => SchedulerFireCheckStatus.withName(s))

  implicit val RendicontazioneStatusMapper: JdbcType[RendicontazioneStatus.Value] with BaseTypedType[RendicontazioneStatus.Value] =
    MappedColumnType.base[RendicontazioneStatus.Value, String](e => e.toString, s => RendicontazioneStatus.withName(s))

  implicit val amountMapper: JdbcType[BigDecimal2] with BaseTypedType[BigDecimal2] =
    MappedColumnType.base[BigDecimal2, BigDecimal](e => e.amount, s => s)

}
