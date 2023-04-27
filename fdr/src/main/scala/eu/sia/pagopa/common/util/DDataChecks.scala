package eu.sia.pagopa.common.util

import eu.sia.pagopa.Main.ConfigData
import eu.sia.pagopa.common.exception
import eu.sia.pagopa.common.exception.DigitPaErrorCodes
import it.pagopa.config._

import scala.util.{Failure, Success, Try}

object DDataChecks {

  def getConfigurationKeys(ddataMap: ConfigData, key: String, bundleName: String = "GLOBAL"): String = {
    ddataMap.configurations.get(s"$bundleName-$key").map(_.value).getOrElse(throw new RuntimeException(s"$bundleName-$key non presente"))
  }

  def checkPA(log: NodoLogger, ddataMap: ConfigData, idPa: String): Try[CreditorInstitution] = {
    ddataMap.creditorInstitutions.get(idPa) match {
      case Some(value) =>
        if (value.enabled) {
          log.debug(s"[$idPa] found and enabled")
          Success(value)
        } else {
          log.warn(s"[$idPa] found but disabled")
          Failure(DigitPaErrorCodes.PPT_DOMINIO_DISABILITATO)
        }
      case None =>
        log.warn(s"[$idPa] not found")
        Failure(DigitPaErrorCodes.PPT_DOMINIO_SCONOSCIUTO)
    }
  }

  def checkPsp(log: NodoLogger, ddataMap: ConfigData, idPsp: String): Try[PaymentServiceProvider] = {
    ddataMap.psps.get(idPsp) match {
      case Some(value) =>
        if (value.enabled) {
          log.debug(s"[$idPsp] found and enabled")
          Success(value)
        } else {
          log.warn(s"[$idPsp] found but disabled")
          Failure(DigitPaErrorCodes.PPT_PSP_DISABILITATO)
        }
      case None =>
        log.warn(s"[$idPsp] not found")
        Failure(DigitPaErrorCodes.PPT_PSP_SCONOSCIUTO)
    }
  }

  def checkIntermediarioPSP(log: NodoLogger, ddataMap: ConfigData, idIntPsp: String): Try[BrokerPsp] = {
    ddataMap.pspBrokers.get(idIntPsp) match {
      case Some(value) =>
        if (value.enabled) {
          log.debug(s"[$idIntPsp] found and enabled")
          Success(value)
        } else {
          log.warn(s"[$idIntPsp] found but disabled")
          Failure(DigitPaErrorCodes.PPT_INTERMEDIARIO_PSP_DISABILITATO)
        }
      case None =>
        log.warn(s"[$idIntPsp] not found")
        Failure(DigitPaErrorCodes.PPT_INTERMEDIARIO_PSP_SCONOSCIUTO)
    }
  }

  def checkCodifiche(log: NodoLogger, ddataMap: ConfigData, formatoCodifica: String): Try[Encoding] = {
    ddataMap.encodings.get(formatoCodifica) match {
      case Some(value) =>
        log.debug(s"[$formatoCodifica] found")
        Success(value)
      case None =>
        log.warn(s"[$formatoCodifica] not found")
        Failure(DigitPaErrorCodes.PPT_CODIFICA_PSP_SCONOSCIUTA)
    }
  }

  def checkCanale(log: NodoLogger, ddataMap: ConfigData, idCanale: String, password: Option[String], checkPassword: Boolean): Try[Channel] = {
    ddataMap.channels.get(idCanale) match {
      case Some(value) =>
        if (value.enabled) {
          log.debug(s"[$idCanale] found and enabled")
          if (!checkPassword || password.forall(value.password.contains(_))) {
            Success(value)
          } else {
            log.warn(s"[$idCanale] found and enabled, wrong password")
            Failure(exception.DigitPaException("Password sconosciuta o errata", DigitPaErrorCodes.PPT_AUTENTICAZIONE))
          }
        } else {
          log.warn(s"[$idCanale] found but disabled")
          Failure(DigitPaErrorCodes.PPT_CANALE_DISABILITATO)
        }
      case None =>
        log.warn(s"[$idCanale] not found")
        Failure(DigitPaErrorCodes.PPT_CANALE_SCONOSCIUTO)
    }
  }

