# Runtime identity of both web apps: pulls images from ACR and resolves Key Vault references.
# No registry passwords or connection secrets exist anywhere.
resource "azurerm_user_assigned_identity" "apps" {
  name                = "id-${local.name}-apps"
  location            = local.location
  resource_group_name = local.rg
  tags                = local.tags

  lifecycle {
    ignore_changes = [tags["created_By"], tags["created_Date"]]
  }
}

# GitHub Actions signs in with OpenID Connect (short-lived tokens, no stored Azure credentials).
# Only workflows running on the main branch of the repository can use it.
resource "azurerm_user_assigned_identity" "github" {
  name                = "id-${local.name}-github"
  location            = local.location
  resource_group_name = local.rg
  tags                = local.tags

  lifecycle {
    ignore_changes = [tags["created_By"], tags["created_Date"]]
  }
}

resource "azurerm_federated_identity_credential" "github_main" {
  name                      = "github-main"
  user_assigned_identity_id = azurerm_user_assigned_identity.github.id
  audience                  = ["api://AzureADTokenExchange"]
  issuer                    = "https://token.actions.githubusercontent.com"
  subject                   = "repo:${var.github_repository}:ref:refs/heads/main"
}

resource "azurerm_role_assignment" "github_acr_push" {
  scope                = azurerm_container_registry.main.id
  role_definition_name = "AcrPush"
  principal_id         = azurerm_user_assigned_identity.github.principal_id
  principal_type       = "ServicePrincipal"
}

# Deployments only change the container images of the two apps.
resource "azurerm_role_assignment" "github_web_contributor" {
  for_each = {
    web = azapi_resource.web.id
    api = azapi_resource.api.id
  }
  scope                = each.value
  role_definition_name = "Website Contributor"
  principal_id         = azurerm_user_assigned_identity.github.principal_id
  principal_type       = "ServicePrincipal"
}
