package eu.sia.pagopa.testutil

import eu.sia.pagopa.Main.ConfigData
import eu.sia.pagopa.common.util.Constant
import it.pagopa.config._

import java.time.OffsetDateTime

object TestDData {

  val baseCanale = Channel(
    TestItems.canale,
    Some(s"${TestItems.canale}_descr"),
    true,
    TestItems.canalePwd,
    Connection("HTTP","localhost",8087),
    TestItems.intPSP,
    None,//proxy
    Some(Service(Some("FespPsp"),Some("targetHost"),Some(8081),Some("targetPath"))),
    Some(Service(Some("servizioNMP"),Some("targetHostNMP"),Some(8082),Some("targetPathNMP"))),
    10,
    Timeouts(10,10,10),
    false,
    Redirect(Some("HTTP"),Some("redirectIp"),Some("redirectPath"),Some(8083),Some("redirectQS")),
    "ATTIVATO_PRESSO_PSP",
    Some("idPsp1"),
    true,
    false,
    true,
    true,
    true,
    2
  )
  val basePA = CreditorInstitution(TestItems.PA, true, Some("pa DEV ragsoc"), Some("pa DEV descr"), None, true, false, false)
  val basePSP = PaymentServiceProvider(TestItems.PSP, true, Some("CFpspTest"), Some("Test-PSP"), Some("ABIAA"), Some("bic"), Some("Test"), true, false, Some("tax"), Some("vat"))
  val baseStazione = Station(
    TestItems.stazione,
    true,
    2,
    Connection("HTTP","localhost",8087),
    Some(Connection("HTTP","localhost",8088)),
    TestItems.stazionePwd,
    Redirect(Some("HTTP"),Some("redirectIp"),Some("redirectPath"),Some(8083),Some("redirectQS")),
    Some(Service(Some("service"),Some("targetHost"),Some(8081),Some("targetPath"))),
    Some(Service(Some("servicePof"),Some("targetHostPof"),Some(8081),Some("targetPathPof"))),
    Some(Service(Some("serviceMod4"),None,None,None)),
    TestItems.testIntPA,
    None,
    10,
    Timeouts(10,10,10),
    true,
    1
  )