  def checkStazione(log: NodoLogger, ddataMap: ConfigData, idStazione: String, password: Option[String], checkPassword: Boolean): Try[Station] = {
    ddataMap.stations.get(idStazione) match {
      case Some(value) =>
        if (value.enabled) {
          log.debug(s"[$idStazione] found and enabled")
          if (!checkPassword || password.forall(value.password.contains(_))) {
            Success(value)
          } else {
            log.warn(s"[$idStazione] found and enabled, wrong password")
            Failure(exception.DigitPaException("Password sconosciuta o errata", DigitPaErrorCodes.PPT_AUTENTICAZIONE))
          }
        } else {
          log.warn(s"[$idStazione] found but disabled")
          Failure(DigitPaErrorCodes.PPT_STAZIONE_INT_PA_DISABILITATA)
        }
      case None =>
        log.warn(s"[$idStazione] non trovata")
        Failure(DigitPaErrorCodes.PPT_STAZIONE_INT_PA_SCONOSCIUTA)
    }
  }

  def checkPspCanaleTipoVersamento(log: NodoLogger, ddataMap: ConfigData, psp: PaymentServiceProvider, canale: Channel, tvopt: Option[PaymentType]): Try[PspChannelPaymentType] = {

    val c = ddataMap.pspChannelPaymentTypes.filter(pspctv => {
      val pspc = pspctv._2
      pspc.channelCode == canale.channelCode && pspc.pspCode == psp.pspCode
    })
    if (c.isEmpty) {
      log.warn(s"[psp:${psp.pspCode},canale:${canale.channelCode}] wrong configuration psp-canale")
      Failure(exception.DigitPaException("Configurazione psp-canale non corretta", DigitPaErrorCodes.PPT_AUTORIZZAZIONE))
    } else {
      c.find(a => tvopt.forall(_.paymentType == a._2.paymentType)) match {
        case Some(value) =>
          Success(value._2)
        case None =>
          log.warn(s"[psp:${psp.pspCode},canale:${canale.channelCode},tipoVersamento:${tvopt.getOrElse("n.a")}] wrong configuration psp-canale-tipoVersamento")
          Failure(exception.DigitPaException("Configurazione psp-canale-tipoVersamento non corretta", DigitPaErrorCodes.PPT_AUTORIZZAZIONE))
      }
    }

  }

  def checkTipoVersamento(log: NodoLogger, ddataMap: ConfigData, tipoVersamento: String): Try[PaymentType] = {
    ddataMap.paymentTypes.get(tipoVersamento) match {
      case Some(value) => Success(value)
      case None =>
        log.warn(s"[$tipoVersamento] Unknown payment type")
        Failure(DigitPaErrorCodes.PPT_TIPO_VERSAMENTO_SCONOSCIUTO)
    }
  }

  //noinspection ScalaStyle
  def checkPspIntermediarioPspCanale(
      log: NodoLogger,
      ddataMap: ConfigData,
      idPsp: Option[String] = None,
      idIntermediarioPsp: String,
      idCanale: Option[String] = None,
      password: Option[String] = None,
      tipoVers: Option[String] = None,
      checkPassword: Boolean
  ): Try[(Option[PaymentServiceProvider], BrokerPsp, Option[Channel])] = {

    for {
      pspOpt <- idPsp match {
        case Some(idpsp) =>
          checkPsp(log, ddataMap, idpsp).map(Some(_))
        case None =>
          Success(None)
      }
      intermediarioPsp <- checkIntermediarioPSP(log, ddataMap, idIntermediarioPsp)
      canaleOpt <- idCanale match {
        case Some(idc) =>
          checkCanale(log, ddataMap, idc, password, checkPassword).map(Some(_))
        case None =>
          Success(None)
      }
      _ <-
        if (canaleOpt.forall(can => can.brokerPspCode == intermediarioPsp.brokerPspCode)) {
          Success(())
        } else {
          log.warn(s"Id PA per [canale:${idCanale.getOrElse("n.a")},intermediarioPsp:$idIntermediarioPsp] wrong configuration intermediario-canale")
          Failure(exception.DigitPaException("Configurazione intermediario-canale non corretta", DigitPaErrorCodes.PPT_AUTORIZZAZIONE))
        }
      tipoVersOpt <- tipoVers match {
        case Some(tv) =>
          checkTipoVersamento(log, ddataMap, tv).map(Some(_))
        case None =>
          Success(None)
      }
      pspcanale = for {
        psp <- pspOpt
        canale <- canaleOpt
      } yield (psp, canale)

      _ <- pspcanale match {
        case Some(s) =>
          checkPspCanaleTipoVersamento(log, ddataMap, s._1, s._2, tipoVersOpt)
        case None =>
          Success(None)
      }

    } yield (pspOpt, intermediarioPsp, canaleOpt)
  }

