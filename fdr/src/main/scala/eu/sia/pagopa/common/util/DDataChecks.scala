package eu.sia.pagopa.common.util

import eu.sia.pagopa.Main.ConfigData
import eu.sia.pagopa.common.exception
import eu.sia.pagopa.common.exception.DigitPaErrorCodes
import it.pagopa.config._

import scala.util.{Failure, Success, Try}

object DDataChecks {

  val CHECK = "CHECK"

  def maxIdempotencyCacheDurationDays(ddataMap:ConfigData): Long = {
    val actko: Long = DDataChecks.getConfigurationKeys(ddataMap, "idempotency.activate.ko.duration.days").toLong
    val outcomeko: Long = DDataChecks.getConfigurationKeys(ddataMap, "idempotency.outcome.ko.duration.days").toLong
    val outcomeok: Long = DDataChecks.getConfigurationKeys(ddataMap, "idempotency.outcome.ok.duration.days").toLong

    Math.max(Math.max(actko,outcomeko),outcomeok)
  }

  def isBizEventEnabled(ddataMap: ConfigData): Boolean = {
    Try(DDataChecks.getConfigurationKeys(ddataMap, "sendBizEventsToEventHub").toBoolean).getOrElse(false)
  }

  def getStation(ddataMap: ConfigData, stationId: String): Station = {
    ddataMap.stations(stationId)
  }

  def getChannel(ddataMap: ConfigData, channelId: String): Channel = {
    ddataMap.channels(channelId)
  }

  def getConfigurationKeys(ddataMap: ConfigData, key: String, bundleName: String = "GLOBAL"): String = {
    ddataMap.configurations.get(s"$bundleName-$key").map(_.value).getOrElse(throw new RuntimeException(s"$bundleName-$key non presente"))
  }

  def checkPA(log: NodoLogger, ddataMap: ConfigData, idPa: String): Try[CreditorInstitution] = {
    ddataMap.creditorInstitutions.get(idPa) match {
      case Some(value) =>
        if (value.enabled) {
          log.debug(s"[$idPa] trovato e abilitato")
          Success(value)
        } else {
          log.warn(s"[$idPa] trovato e disabilitato")
          Failure(DigitPaErrorCodes.PPT_DOMINIO_DISABILITATO)
        }
      case None =>
        log.warn(s"[$idPa] non trovato")
        Failure(DigitPaErrorCodes.PPT_DOMINIO_SCONOSCIUTO)
    }
  }

  def checkPsp(log: NodoLogger, ddataMap: ConfigData, idPsp: String): Try[PaymentServiceProvider] = {
    ddataMap.psps.get(idPsp) match {
      case Some(value) =>
        if (value.enabled) {
          log.debug(s"[$idPsp] trovato e abilitato")
          Success(value)
        } else {
          log.warn(s"[$idPsp] trovato e disabilitato")
          Failure(DigitPaErrorCodes.PPT_PSP_DISABILITATO)
        }
      case None =>
        log.warn(s"[$idPsp] non trovato")
        Failure(DigitPaErrorCodes.PPT_PSP_SCONOSCIUTO)
    }
  }

  def checkIntermediarioPSP(log: NodoLogger, ddataMap: ConfigData, idIntPsp: String): Try[BrokerPsp] = {
    ddataMap.pspBrokers.get(idIntPsp) match {
      case Some(value) =>
        if (value.enabled) {
          log.debug(s"[$idIntPsp] trovato e abilitato")
          Success(value)
        } else {
          log.warn(s"[$idIntPsp] trovato e disabilitato")
          Failure(DigitPaErrorCodes.PPT_INTERMEDIARIO_PSP_DISABILITATO)
        }
      case None =>
        log.warn(s"[$idIntPsp] non trovato")
        Failure(DigitPaErrorCodes.PPT_INTERMEDIARIO_PSP_SCONOSCIUTO)
    }
  }

  def checkCodifiche(log: NodoLogger, ddataMap: ConfigData, formatoCodifica: String): Try[Encoding] = {
    ddataMap.encodings.get(formatoCodifica) match {
      case Some(value) =>
        log.debug(s"[$formatoCodifica] trovato")
        Success(value)
      case None =>
        log.warn(s"[$formatoCodifica] non trovato")
        Failure(DigitPaErrorCodes.PPT_CODIFICA_PSP_SCONOSCIUTA)
    }
  }