  val ddataMap: ConfigData = {
    ConfigDataV1(
      "version",
      Map(
        TestItems.PA -> basePA.copy(creditorInstitutionCode = TestItems.PA),
        "77777777777" -> basePA.copy(creditorInstitutionCode = "77777777777", true, description = Some("77777777777"), businessName = Some("77777777777")),
        TestItems.PA_FTP -> basePA.copy(creditorInstitutionCode = TestItems.PA_FTP, reportingFtp = true),
        TestItems.PA_old -> basePA.copy(creditorInstitutionCode = TestItems.PA_old),
        TestItems.PA_2 -> basePA.copy(creditorInstitutionCode = TestItems.PA_2)
      ),
      Map(TestItems.testIntPA -> BrokerCreditorInstitution(TestItems.testIntPA, true, Some("INTPAT"), false)),
      Map(
        TestItems.stazione -> baseStazione.copy(stationCode = TestItems.stazione, version = 2),
        TestItems.stazioneOld -> baseStazione.copy(stationCode = TestItems.stazioneOld, version = 1),
        TestItems.stazioneNonIstantanea -> baseStazione.copy(stationCode = TestItems.stazioneNonIstantanea, version = 2, invioRtIstantaneo = false),
        TestItems.stazionePV2 -> baseStazione.copy(stationCode = TestItems.stazionePV2, version = 2, invioRtIstantaneo = false, primitiveVersion = 2),
        TestItems.stazionePV2Broadcast -> baseStazione.copy(stationCode = TestItems.stazionePV2Broadcast, version = 2, invioRtIstantaneo = false, primitiveVersion = 2)
      ),
      Map(
        TestItems.stazione -> StationCreditorInstitution(TestItems.PA, TestItems.stazione, Some(1), None, Some(0), false, false, 1, true),
        TestItems.stazioneOld -> StationCreditorInstitution(TestItems.PA, TestItems.stazioneOld, Some(2), None, None, true, false, 1, false),
        "77777777777" -> StationCreditorInstitution("77777777777", TestItems.stazioneOld, Some(2), Some(1), Some(1), true, false, 1, false),
        TestItems.stazione + "ftp" -> StationCreditorInstitution(TestItems.PA_FTP, TestItems.stazioneOld, Some(2), Some(1), Some(1), true, false, 1, false),
        TestItems.paOldStazioneOld -> StationCreditorInstitution(TestItems.PA_old, TestItems.stazioneOld, Some(2), Some(1), None, true, false, 1, false),
        TestItems.stazioneNonIstantanea -> StationCreditorInstitution(TestItems.PA, TestItems.stazioneNonIstantanea, Some(2), Some(1), Some(1), false, false, 1, true),
        TestItems.stazionePV2 -> StationCreditorInstitution(TestItems.PA, TestItems.stazionePV2, Some(3), Some(1), Some(1), false, false, 2, true),
        TestItems.stazionePV2Broadcast -> StationCreditorInstitution(TestItems.PA, TestItems.stazionePV2Broadcast, Some(4), Some(1), Some(1), false, true, 2, true),
        TestItems.PA_2 + TestItems.stazioneNonIstantanea -> StationCreditorInstitution(TestItems.PA_2, TestItems.stazioneNonIstantanea, Some(5), Some(1), Some(1), false, true, 1, true)
      ),
      Map("QR-CODE" -> Encoding("QR-CODE", "QR-CODE"), "BARCODE-128-AIM" -> Encoding("BARCODE-128-AIM", "BARCODE-128-AIM")),
      Map(
        TestItems.PA -> CreditorInstitutionEncoding("QR-CODE", TestItems.PA + "0", TestItems.PA),
        TestItems.PA + "1" -> CreditorInstitutionEncoding("BARCODE-128-AIM", TestItems.PA + "1", TestItems.PA)
      ),
      Map(
        s"${TestItems.PA}-IT96R0123454321000000012345" -> Iban("IT96R0123454321000000012345", TestItems.PA, OffsetDateTime.now(), OffsetDateTime.now(), None, None, None, None),
        s"77777777777-IT96R0123454321000000012345" -> Iban("IT96R0123454321000000012345", "77777777777", OffsetDateTime.now(), OffsetDateTime.now(), None, None, None, None),
        s"${TestItems.PA}-IT96R0760154321123456789001" -> Iban("IT96R0760154321123456789001", TestItems.PA, OffsetDateTime.now(), OffsetDateTime.now(), None, None, None, None),
        s"${TestItems.PA_2}-IT96R0123454321000000012345" -> Iban("IT96R0123454321000000012345", TestItems.PA_2, OffsetDateTime.now(), OffsetDateTime.now(), None, None, None, None)
      ),
      Map(TestItems.PA -> CreditorInstitutionInformation("")),
      Map(
        TestItems.PSP -> basePSP.copy(pspCode = TestItems.PSP, taxCode = Some(TestItems.PSP)),
        "70000000001" -> basePSP.copy(pspCode = "70000000001", taxCode = Some("70000000001")),
        "idPsp1" -> basePSP.copy(pspCode = "idPsp1", taxCode = Some("idPsp1")),
        TestItems.PSPMod3New -> basePSP.copy(pspCode = TestItems.PSPMod3New, taxCode = Some(TestItems.PSPMod3New)),
        TestItems.PSPAgid -> basePSP.copy(pspCode = TestItems.PSPAgid, taxCode = Some(TestItems.PSPAgid)),
        TestItems.PSPECOMMERCE -> basePSP.copy(pspCode = TestItems.PSPECOMMERCE, taxCode = Some(TestItems.PSPECOMMERCE))
      ),
      Map(
        TestItems.intPSP -> BrokerPsp(TestItems.intPSP, Some("INTPSPT"), true, true),
        TestItems.intPSPMod3New -> BrokerPsp(TestItems.intPSPMod3New, Some(TestItems.intPSPMod3New), true, true),
        TestItems.intPSPAgid -> BrokerPsp(TestItems.intPSPAgid, Some(TestItems.intPSPAgid), true, true)
      ),
      Map(
        "AD" -> PaymentType("AD", Some("Addebito diretto")),
        "BBT" -> PaymentType("BBT", Some("Bonifico bancario telematico")),
        "BP" -> PaymentType("BP", Some("Bollettino postale")),
        "CP" -> PaymentType("CP", Some("Carta di pagamento")),
        "OBEP" -> PaymentType("OBEP", Some("Online Banking Electronic Payment")),
        "PO" -> PaymentType("PO", Some("Pagamento attivato presso PSP")),
        "JIF" -> PaymentType("JIF", Some("Jiffy")),
        "MYBK" -> PaymentType("MYBK", Some("MyBank")),
        "PPAL" -> PaymentType("PPAL", Some("PayPal")),
        "BPAY" -> PaymentType("BPAY", Some("Bancomat Pay")),
        "NODOPAY" -> PaymentType("NODOPAY", Some("Nodo Pay"))
      ),
      Map(
        "1, 1, 6, 1" -> PspChannelPaymentType(TestItems.PSP,TestItems.canale, "PO"),
        "1, 1, 4, 1" -> PspChannelPaymentType(TestItems.PSP,TestItems.canale, "CP"),
        "2, 2, 4, 1" -> PspChannelPaymentType(TestItems.PSP,TestItems.canaleImmediato, "CP"),
        "2, 2, 6, 1" -> PspChannelPaymentType(TestItems.PSP,TestItems.canaleImmediato, "PO"),
        "3, 5, 8, 1" -> PspChannelPaymentType(TestItems.PSP,TestItems.canaleDifferito, "MYBK"),
        "4, 5, 6, 1" -> PspChannelPaymentType(TestItems.PSP,TestItems.canaleDifferito, "PO"),
        "5, 3, 6, 4" -> PspChannelPaymentType(TestItems.PSPMod3New,TestItems.canaleAgid, "PO"),
        "6, 4, 6, 4" -> PspChannelPaymentType(TestItems.PSPMod3New,TestItems.canaleMod3new, "PO"),
        "7, 3, 6, 5" -> PspChannelPaymentType(TestItems.PSPAgid,TestItems.canaleAgid, "PO"),
        "7, 3, 4, 5" -> PspChannelPaymentType(TestItems.PSPAgid,TestItems.canaleAgid, "CP"),
        "8, 6, 9, 1" -> PspChannelPaymentType(TestItems.PSP,TestItems.canalePayPal, "PPAL"),
        "9, 7, 6, 1" -> PspChannelPaymentType(TestItems.PSP,TestItems.canalePull, "PO"),
        "10, 8, 6, 1" -> PspChannelPaymentType(TestItems.PSP,TestItems.canaleIrr, "PO"),
        "11, 9, 6, 5" -> PspChannelPaymentType(TestItems.PSPAgid,TestItems.canaleCD, "PO"),
        "12, 10, 10, 1" -> PspChannelPaymentType(TestItems.PSP,TestItems.canaleBPay, "BPAY"),
        "13, 11, 10, 1" -> PspChannelPaymentType(TestItems.PSP,TestItems.canaleBPayWrongModello, "BPAY"),
        "14, 12, 11, 1" -> PspChannelPaymentType(TestItems.PSP,TestItems.canaleNodoPay, "NODOPAY"),
        "15, 13, 11, 1" -> PspChannelPaymentType(TestItems.PSP,TestItems.canaleNodoPayWrongModello, "NODOPAY"),
        "16, 14, 11, 1" -> PspChannelPaymentType(TestItems.PSP,TestItems.canaleNodoPayV1, "NODOPAY"),
        "17, 1, 6, 6" -> PspChannelPaymentType(TestItems.PSPECOMMERCE,TestItems.canale, "PO"),
        "18, 12, 6, 6" -> PspChannelPaymentType(TestItems.PSPECOMMERCE,TestItems.canaleNodoPay, "NODOPAY")
      ),
      Map(
        "test2" -> Plugin("test2", None, None, None, None),
        "wpl02" -> Plugin("wpl02", None, None, None, None),
        "idPsp1" -> Plugin("idPsp1", None, None, None, None),
        "wpl04" -> Plugin("wpl04", None, None, None, None),
        "wpl06" -> Plugin("wpl06", None, None, None, None),
        "wpl05" -> Plugin("wpl05", None, None, None, None),
        "wpl03" -> Plugin("wpl03", None, None, None, None),
        "wpl07" -> Plugin("wpl07", None, None, None, None)
      ),
      Map(TestItems.PSP -> PspInformation("<xml></xml>")),
      Map(),
      Map(
        TestItems.canale -> baseCanale.copy(channelCode = TestItems.canale, paymentModel = "ATTIVATO_PRESSO_PSP"),
        TestItems.canaleImmediato -> baseCanale.copy(channelCode = TestItems.canaleImmediato, paymentModel = "IMMEDIATO"),
        TestItems.canaleAgid -> baseCanale.copy(channelCode = TestItems.canaleAgid, brokerPspCode = TestItems.intPSPAgid, paymentModel = "IMMEDIATO_MULTIBENEFICIARIO"),
        TestItems.canaleMod3new -> baseCanale.copy(channelCode = TestItems.canaleMod3new, brokerPspCode = TestItems.intPSPMod3New, paymentModel = "IMMEDIATO"),
        TestItems.canaleDifferito -> baseCanale.copy(channelCode = TestItems.canaleDifferito, paymentModel = "DIFFERITO"),
        TestItems.canalePayPal -> baseCanale.copy(channelCode = TestItems.canalePayPal, paymentModel = "IMMEDIATO"),
        TestItems.canalePull -> baseCanale.copy(channelCode = TestItems.canalePull, paymentModel = "IMMEDIATO"),
        TestItems.canaleIrr -> baseCanale.copy(channelCode = TestItems.canaleIrr, paymentModel = "IMMEDIATO", connection = baseCanale.connection.copy(ip="1.1.1.1")),
        TestItems.canaleCD -> baseCanale.copy(channelCode = TestItems.canaleCD, brokerPspCode = TestItems.intPSPCD, paymentModel = "ATTIVATO_PRESSO_PSP", connection = baseCanale.connection.copy(ip="1.1.1.1")),
        TestItems.canaleBPay -> baseCanale.copy(channelCode = TestItems.canaleBPay, paymentModel = "IMMEDIATO"),
        TestItems.canaleBPayWrongModello -> baseCanale.copy(channelCode = TestItems.canaleBPayWrongModello, paymentModel = "ATTIVATO_PRESSO_PSP"),
        TestItems.canaleNodoPay -> baseCanale.copy(channelCode = TestItems.canaleNodoPay, paymentModel = "IMMEDIATO", primitiveVersion = 2),
        TestItems.canaleNodoPayWrongModello -> baseCanale.copy(channelCode = TestItems.canaleNodoPayWrongModello, paymentModel = "ATTIVATO_PRESSO_PSP"),
        TestItems.canaleNodoPayV1 -> baseCanale.copy(channelCode = TestItems.canaleNodoPayV1, paymentModel = "IMMEDIATO", primitiveVersion = 1)
      ),
      Map(
        "1" -> CdsService("00001", "tassa auto v1", "TassaAutomobilistica_1_1_0.xsd", 1, "Tassa automobilistica"),
        "2" -> CdsService("00002", "servizio2 v2", "TassaAutomobilistica_1_1_0.xsd", 2, "Altro"),
        "3" -> CdsService("00003", "servizio3 v2", "TassaAutomobilistica_1_1_0.xsd", 1, "Altro")
      ),
      Map(TestItems.PA -> CdsSubject(TestItems.PA, s"${TestItems.PA}descr"), TestItems.PAUnknown -> CdsSubject(TestItems.PAUnknown, s"${TestItems.PAUnknown}descr")),
      Map(
        "1-1" -> CdsSubjectService(TestItems.PA, "00001", "00001", OffsetDateTime.now().minusDays(1), Some(OffsetDateTime.now().plusDays(1)), true,Some(TestItems.stazione),None),
        "1-2" -> CdsSubjectService(TestItems.PA, "00002", "00002", OffsetDateTime.now().minusDays(2), Some(OffsetDateTime.now().plusDays(2)), true,Some(TestItems.stazione),None),
        "1-3" -> CdsSubjectService(TestItems.PA, "00002", "00003", OffsetDateTime.now().minusDays(3), Some(OffsetDateTime.now().plusDays(3)), false,Some(TestItems.stazione),None),
        "1-4" -> CdsSubjectService(TestItems.PA, "00003", "00004", OffsetDateTime.now().minusDays(4), Some(OffsetDateTime.now().plusDays(4)), false,Some(TestItems.stazione),None),
        "1-5" -> CdsSubjectService(TestItems.PAUnknown, "00002", "00005", OffsetDateTime.now().minusDays(5), Some(OffsetDateTime.now().plusDays(5)), false,Some(TestItems.stazione),None),
        "1-6" -> CdsSubjectService(TestItems.PAUnknown, "00001", "00006", OffsetDateTime.now().minusDays(6), Some(OffsetDateTime.now().plusDays(6)), false,Some(TestItems.stazione),None)
      ),
      Map("Altro" -> CdsCategory("Altro"), "Tassa automobilistica" -> CdsCategory("Tassa automobilistica"), "Donazioni" -> CdsCategory("Donazioni")),
      Map(
        "GLOBAL-azureSdkClientReEventEnabled" -> ConfigurationKey("", "", "false", None),
        "GLOBAL-ccpRandomTraduttore" -> ConfigurationKey("", "", "true", None),
        "GLOBAL-ftp.env.path" -> ConfigurationKey("", "", "pate_", None),
        "GLOBAL-idCanaleAGID" -> ConfigurationKey("", "", TestItems.canaleAgid, None),
        "GLOBAL-idCanaleCD" -> ConfigurationKey("", "", TestItems.canaleCD, None),
        "GLOBAL-idCanaleMod3New" -> ConfigurationKey("", "", TestItems.canaleMod3new, None),
        "GLOBAL-idIntPspMod3New" -> ConfigurationKey("", "", TestItems.intPSPMod3New, None),
        "GLOBAL-idPlugin.mybank" -> ConfigurationKey("", "", "wpl04", None),
        "GLOBAL-idPspAGID" -> ConfigurationKey("", "", TestItems.PSPAgid, None),
        "GLOBAL-idPspCD" -> ConfigurationKey("", "", TestItems.PSPCD, None),
        "GLOBAL-idPspMod3New" -> ConfigurationKey("", "", TestItems.PSPMod3New, None),
        "GLOBAL-id_psp_poste" -> ConfigurationKey("", "", "POSTE", None),
        "GLOBAL-intPspAGID" -> ConfigurationKey("", "", TestItems.intPSPAgid, None),
        "GLOBAL-intPspCD" -> ConfigurationKey("", "", TestItems.intPSPCD, None),
        "GLOBAL-istitutoAttestante.capAttestante" -> ConfigurationKey("", "", "00144", None),
        "GLOBAL-istitutoAttestante.civicoAttestante" -> ConfigurationKey("", "", "21", None),
        "GLOBAL-istitutoAttestante.codiceUnitOperAttestante" -> ConfigurationKey("", "", "n/a", None),
        "GLOBAL-istitutoAttestante.denominazioneAttestante" -> ConfigurationKey("", "", "Agenzia per l'Italia Digitale", None),
        "GLOBAL-istitutoAttestante.denomUnitOperAttestante" -> ConfigurationKey("", "", "n/a", None),
        "GLOBAL-istitutoAttestante.identificativoUnivocoAttestante.codiceIdentificativoUnivoco" -> ConfigurationKey("", "", "97735020584", None),
        "GLOBAL-istitutoAttestante.identificativoUnivocoAttestante.tipoIdentificativoUnivoco" -> ConfigurationKey("", "", "G", None),
        "GLOBAL-istitutoAttestante.indirizzoAttestante" -> ConfigurationKey("", "", "Via Liszt", None),
        "GLOBAL-istitutoAttestante.localitaAttestante" -> ConfigurationKey("", "", "Roma", None),
        "GLOBAL-istitutoAttestante.nazioneAttestante" -> ConfigurationKey("", "", "IT", None),
        "GLOBAL-istitutoAttestante.provinciaAttestante" -> ConfigurationKey("", "", "RM", None),
        "GLOBAL-scheduler.ftpUploadRetryPollerMaxRetry" -> ConfigurationKey("", "", "5", None),
        "GLOBAL-scheduler.jobName_ftpUpload.enabled" -> ConfigurationKey("", "", "true", None),
        "GLOBAL-scheduler.jobName_ftpUpload.jobDescription" -> ConfigurationKey("", "", "Description", None),
        "GLOBAL-validate_input" -> ConfigurationKey("", "", "true", None),
        "GLOBAL-validate_output" -> ConfigurationKey("", "", "true", None),
        "rendicontazioni-chiediElencoFlussiRendicontazioneDayLimit" -> ConfigurationKey("", "", "30", None),
        "rendicontazioni-ftp.internalFTPComponentEndpointHost" -> ConfigurationKey("", "", "0.0.0.0", None),
        "rendicontazioni-ftp.internalFTPComponentEndpointPort" -> ConfigurationKey("", "", "9610", None)
      ),
      Map("1" -> FtpServer("localhost", 8899, true, "", "", "/", Constant.KeyName.RENDICONTAZIONI, Constant.KeyName.RENDICONTAZIONI, "", "", "", 1)),
      Map(),
      Map(),
      Map(
        "m1" -> MetadataDict("m1", Some("1"), OffsetDateTime.now().minusDays(1), Some(OffsetDateTime.now().plusDays(1))),
        "m2" -> MetadataDict("m2", Some("2"), OffsetDateTime.now().minusDays(1), Some(OffsetDateTime.now().plusDays(1))),
        "m3" -> MetadataDict("m3", Some("3"), OffsetDateTime.now().minusDays(1), Some(OffsetDateTime.now().plusDays(1))),
        "METADATA1" -> MetadataDict("METADATA1", Some("3"), OffsetDateTime.now().minusDays(1), Some(OffsetDateTime.now().plusDays(1))),
        "METADATA2" -> MetadataDict("METADATA2", Some("3"), OffsetDateTime.now().minusDays(1), Some(OffsetDateTime.now().plusDays(1)))
      )
    )
  }

}
