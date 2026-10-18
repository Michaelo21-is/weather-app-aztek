data "azurerm_client_config" "current" {}

data "azurerm_resource_group" "app" {
  name = var.resource_group_name
}

# Cloudflare's published edge ranges. Only these may reach the web app, so the site can't be reached around the
# CDN, and the CF-Connecting-IP header the backend trusts can only have been set by Cloudflare.
data "http" "cloudflare_ips" {
  url = "https://api.cloudflare.com/client/v4/ips"

  lifecycle {
    postcondition {
      condition     = self.status_code == 200 && jsondecode(self.response_body).success
      error_message = "Could not load the Cloudflare IP ranges."
    }
  }
}

locals {
  # Deterministic suffix for globally unique names (no random provider, so names survive a state loss).
  suffix   = substr(sha1("${data.azurerm_client_config.current.subscription_id}/${var.resource_group_name}"), 0, 6)
  region   = { northeurope = "neu", westeurope = "weu", swedencentral = "sec" }[var.location]
  name     = "weather-${var.environment}-${local.region}"
  location = var.location
  rg       = data.azurerm_resource_group.app.name

  # The tenant adds created_By/created_Date tags to every new resource; each tagged resource ignores those two keys
  # (lifecycle ignore_changes) instead of removing them on every apply.
  tags = {
    application = "weather-app"
    environment = var.environment
    managed-by  = "terraform"
    repository  = var.github_repository
  }

  cloudflare_cidrs = concat(
    jsondecode(data.http.cloudflare_ips.response_body).result.ipv4_cidrs,
    jsondecode(data.http.cloudflare_ips.response_body).result.ipv6_cidrs,
  )
}