  def checkIntermediarioPA(log: NodoLogger, ddataMap: ConfigData, idIntPa: String): Try[BrokerCreditorInstitution] = {

    ddataMap.creditorInstitutionBrokers.get(idIntPa) match {
      case Some(value) =>
        if (value.enabled) {
          log.debug(s"[$idIntPa] found and enabled")
          Success(value)
        } else {
          log.warn(s"[$idIntPa] found but disabled")
          Failure(DigitPaErrorCodes.PPT_INTERMEDIARIO_PA_DISABILITATO)
        }
      case None =>
        log.warn(s"[$idIntPa] not found")
        Failure(DigitPaErrorCodes.PPT_INTERMEDIARIO_PA_SCONOSCIUTO)
    }
  }

  def checkPaIntermediarioPaStazione(
      log: NodoLogger,
      ddataMap: ConfigData,
      idPa: String,
      idIntermediarioPa: String,
      idStazione: String,
      auxDigit: Option[Long] = None,
      password: Option[String] = None,
      checkPassword: Boolean = true
  ): Try[(CreditorInstitution, BrokerCreditorInstitution, Station)] = {
    for {
      pa <- checkPA(log, ddataMap, idPa)
      intermediarioPa <- checkIntermediarioPA(log, ddataMap, idIntermediarioPa)
      stazione <- checkStazione(log, ddataMap, idStazione, password, checkPassword)

      _ <-
        if (stazione.brokerCode == intermediarioPa.brokerCode) {
          Success(())
        } else {
          log.warn(s"[stazione:$idStazione,intermediarioPa:$idIntermediarioPa] wrong configuration intermediario-stazione")
          Failure(exception.DigitPaException("Configurazione intermediario-stazione non corretta", DigitPaErrorCodes.PPT_AUTORIZZAZIONE))
        }

      checkPaStazionePa = ddataMap.creditorInstitutionStations.exists(pastazionepa => {
        val cast = pastazionepa._2
        cast.creditorInstitutionCode == pa.creditorInstitutionCode &&
        cast.stationCode == stazione.stationCode &&
        auxDigit.forall(aux => cast.auxDigit.forall(_ == aux))
      })
      _ <-
        if (checkPaStazionePa) {
          Success(())
        } else {
          log.warn(s"[stazione:$idStazione,intermediarioPa:$idIntermediarioPa,pa:$idPa] wrong configuration pa-intermediario-stazione")
          Failure(exception.DigitPaException("Configurazione pa-intermediario-stazione non corretta", DigitPaErrorCodes.PPT_AUTORIZZAZIONE))
        }

    } yield (pa, intermediarioPa, stazione)

  }

  def checkIntermediarioPaStazionePassword(log: NodoLogger, ddataMap: ConfigData, idIntermediarioPa: String, idStazione: String, password: String, checkPassword: Boolean = true): Try[(BrokerCreditorInstitution, Station)] = {
    for {
      intermediarioPa <- checkIntermediarioPA(log, ddataMap, idIntermediarioPa)
      stazione <- checkStazione(log, ddataMap, idStazione, Some(password), checkPassword)

      _ <-
        if (stazione.brokerCode == intermediarioPa.brokerCode) {
          Success(())
        } else {
          log.warn(s"[stazione:$idStazione,intermediarioPa:$idIntermediarioPa] wrong configuration intermediario-stazione")
          Failure(exception.DigitPaException("Configurazione intermediario-stazione non corretta", DigitPaErrorCodes.PPT_AUTORIZZAZIONE))
        }

      checkPaStazionePa = ddataMap.creditorInstitutionStations.exists(pastazionepa => pastazionepa._2.stationCode == stazione.stationCode)
      _ <-
        if (checkPaStazionePa) {
          Success(())
        } else {
          log.warn(s"Stazione [$idStazione] not connected to any PA")
          Failure(exception.DigitPaException("Configurazione pa-intermediario-stazione non corretta", DigitPaErrorCodes.PPT_AUTORIZZAZIONE))
        }
    } yield (intermediarioPa, stazione)
  }

}
