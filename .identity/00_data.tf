data "azurerm_storage_account" "tf_storage_account"{
  name                = "pagopainfraterraform${var.env}"
  resource_group_name = "io-infra-rg"
}

data "azurerm_resource_group" "dashboards" {
  name = "dashboards"
}

data "azurerm_kubernetes_cluster" "aks" {
  name                = local.aks_cluster.name
  resource_group_name = local.aks_cluster.resource_group_name
}

data "github_organization_teams" "all" {
  root_teams_only = true
  summary_only    = true
}

data "azurerm_key_vault" "key_vault" {
  name                = "pagopa-${var.env_short}-kv"
  resource_group_name = "pagopa-${var.env_short}-sec-rg"
}

data "azurerm_key_vault" "domain_key_vault" {
  name                = "pagopa-${var.env_short}-${local.domain}-kv"
  resource_group_name = "pagopa-${var.env_short}-${local.domain}-sec-rg"
}

data "azurerm_resource_group" "apim_resource_group" {
  name = "${local.product}-api-rg"
}

data "azurerm_key_vault_secret" "key_vault_sonar" {
  name         = "sonar-token"
  key_vault_id = data.azurerm_key_vault.key_vault.id
}

data "azurerm_key_vault_secret" "key_vault_lightbend_key" {
  name         = "pagopa-lightbend-key"
  key_vault_id = data.azurerm_key_vault.domain_key_vault.id
}

data "azurerm_key_vault_secret" "key_vault_bot_cd_token" {
  name         = "pagopa-platform-domain-github-bot-cd-pat"
  key_vault_id = data.azurerm_key_vault.domain_key_vault.id
}

data "azurerm_key_vault_secret" "key_vault_slack_webhook_url" {
  name         = "slack-webhook-url"
  key_vault_id = data.azurerm_key_vault.domain_key_vault.id
}

data "azurerm_user_assigned_identity" "identity_cd" {
  name = "${local.product}-${local.domain}-01-github-cd-identity"
  resource_group_name = "${local.product}-identity-rg"
}