resource "github_repository_environment" "github_repository_environment" {
  environment = var.env
  repository  = local.github.repository
  # filter teams reviewers from github_organization_teams
  # if reviewers_teams is null no reviewers will be configured for environment
  dynamic "reviewers" {
    for_each = (var.github_repository_environment.reviewers_teams == null || var.env_short != "p" ? [] : [1])
    content {
      teams = matchkeys(
        data.github_organization_teams.all.teams.*.id,
        data.github_organization_teams.all.teams.*.name,
        var.github_repository_environment.reviewers_teams
      )
    }
  }
  deployment_branch_policy {
    protected_branches     = var.github_repository_environment.protected_branches
    custom_branch_policies = var.github_repository_environment.custom_branch_policies
  }
}

locals {
  env_secrets = {
    "CD_CLIENT_ID" : data.azurerm_user_assigned_identity.identity_cd.client_id,
    "TENANT_ID" : data.azurerm_client_config.current.tenant_id,
    "SUBSCRIPTION_ID" : data.azurerm_subscription.current.subscription_id,
  }
  env_variables = {
    "CONTAINER_APP_ENVIRONMENT_NAME" : local.container_app_environment.name,
    "CONTAINER_APP_ENVIRONMENT_RESOURCE_GROUP_NAME" : local.container_app_environment.resource_group,
    "CLUSTER_NAME" : local.aks_cluster.name,
    "CLUSTER_RESOURCE_GROUP" : local.aks_cluster.resource_group_name,
    "NAMESPACE" : local.domain,
    "WORKLOAD_IDENTITY_ID" : data.azurerm_user_assigned_identity.workload_identity_clientid.client_id
  }
  repo_secrets = {
    "DEPLOY_SLACK_WEBHOOK_URL" : data.azurerm_key_vault_secret.key_vault_deploy_slack_webhook_url.value
  }
}

###############
# ENV Secrets #
###############

resource "github_actions_environment_secret" "github_environment_runner_secrets" {
  for_each        = local.env_secrets
  repository      = local.github.repository
  environment     = var.env
  secret_name     = each.key
  plaintext_value = each.value
}

#################
# ENV Variables #
#################

resource "github_actions_environment_variable" "github_environment_runner_variables" {
  for_each      = local.env_variables
  repository    = local.github.repository
  environment   = var.env
  variable_name = each.key
  value         = each.value
}

#############################
# Secrets of the Repository #
#############################

resource "github_actions_secret" "repo_secrets" {
  for_each        = local.repo_secrets
  repository      = local.github.repository
  secret_name     = each.key
  plaintext_value = each.value
}

#tfsec:ignore:github-actions-no-plain-text-action-secrets # not real secret
resource "github_actions_secret" "secret_sonar_token" {
  repository      = local.github.repository
  secret_name     = "SONAR_TOKEN"
  plaintext_value = data.azurerm_key_vault_secret.key_vault_sonar.value
}

resource "github_actions_secret" "lightbend_key" {
  repository      = local.github.repository
  secret_name     = "LIGHTBEND_KEY"
  plaintext_value = data.azurerm_key_vault_secret.key_vault_lightbend_key.value
}

#tfsec:ignore:github-actions-no-plain-text-action-secrets # not real secret
resource "github_actions_secret" "secret_bot_token" {

  repository      = local.github.repository
  secret_name     = "BOT_TOKEN_GITHUB"
  plaintext_value = data.azurerm_key_vault_secret.key_vault_bot_cd_token.value
}

############
## Labels ##
############

resource "github_issue_label" "patch" {
  repository = local.github.repository
  name       = "patch"
  color      = "FF0000"
}

resource "github_issue_label" "ignore_for_release" {
  repository = local.github.repository
  name       = "ignore-for-release"
  color      = "008000"
}

