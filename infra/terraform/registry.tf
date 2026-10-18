# Private registry for the three images (web, api, redis). Admin user disabled: CI pushes with OIDC and the apps
# pull with their managed identity.
resource "azurerm_container_registry" "main" {
  name                = "acrweather${var.environment}${local.suffix}"
  location            = local.location
  resource_group_name = local.rg
  sku                 = "Basic"
  admin_enabled       = false
  tags                = local.tags

  lifecycle {
    ignore_changes = [tags["created_By"], tags["created_Date"]]
  }
}

resource "azurerm_role_assignment" "acr_pull_apps" {
  scope                = azurerm_container_registry.main.id
  role_definition_name = "AcrPull"
  principal_id         = azurerm_user_assigned_identity.apps.principal_id
  principal_type       = "ServicePrincipal"
}
