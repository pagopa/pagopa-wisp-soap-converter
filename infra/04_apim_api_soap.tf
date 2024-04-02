resource "azurerm_api_management_api_version_set" "api_wispsoapconverter_api_soap" {
  name                = format("%s-wisp-soap-converter-soap-api", var.env_short)
  resource_group_name = local.apim.rg
  api_management_name = local.apim.name
  display_name        = "${local.wispsoapconverter_locals.display_name} - WSC soap"
  versioning_scheme   = "Segment"
}

resource "azurerm_api_management_api" "apim_node_for_psp_api_v1_dev" {
  count = var.env_short == "d" ? 1 : 0

  name                  = format("%s-wisp-soap-converter-soap-api", local.project)
  api_management_name   = local.apim.name
  resource_group_name   = local.apim.rg
  subscription_required = local.wispsoapconverter_locals.subscription_required
  version_set_id        = azurerm_api_management_api_version_set.api_wispsoapconverter_api_soap.id
  version               = "v1"
  service_url           = local.wispsoapconverter_locals.service_url
  revision              = "1"

  description  = local.wispsoapconverter_locals.description
  display_name = "${local.wispsoapconverter_locals.display_name} - soap"
  path         = local.wispsoapconverter_locals.soap_path
  protocols    = ["https"]

  import {
    content_format = "wsdl"
    content_value  = file("./api/wispsoapconverter/nodoPerPa/v1/NodoPerPa.wsdl")
    wsdl_selector {
      service_name  = "PagamentiTelematiciRPTservice"
      endpoint_name = "PagamentiTelematiciRPTPort"
    }
  }

}

resource "azurerm_api_management_api_policy" "apim_wisp_soap_converter_policy" {
  api_name            = format("%s-wisp-soap-converter-soap-api", local.project)
  api_management_name = local.apim.name
  resource_group_name = local.apim.rg
  xml_content = templatefile("./api/wispsoapconverter/nodoPerPa/v1/_base_policy.xml.tpl",{
    hostname = format("%s/%s/%s", local.wispsoapconverter_locals.hostname, "wisp-soap-converter","webservices/input")
  })
}