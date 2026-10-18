# Application secrets live only in Key Vault. App Service resolves them at runtime through Key Vault references
# with the apps' managed identity; Terraform writes them through write-only attributes, so no value is stored in the
# Terraform state or shown in a plan.
resource "azurerm_key_vault" "main" {
  name                          = "kv-weather-${var.environment}-${local.suffix}"
  location                      = local.location
  resource_group_name           = local.rg
  tenant_id                     = data.azurerm_client_config.current.tenant_id
  sku_name                      = "standard"
  rbac_authorization_enabled    = true
  purge_protection_enabled      = true
  soft_delete_retention_days    = 7
  public_network_access_enabled = true # App Service resolves references over the public endpoint; access is RBAC-only.
  tags                          = local.tags

  network_acls {
    default_action = "Allow"
    bypass         = "AzureServices"
  }

  lifecycle {
    ignore_changes = [tags["created_By"], tags["created_Date"]]
  }
}

# The operator running Terraform writes secrets and imports the origin certificate.
resource "azurerm_role_assignment" "kv_operator" {
  for_each             = toset(["Key Vault Secrets Officer", "Key Vault Certificates Officer"])
  scope                = azurerm_key_vault.main.id
  role_definition_name = each.value
  principal_id         = data.azurerm_client_config.current.object_id
}

resource "azurerm_role_assignment" "kv_apps" {
  scope                = azurerm_key_vault.main.id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = azurerm_user_assigned_identity.apps.principal_id
  principal_type       = "ServicePrincipal"
}

# App Service imports TLS certificates from Key Vault with its own first-party identity ("Microsoft Azure App Service").
data "azuread_service_principal" "app_service" {
  client_id = "abfa0a7c-a6b6-4736-8310-5855508787cd"
}

resource "azurerm_role_assignment" "kv_app_service_rp" {
  for_each             = toset(["Key Vault Secrets User", "Key Vault Certificate User"])
  scope                = azurerm_key_vault.main.id
  role_definition_name = each.value
  principal_id         = data.azuread_service_principal.app_service.object_id
  principal_type       = "ServicePrincipal"
}

# Azure RBAC takes a moment to reach the Key Vault data plane.
resource "time_sleep" "kv_rbac" {
  create_duration = "60s"
  depends_on      = [azurerm_role_assignment.kv_operator]
}

locals {
  # Secret name in Key Vault => value from backend/.env (ephemeral: usable only in write-only attributes).
  secret_values = {
    "database-password"   = var.database_password
    "jwt-secret"          = var.jwt_secret
    "redis-password"      = var.redis_password
    "openweather-api-key" = var.open_weather_api_key
  }
  secret_names = ["database-password", "jwt-secret", "redis-password", "openweather-api-key"]

  # Versionless references: App Service picks up a new secret version within 24 hours (or on restart).
  kv_ref = { for name in local.secret_names : name => "@Microsoft.KeyVault(SecretUri=${azurerm_key_vault.main.vault_uri}secrets/${name}/)" }
}

resource "azurerm_key_vault_secret" "app" {
  for_each         = toset(local.secret_names)
  name             = each.key
  key_vault_id     = azurerm_key_vault.main.id
  value_wo         = local.secret_values[each.key]
  value_wo_version = var.secrets_version
  content_type     = "text/plain"
  tags             = local.tags

  depends_on = [time_sleep.kv_rbac]

  lifecycle {
    ignore_changes = [tags["created_By"], tags["created_Date"]]
  }
}
