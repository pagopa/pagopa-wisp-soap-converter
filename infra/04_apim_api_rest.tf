resource "azurerm_api_management_api_version_set" "api_wispsoapconverter_api_rest" {
  name                = format("%s-wisp-soap-converter-rest-api", var.env_short)
  resource_group_name = local.apim.rg
  api_management_name = local.apim.name
  display_name        = "${local.wispsoapconverter_locals.display_name} - rest (INTERNAL)"
  versioning_scheme   = "Segment"
}

module "apim_api_wispsoapconverter_api_v1_rest" {
  source                = "git::https://github.com/pagopa/terraform-azurerm-v3.git//api_management_api?ref=v6.7.0"
  name                  = format("%s-wisp-soap-converter-rest-api", local.project)
  api_management_name   = local.apim.name
  resource_group_name   = local.apim.rg
  product_ids           = [local.apim.product_id]
  subscription_required = local.wispsoapconverter_locals.subscription_required

  version_set_id = azurerm_api_management_api_version_set.api_wispsoapconverter_api_rest.id
  api_version    = "v1"

  description  = local.wispsoapconverter_locals.description
  display_name = "${local.wispsoapconverter_locals.display_name} - rest (INTERNAL)"

  path        = local.wispsoapconverter_locals.rest_path
  protocols   = ["https"]
  service_url = local.wispsoapconverter_locals.service_url

  content_format = "openapi"
  content_value = templatefile("../openapi/openapi.json", {
    host    = local.apim.hostname
    service = "wsc"
  })

  xml_content = templatefile("./policy/_base_policy.xml", {
    hostname = format("%s/%s", local.wispsoapconverter_locals.hostname, "wisp-soap-converter")
  })
}