  def checkCanale(log: NodoLogger, ddataMap: ConfigData, idCanale: String, password: Option[String]): Try[Channel] = {
    ddataMap.channels.get(idCanale) match {
      case Some(value) =>
        if (value.enabled) {
          log.debug(s"[$idCanale] trovato e abilitato")
          if (password.forall(value.password.contains(_))) {
            Success(value)
          } else {
            log.warn(s"[$idCanale] trovato e abilitato, password errata")
            Failure(exception.DigitPaException("Password sconosciuta o errata", DigitPaErrorCodes.PPT_AUTENTICAZIONE))
          }
        } else {
          log.warn(s"[$idCanale] trovato e disabilitato")
          Failure(DigitPaErrorCodes.PPT_CANALE_DISABILITATO)
        }
      case None =>
        log.warn(s"[$idCanale] non trovato")
        Failure(DigitPaErrorCodes.PPT_CANALE_SCONOSCIUTO)
    }
  }

  def checkStazione(log: NodoLogger, ddataMap: ConfigData, idStazione: String, password: Option[String]): Try[Station] = {

    ddataMap.stations.get(idStazione) match {
      case Some(value) =>
        if (value.enabled) {
          log.debug(s"[$idStazione] trovata e abilitato")
          if (password.forall(value.password.contains(_))) {
            Success(value)
          } else {
            log.warn(s"[$idStazione] trovata e abilitato, password errata")
            Failure(exception.DigitPaException("Password sconosciuta o errata", DigitPaErrorCodes.PPT_AUTENTICAZIONE))
          }
        } else {
          log.warn(s"[$idStazione] trovata e disabilitato")
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
      log.warn(s"[psp:${psp.pspCode},canale:${canale.channelCode}] Configurazione psp-canale non corretta")
      Failure(exception.DigitPaException("Configurazione psp-canale non corretta", DigitPaErrorCodes.PPT_AUTORIZZAZIONE))
    } else {
      c.find(a => tvopt.forall(_.paymentType == a._2.paymentType)) match {
        case Some(value) =>
          Success(value._2)
        case None =>
          log.warn(s"[psp:${psp.pspCode},canale:${canale.channelCode},tipoVersamento:${tvopt.getOrElse("n.a")}] Configurazione psp-canale-tipoVersamento non corretta")
          Failure(exception.DigitPaException("Configurazione psp-canale-tipoVersamento non corretta", DigitPaErrorCodes.PPT_AUTORIZZAZIONE))
      }
    }

  }

  def checkDenylist(log: NodoLogger, ddataMap: ConfigData, idPsp: String, idCanale: String, idDominio: String) = {
    Success(())//TODO remove
  }

  def checkTipoVersamento(log: NodoLogger, ddataMap: ConfigData, tipoVersamento: String): Try[PaymentType] = {
    ddataMap.paymentTypes.get(tipoVersamento) match {
      case Some(value) => Success(value)
      case None =>
        log.warn(s"[$tipoVersamento] Tipo versamento sconosciuto")
        Failure(DigitPaErrorCodes.PPT_TIPO_VERSAMENTO_SCONOSCIUTO)
    }
  }

  def checkIban(log: NodoLogger, ddataMap: ConfigData, identPa: String, ibanAccredito: String): Try[Iban] = {
      val ibanPAkey = s"${identPa}-$ibanAccredito"
      ddataMap.ibans.get(ibanPAkey) match {
        case Some(value) =>
          log.debug(s"Coppia PA [${identPa}] IBAN [$ibanAccredito] trovata")
          Success(value)
        case None =>
          log.warn(s"Coppia PA [$identPa] IBAN [$ibanAccredito] non trovata")
          Failure(DigitPaErrorCodes.PPT_IBAN_NON_CENSITO)
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
      tipoVers: Option[String] = None
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
          checkCanale(log, ddataMap, idc, password).map(Some(_))
        case None =>
          Success(None)
      }
      _ <-
        if (canaleOpt.forall(can => can.brokerPspCode == intermediarioPsp.brokerPspCode)) {
          Success(())
        } else {
          log.warn(s"Id PA per [canale:${idCanale.getOrElse("n.a")},intermediarioPsp:$idIntermediarioPsp] Configurazione intermediario-canale non corretta")
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

  //noinspection ScalaStyle
  def checkPspCanale(
      log: NodoLogger,
      ddataMap: ConfigData,
      idPsp: Option[String] = None,
      idCanale: Option[String] = None,
      password: Option[String] = None,
      tipoVers: Option[String] = None
  ): Try[(Option[PaymentServiceProvider], Option[Channel])] = {

    for {
      pspOpt <- idPsp match {
        case Some(idpsp) =>
          checkPsp(log, ddataMap, idpsp).map(Some(_))
        case None =>
          Success(None)
      }
      canaleOpt <- idCanale match {
        case Some(idc) =>
          checkCanale(log, ddataMap, idc, password).map(Some(_))
        case None =>
          Success(None)
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
    } yield (pspOpt, canaleOpt)
  }

  def checkIntermediarioPA(log: NodoLogger, ddataMap: ConfigData, idIntPa: String): Try[BrokerCreditorInstitution] = {

    ddataMap.creditorInstitutionBrokers.get(idIntPa) match {
      case Some(value) =>
        if (value.enabled) {
          log.debug(s"[$idIntPa] trovato e abilitato")
          Success(value)
        } else {
          log.warn(s"[$idIntPa] trovato e disabilitato")
          Failure(DigitPaErrorCodes.PPT_INTERMEDIARIO_PA_DISABILITATO)
        }
      case None =>
        log.warn(s"[$idIntPa] non trovato")
        Failure(DigitPaErrorCodes.PPT_INTERMEDIARIO_PA_SCONOSCIUTO)
    }
  }

  //TODO vedere se deve tornare la PA per forza
  def checkCodifichePA(log: NodoLogger, ddataMap: ConfigData, codicePA: String, codificaPA: String): Try[CreditorInstitution] = {
    for {
      _ <- checkCodifiche(log, ddataMap, codificaPA)
      codificaPa <- ddataMap.creditorInstitutionEncodings.get(codicePA) match {
        case Some(value) =>
          Success(value)
        case None =>
          log.warn(s"CodificaPa [codicePa:$codicePA] non trovata")
          Failure(DigitPaErrorCodes.PPT_DOMINIO_SCONOSCIUTO)
        case _ =>
          log.warn(s"CodificaPa [codicePa:$codicePA] configurazione errata")
          Failure(DigitPaErrorCodes.PPT_SYSTEM_ERROR)
      }
      pa <- ddataMap.creditorInstitutions.find(pa => pa._2.creditorInstitutionCode == codificaPa.creditorInstitutionCode) match {
        case Some(pa) => Success(pa._2)
        case None =>
          log.warn(s"CodificaPa [codicePa:$codicePA] PA non trovata")
          Failure(DigitPaErrorCodes.PPT_DOMINIO_SCONOSCIUTO)
      }
    } yield pa
  }

  def checkPaIntermediarioPaStazione(
      log: NodoLogger,
      ddataMap: ConfigData,
      idPa: String,
      idIntermediarioPa: String,
      idStazione: String,
      auxDigit: Option[Long] = None,
      password: Option[String] = None
  ): Try[(CreditorInstitution, BrokerCreditorInstitution, Station)] = {
    for {
      pa <- checkPA(log, ddataMap, idPa)
      intermediarioPa <- checkIntermediarioPA(log, ddataMap, idIntermediarioPa)
      stazione <- checkStazione(log, ddataMap, idStazione, password)

      _ <-
        if (stazione.brokerCode == intermediarioPa.brokerCode) {
          Success(())
        } else {
          log.warn(s"[stazione:$idStazione,intermediarioPa:$idIntermediarioPa] Configurazione intermediario-stazione non corretta")
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
          log.warn(s"[stazione:$idStazione,intermediarioPa:$idIntermediarioPa,pa:$idPa] Configurazione pa-intermediario-stazione non corretta")
          Failure(exception.DigitPaException("Configurazione pa-intermediario-stazione non corretta", DigitPaErrorCodes.PPT_AUTORIZZAZIONE))
        }

    } yield (pa, intermediarioPa, stazione)

  }

  def checkPaIntermediarioPaStazioneMultibeneficiario(
      log: NodoLogger,
      ddataMap: ConfigData,
      idPa: String,
      idIntermediarioPa: String,
      idStazione: String,
      auxDigit: Option[Long] = None,
      password: Option[String] = None
  ): Try[(CreditorInstitution, BrokerCreditorInstitution, Station)] = {
    for {
      pa <- checkPA(log, ddataMap, idPa)
      intermediarioPa <- checkIntermediarioPA(log, ddataMap, idIntermediarioPa)
      stazione <- checkStazione(log, ddataMap, idStazione, password)

      //TODO check se fkpa e fkstazione in pastazionepa devono essere option
      checkPaStazionePa = ddataMap.creditorInstitutionStations.exists(pastazionepa => {
        val cast = pastazionepa._2
        cast.creditorInstitutionCode == pa.creditorInstitutionCode &&
        cast.stationCode == stazione.stationCode &&
        auxDigit.forall(aux => cast.auxDigit.forall(_ == aux))
      })

    } yield (pa, intermediarioPa, stazione)

  }

  def checkIntermediarioPaStazionePassword(log: NodoLogger, ddataMap: ConfigData, idIntermediarioPa: String, idStazione: String, password: String): Try[(BrokerCreditorInstitution, Station)] = {
    for {
      intermediarioPa <- checkIntermediarioPA(log, ddataMap, idIntermediarioPa)
      stazione <- checkStazione(log, ddataMap, idStazione, Some(password))

      _ <-
        if (stazione.brokerCode == intermediarioPa.brokerCode) {
          Success(())
        } else {
          log.warn(s"[stazione:$idStazione,intermediarioPa:$idIntermediarioPa] Configurazione intermediario-stazione non corretta")
          Failure(exception.DigitPaException("Configurazione intermediario-stazione non corretta", DigitPaErrorCodes.PPT_AUTORIZZAZIONE))
        }

      checkPaStazionePa = ddataMap.creditorInstitutionStations.exists(pastazionepa => pastazionepa._2.stationCode == stazione.stationCode)
      _ <-
        if (checkPaStazionePa) {
          Success(())
        } else {
          log.warn(s"Stazione [$idStazione] non collegata ad alcuna PA")
          Failure(exception.DigitPaException("Configurazione pa-intermediario-stazione non corretta", DigitPaErrorCodes.PPT_AUTORIZZAZIONE))
        }

    } yield (intermediarioPa, stazione)
  }

  def checkIntermediarioPaStazionePasswordMultibeneficiario(log: NodoLogger, ddataMap: ConfigData, idIntermediarioPa: String, idStazione: String, password: String): Try[(BrokerCreditorInstitution, Station)] = {
    for {
      intermediarioPa <- checkIntermediarioPA(log, ddataMap, idIntermediarioPa)
      stazione <- checkStazione(log, ddataMap, idStazione, Some(password))
    } yield (intermediarioPa, stazione)
  }

  //noinspection ScalaStyle
  def checkPaStazionePa(log: NodoLogger, ddataMap: ConfigData, idPa: String, noticeNumber: String): Try[(CreditorInstitution, Station, BrokerCreditorInstitution, String)] = {
    for {
      pa <- checkPA(log, ddataMap, idPa)
      (iuv, segregazione, progressivo, auxValue) = Util.getNoticeNumberData(noticeNumber)
      pastazionepa <- ddataMap.creditorInstitutionStations.find(pastazionepa => {
        pastazionepa._2.creditorInstitutionCode == pa.creditorInstitutionCode &&
        progressivo.forall(pastazionepa._2.applicationCode.contains(_)) &&
        segregazione.forall(pastazionepa._2.segregationCode.contains(_)) &&
        auxValue.forall(pastazionepa._2.auxDigit.contains(_))
      }) match {
        case Some(paspa) => Success(paspa)
        case None =>
          Failure(exception.DigitPaException("Configurazione pa-progressivo stazione non corretta", DigitPaErrorCodes.PPT_STAZIONE_INT_PA_SCONOSCIUTA))
      }
      stazione <- ddataMap.stations.find(s => pastazionepa._2.stationCode == s._2.stationCode) match {
        case Some((idStazione, value)) =>
          if (value.enabled) {
            log.debug(s"Stazione [$idStazione] trovata e abilitata")
            Success(value)
          } else {
            log.warn(s"Stazione [$idStazione] trovata e disabilitata")
            Failure(DigitPaErrorCodes.PPT_STAZIONE_INT_PA_DISABILITATA)
          }
        case None =>
          Failure(exception.DigitPaException("Configurazione pa-progressivo stazione non corretta", DigitPaErrorCodes.PPT_STAZIONE_INT_PA_SCONOSCIUTA))
      }
      intermediarioPa <- ddataMap.creditorInstitutionBrokers.find(ipa => ipa._2.brokerCode == stazione.brokerCode) match {
        case Some((idIntPa, value)) =>
          if (value.enabled) {
            log.debug(s"Intermediario PA [$idIntPa] trovato e abilitato")
            Success(value)
          } else {
            log.warn(s"Intermediario PA [$idIntPa] trovato e disabilitato")
            Failure(DigitPaErrorCodes.PPT_INTERMEDIARIO_PA_DISABILITATO)
          }
        case None =>
          Failure(exception.DigitPaException("Configurazione pa-progressivo stazione non corretta", DigitPaErrorCodes.PPT_STAZIONE_INT_PA_SCONOSCIUTA))
      }
    } yield (pa, stazione, intermediarioPa, iuv)

  }

  //noinspection ScalaStyle
  def checkPaStazionePa(log: NodoLogger, ddataMap: ConfigData, idPa: String, progressivo: Option[Int], segregazione: Option[Long], auxDigit: Option[Long]): Try[(CreditorInstitution, Station, BrokerCreditorInstitution)] = {
    for {
      pa <- checkPA(log, ddataMap, idPa)
      pastazionepa <- ddataMap.creditorInstitutionStations.find(pastazionepa => {
        pastazionepa._2.creditorInstitutionCode == idPa &&
        progressivo.forall(pastazionepa._2.applicationCode.contains(_)) &&
        segregazione.forall(pastazionepa._2.segregationCode.contains(_)) &&
        auxDigit.forall(pastazionepa._2.auxDigit.contains(_))
      }) match {
        case Some(paspa) => Success(paspa)
        case None =>
          Failure(exception.DigitPaException("Configurazione pa-progressivo stazione non corretta", DigitPaErrorCodes.PPT_STAZIONE_INT_PA_SCONOSCIUTA))
      }
      stazione <- ddataMap.stations.find(s => pastazionepa._2.stationCode == s._2.stationCode) match {
        case Some((idStazione, value)) =>
          if (value.enabled) {
            log.debug(s"Stazione [$idStazione] trovata e abilitata")
            Success(value)
          } else {
            log.warn(s"Stazione [$idStazione] trovata e disabilitata")
            Failure(DigitPaErrorCodes.PPT_STAZIONE_INT_PA_DISABILITATA)
          }
        case None =>
          Failure(exception.DigitPaException("Configurazione pa-progressivo stazione non corretta", DigitPaErrorCodes.PPT_STAZIONE_INT_PA_SCONOSCIUTA))
      }
      intermediarioPa <- ddataMap.creditorInstitutionBrokers.find(ipa => ipa._2.brokerCode == stazione.brokerCode) match {
        case Some((idIntPa, value)) =>
          if (value.enabled) {
            log.debug(s"Intermediario PA [$idIntPa] trovato e abilitato")
            Success(value)
          } else {
            log.warn(s"Intermediario PA [$idIntPa] trovato e disabilitato")
            Failure(DigitPaErrorCodes.PPT_INTERMEDIARIO_PA_DISABILITATO)
          }
        case None =>
          Failure(exception.DigitPaException("Configurazione pa-progressivo stazione non corretta", DigitPaErrorCodes.PPT_STAZIONE_INT_PA_SCONOSCIUTA))
      }
    } yield (pa, stazione, intermediarioPa)

  }

  def checkSegregazioneStazione(log: NodoLogger, ddataMap: ConfigData, pa: CreditorInstitution, stazione: Station, segregazione: Long): Try[StationCreditorInstitution] = {
    for {
      _ <- Success(())
      pastazionepa <- ddataMap.creditorInstitutionStations.find(pastazionepa => {
        pastazionepa._2.creditorInstitutionCode == pa.creditorInstitutionCode &&
        pastazionepa._2.stationCode == stazione.stationCode &&
        pastazionepa._2.segregationCode.contains(segregazione)
      }) match {
        case Some(paspa) => Success(paspa)
        case None =>
          Failure(exception.DigitPaException("Configurazione pa-stazione-segregazione stazione non corretta", DigitPaErrorCodes.PPT_STAZIONE_INT_PA_SCONOSCIUTA))
      }
    } yield pastazionepa._2

  }

  def checkCdsServizio(log: NodoLogger, ddataMap: ConfigData, idServizio: String, versione: Int): Try[CdsService] = {
    ddataMap.cdsServices.find(_._2.id == idServizio) match {
      case Some(value) =>
        if (value._2.version == versione) {
          Success(value._2)
        } else {
          log.warn(s"[$idServizio] trovato, versione errata")
          Failure(exception.DigitPaException("Versione servizio incompatibile con la chiamata", DigitPaErrorCodes.PPT_VERSIONE_SERVIZIO))
        }
      case None =>
        log.warn(s"[$idServizio] non trovato")
        Failure(exception.DigitPaException("Servizio inesistente sul sistema pagoPA", DigitPaErrorCodes.PPT_SERVIZIO_SCONOSCIUTO))
    }
  }

  def checkCdsSoggettoServizio(log: NodoLogger, ddataMap: ConfigData, idSoggettoServizio: String): Try[CdsSubjectService] = {
    ddataMap.cdsSubjectServices.find(_._2.subjectServiceId == idSoggettoServizio) match {
      case Some(value) =>
        Success(value._2)
      case None =>
        log.warn(s"[$idSoggettoServizio] non trovato")
        Failure(exception.DigitPaException("Soggetto Servizio inesistente sul sistema pagoPA", DigitPaErrorCodes.PPT_SERVIZIO_SCONOSCIUTO))
    }
  }

}
