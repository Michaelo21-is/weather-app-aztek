output "resource_group" {
  value = local.rg
}

output "web_app_name" {
  value = azapi_resource.web.name
}

output "api_app_name" {
  value = azapi_resource.api.name
}

output "web_default_hostname" {
  value = azapi_resource.web.output.properties.defaultHostName
}

output "api_default_hostname" {
  value = azapi_resource.api.output.properties.defaultHostName
}

output "acr_name" {
  value = azurerm_container_registry.main.name
}

output "acr_login_server" {
  value = azurerm_container_registry.main.login_server
}

output "key_vault_name" {
  value = azurerm_key_vault.main.name
}

output "postgres_fqdn" {
  value = azurerm_postgresql_flexible_server.main.fqdn
}

output "log_analytics_workspace_id" {
  value = azapi_resource.log.id
}

output "cloudflare_dns_records" {
  description = "Records to create in the Cloudflare zone."
  value = {
    apex = {
      type    = "CNAME (flattened at the apex)"
      name    = var.custom_domain
      content = azapi_resource.web.output.properties.defaultHostName
      proxied = true
    }
    verification = {
      type    = "TXT"
      name    = "asuid.${var.custom_domain}"
      content = azapi_resource.web.output.properties.customDomainVerificationId
      proxied = false
    }
  }
}

# Repository variables for .github/workflows/deploy.yml (identifiers, not secrets).
output "github_actions_variables" {
  value = {
    AZURE_CLIENT_ID       = azurerm_user_assigned_identity.github.client_id
    AZURE_TENANT_ID       = data.azurerm_client_config.current.tenant_id
    AZURE_SUBSCRIPTION_ID = data.azurerm_client_config.current.subscription_id
    AZURE_RESOURCE_GROUP  = local.rg
    ACR_NAME              = azurerm_container_registry.main.name
    WEB_APP_NAME          = azapi_resource.web.name
    API_APP_NAME          = azapi_resource.api.name
  }
}
