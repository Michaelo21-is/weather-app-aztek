variable "subscription_id" {
  description = "Azure subscription (Subscription for Exercise 2)."
  type        = string
}

variable "location" {
  description = "Azure region. North Europe: cheapest eligible EU region for this subscription (see docs/azure-deployment.md)."
  type        = string
  default     = "northeurope"
}

variable "resource_group_name" {
  description = "Existing resource group for the application. It is read, never created or deleted."
  type        = string
  default     = "aztek-app-rg"
}

variable "environment" {
  type    = string
  default = "prod"
}

variable "custom_domain" {
  type    = string
  default = "michael-site.com"
}

variable "custom_domain_enabled" {
  description = <<-EOT
    Bind custom_domain to the web app. Turn on only after the asuid TXT record exists in Cloudflare and the
    Cloudflare Origin CA certificate was imported into Key Vault (infra/scripts/import-origin-certificate.sh).
  EOT
  type        = bool
  default     = false
}

variable "origin_certificate_name" {
  description = "Key Vault certificate holding the Cloudflare Origin CA certificate for custom_domain."
  type        = string
  default     = "cloudflare-origin-michael-site-com"
}

variable "github_repository" {
  description = "owner/repo allowed to deploy through OpenID Connect (main branch only)."
  type        = string
  default     = "Michaelo21-is/weather-app-aztek"
}

variable "image_tag" {
  description = "Initial image tag. Later deployments come from CI, and Terraform ignores the running tag."
  type        = string
  default     = "latest"
}

variable "extra_allowed_ips" {
  description = "CIDRs allowed to reach the web app directly besides Cloudflare (e.g. an operator IP while testing)."
  type        = list(string)
  default     = []
}

variable "alert_email" {
  description = "Receives monitoring alerts and budget notifications. Empty disables both."
  type        = string
  default     = ""
}

variable "monthly_budget_usd" {
  description = "Budget for the resource group; notifications at 50%, 80% and 100% (actual) and 100% (forecast)."
  type        = number
  default     = 45
}

variable "postgres_geo_redundant_backup" {
  description = "Copy backups to the paired region (West Europe). Can only be chosen when the server is created."
  type        = bool
  default     = true
}

# ---------------------------------------------------------------------------------------------------------------
# Application configuration from backend/.env. Loaded by infra/scripts/tf.sh as TF_VAR_* environment variables.
# The secrets are ephemeral: they only flow into write-only attributes, so they never appear in a plan or the state.
# ---------------------------------------------------------------------------------------------------------------

variable "database_name" {
  description = "DATABASE_NAME"
  type        = string
  default     = "weatherdb"
}

variable "database_username" {
  description = "DATABASE_USERNAME (PostgreSQL administrator login, used by the backend)."
  type        = string
}

variable "database_password" {
  description = "DATABASE_PASSWORD"
  type        = string
  sensitive   = true
  ephemeral   = true
}

variable "jwt_secret" {
  description = "JWT_SECRET"
  type        = string
  sensitive   = true
  ephemeral   = true
}

variable "redis_password" {
  description = "REDIS_PASSWORD"
  type        = string
  sensitive   = true
  ephemeral   = true
}

variable "open_weather_api_key" {
  description = "OPEN_WEATHER_API"
  type        = string
  sensitive   = true
  ephemeral   = true
}

variable "secrets_version" {
  description = "Bump to push changed secret values (Key Vault secrets and the PostgreSQL password) on the next apply."
  type        = number
  default     = 1
}
