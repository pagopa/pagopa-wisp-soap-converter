locals {
  product = "${var.prefix}-${var.env_short}"
  project = "${var.prefix}-${var.env_short}-${var.location_short}-${var.domain}"

  apim = {
    name       = "${local.product}-apim"
    rg         = "${local.product}-api-rg"
    product_id = "wisp_soap_converter"
    hostname = "api.${var.apim_dns_zone_prefix}.${var.external_domain}"
  }
  wispsoapconverter_locals = {
    hostname = var.env == "prod" ? "weuprod.nodo.internal.platform.pagopa.it" : "weu${var.env}.nodo.internal.${var.env}.platform.pagopa.it"

    product_id            = "wisp_soap_converter"
    display_name          = "WISPSOAPCONVERTER"
    description           = "Wisp soap converter"
    subscription_required = true
    subscription_limit    = 1000

    rest_path        = "wisp-soap-converter/rest"
    soap_path        = "wisp-soap-converter/soap"
    service_url = null

    pagopa_tenant_id = data.azurerm_client_config.current.tenant_id
  }

}

