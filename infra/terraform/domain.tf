# michael-site.com on the web app, with a Cloudflare Origin CA certificate (Cloudflare SSL mode "Full (strict)").
#
# Why not an App Service managed certificate: its issuance and renewal need the domain to resolve straight to Azure,
# which a proxied (orange-cloud) Cloudflare record never does, so renewals would fail silently. The Origin CA
# certificate is free, valid for 15 years, and only ever presented to Cloudflare; visitors see Cloudflare's own
# publicly trusted edge certificate, which Cloudflare renews automatically.
#
# Prerequisites (see docs/azure-deployment.md): TXT asuid.<domain> = custom_domain_verification_id in Cloudflare, and
# the certificate imported into Key Vault by infra/scripts/import-origin-certificate.sh.

resource "azurerm_app_service_certificate" "origin" {
  count               = var.custom_domain_enabled ? 1 : 0
  name                = "cloudflare-origin-${replace(var.custom_domain, ".", "-")}"
  resource_group_name = local.rg
  location            = local.location
  app_service_plan_id = azurerm_service_plan.main.id
  # Versionless: App Service syncs a renewed certificate from Key Vault automatically.
  key_vault_secret_id = "${azurerm_key_vault.main.vault_uri}secrets/${var.origin_certificate_name}"
  tags                = local.tags

  depends_on = [azurerm_role_assignment.kv_app_service_rp]

  lifecycle {
    ignore_changes = [tags["created_By"], tags["created_Date"]]
  }
}

resource "azurerm_app_service_custom_hostname_binding" "apex" {
  count               = var.custom_domain_enabled ? 1 : 0
  hostname            = var.custom_domain
  app_service_name    = azapi_resource.web.name
  resource_group_name = local.rg

  # Managed by the certificate binding below.
  lifecycle {
    ignore_changes = [ssl_state, thumbprint]
  }
}

resource "azurerm_app_service_certificate_binding" "apex" {
  count               = var.custom_domain_enabled ? 1 : 0
  hostname_binding_id = azurerm_app_service_custom_hostname_binding.apex[0].id
  certificate_id      = azurerm_app_service_certificate.origin[0].id
  ssl_state           = "SniEnabled"
}
