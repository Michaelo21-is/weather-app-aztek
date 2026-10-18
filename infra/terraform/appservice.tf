# One Linux B1 plan hosts both apps (billed per plan, not per app):
#   web: Next.js; public, but only Cloudflare's edge may connect; proxies /api/* to the api app.
#   api: Spring Boot (main container) + Redis (sidecar on localhost); only the web app's subnet may connect.
# The sites are azapi resources because azurerm_linux_web_app stores the site publishing credentials in the state.

resource "azurerm_service_plan" "main" {
  name                = "asp-${local.name}"
  location            = local.location
  resource_group_name = local.rg
  os_type             = "Linux"
  sku_name            = "B1"
  worker_count        = 1
  tags                = local.tags

  lifecycle {
    ignore_changes = [tags["created_By"], tags["created_Date"]]
  }
}

locals {
  registry = azurerm_container_registry.main.login_server

  # Settings shared by both sites.
  site_properties = {
    serverFarmId              = azurerm_service_plan.main.id
    httpsOnly                 = true
    clientAffinityEnabled     = false
    publicNetworkAccess       = "Enabled"
    virtualNetworkSubnetId    = azurerm_subnet.app.id
    keyVaultReferenceIdentity = azurerm_user_assigned_identity.apps.id
  }
  site_config = {
    alwaysOn                               = true
    http20Enabled                          = true
    minTlsVersion                          = "1.2"
    scmMinTlsVersion                       = "1.2"
    ftpsState                              = "Disabled"
    vnetRouteAllEnabled                    = true
    acrUseManagedIdentityCreds             = true
    acrUserManagedIdentityID               = azurerm_user_assigned_identity.apps.client_id
    ipSecurityRestrictionsDefaultAction    = "Deny"
    scmIpSecurityRestrictionsDefaultAction = "Deny"
    scmIpSecurityRestrictionsUseMain       = false
  }

  web_allowed = concat(
    [for i, cidr in local.cloudflare_cidrs : { name = "cloudflare-${i}", ipAddress = cidr }],
    [for i, cidr in var.extra_allowed_ips : { name = "operator-${i}", ipAddress = cidr }],
  )
}

resource "azapi_resource" "web" {
  type      = "Microsoft.Web/sites@2025-03-01"
  name      = "app-weather-web-${var.environment}-${local.suffix}"
  parent_id = data.azurerm_resource_group.app.id
  location  = local.location
  tags      = local.tags

  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.apps.id]
  }

  body = {
    kind = "app,linux,container" # classic single-container app (the sidecar api app reports "app,linux")
    properties = merge(local.site_properties, {
      siteConfig = merge(local.site_config, {
        linuxFxVersion  = "DOCKER|${local.registry}/weather-web:${var.image_tag}"
        healthCheckPath = "/healthz"
        ipSecurityRestrictions = [for i, rule in local.web_allowed : {
          name      = rule.name
          ipAddress = rule.ipAddress
          action    = "Allow"
          priority  = 100 + i
          tag       = "Default"
        }]
        appSettings = [
          # The private backend; src/proxy.js forwards /api/* there.
          { name = "BACKEND_URL", value = "https://${azapi_resource.api.output.properties.defaultHostName}" },
          { name = "WEBSITES_PORT", value = "3000" },
          { name = "WEBSITES_ENABLE_APP_SERVICE_STORAGE", value = "false" },
        ]
      })
    })
  }

  # CI deploys new image tags; Terraform must not roll them back.
  ignore_body_changes    = ["properties.siteConfig.linuxFxVersion"]
  ignore_casing          = true # Azure returns resource IDs such as serverFarmId in lower case
  response_export_values = ["properties.defaultHostName", "properties.customDomainVerificationId", "properties.inboundIpAddress"]

  depends_on = [azurerm_role_assignment.acr_pull_apps, azurerm_role_assignment.kv_apps]

  lifecycle {
    ignore_changes = [tags["created_By"], tags["created_Date"]]
  }
}

resource "azapi_resource" "api" {
  type      = "Microsoft.Web/sites@2025-03-01"
  name      = "app-weather-api-${var.environment}-${local.suffix}"
  parent_id = data.azurerm_resource_group.app.id
  location  = local.location
  tags      = local.tags

  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.apps.id]
  }

  body = {
    kind = "app,linux"
    properties = merge(local.site_properties, {
      siteConfig = merge(local.site_config, {
        # Sidecar mode: the containers are defined by the sitecontainers resources below.
        linuxFxVersion  = "SITECONTAINERS"
        healthCheckPath = "/actuator/health/readiness"
        ipSecurityRestrictions = [{
          name                 = "web-app-subnet"
          vnetSubnetResourceId = azurerm_subnet.app.id
          action               = "Allow"
          priority             = 100
          tag                  = "Default"
        }]
        appSettings = [
          { name = "DATABASE_HOST", value = azurerm_postgresql_flexible_server.main.fqdn },
          { name = "DATABASE_PORT", value = "5432" },
          { name = "DATABASE_NAME", value = var.database_name },
          { name = "DATABASE_USERNAME", value = var.database_username },
          { name = "DATABASE_PASSWORD", value = local.kv_ref["database-password"] },
          { name = "DATABASE_SSL_MODE", value = "require" },
          { name = "REDIS_HOST", value = "localhost" },
          { name = "REDIS_PORT", value = "6379" },
          { name = "REDIS_PASSWORD", value = local.kv_ref["redis-password"] },
          { name = "JWT_SECRET", value = local.kv_ref["jwt-secret"] },
          { name = "OPEN_WEATHER_API", value = local.kv_ref["openweather-api-key"] },
          { name = "WEBSITE_URL", value = "https://${var.custom_domain}" },
          # Client IP for the login limiter: set by Cloudflare, passed through by the Next.js proxy.
          { name = "FORWARD_HEADERS_STRATEGY", value = "native" },
          { name = "CLIENT_IP_HEADER", value = "CF-Connecting-IP" },
          # B1 has 1 vCPU and 1.75 GB for all containers: a fixed heap leaves room for Next.js and Redis, the C1-only JIT
          # and the serial collector roughly halve the CPU the JVM needs to start on a single core.
          { name = "JAVA_TOOL_OPTIONS", value = "-Xms128m -Xmx512m -XX:TieredStopAtLevel=1 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError" },
          { name = "WEBSITES_ENABLE_APP_SERVICE_STORAGE", value = "false" },
          # A cold start on the single B1 core can take minutes; the platform maximum avoids a kill-and-restart loop.
          { name = "WEBSITES_CONTAINER_START_TIME_LIMIT", value = "1800" },
        ]
      })
    })
  }

  ignore_casing          = true # Azure returns resource IDs such as serverFarmId in lower case
  response_export_values = ["properties.defaultHostName"]

  depends_on = [
    azurerm_role_assignment.acr_pull_apps,
    azurerm_role_assignment.kv_apps,
    azurerm_key_vault_secret.app,
  ]

  lifecycle {
    ignore_changes = [tags["created_By"], tags["created_Date"]]
  }
}

resource "azapi_resource" "api_backend" {
  type      = "Microsoft.Web/sites/sitecontainers@2025-03-01"
  name      = "backend"
  parent_id = azapi_resource.api.id

  body = {
    properties = {
      image                                  = "${local.registry}/weather-api:${var.image_tag}"
      isMain                                 = true
      targetPort                             = "8080"
      authType                               = "UserAssigned"
      userManagedIdentityClientId            = azurerm_user_assigned_identity.apps.client_id
      inheritAppSettingsAndConnectionStrings = true
    }
  }

  ignore_body_changes = ["properties.image"]
}

resource "azapi_resource" "api_redis" {
  type      = "Microsoft.Web/sites/sitecontainers@2025-03-01"
  name      = "redis"
  parent_id = azapi_resource.api.id

  body = {
    properties = {
      image                       = "${local.registry}/weather-redis:${var.image_tag}"
      isMain                      = false
      targetPort                  = "6379"
      authType                    = "UserAssigned"
      userManagedIdentityClientId = azurerm_user_assigned_identity.apps.client_id
      # Redis gets only its password (resolved from the REDIS_PASSWORD app setting), not the backend's secrets.
      inheritAppSettingsAndConnectionStrings = false
      environmentVariables                   = [{ name = "REDIS_PASSWORD", value = "REDIS_PASSWORD" }]
    }
  }

  ignore_body_changes = ["properties.image"]
}

# No FTP or SCM basic-auth publishing: deployments go through Azure RBAC (OIDC) only.
# Every site already has these two policy objects, so they are updated in place rather than created.
resource "azapi_update_resource" "basic_auth_off" {
  for_each = {
    web-ftp = { site = azapi_resource.web.id, policy = "ftp" }
    web-scm = { site = azapi_resource.web.id, policy = "scm" }
    api-ftp = { site = azapi_resource.api.id, policy = "ftp" }
    api-scm = { site = azapi_resource.api.id, policy = "scm" }
  }
  type      = "Microsoft.Web/sites/basicPublishingCredentialsPolicies@2025-03-01"
  name      = each.value.policy
  parent_id = each.value.site
  body      = { properties = { allow = false } }
}

# Container stdout/stderr in the log stream (short retention; Log Analytics keeps the history).
resource "azapi_update_resource" "site_logs" {
  for_each  = { web = azapi_resource.web.id, api = azapi_resource.api.id }
  type      = "Microsoft.Web/sites/config@2025-03-01"
  name      = "logs"
  parent_id = each.value

  body = {
    properties = {
      applicationLogs = { fileSystem = { level = "Information" } }
      httpLogs        = { fileSystem = { enabled = true, retentionInDays = 3, retentionInMb = 35 } }
    }
  }
}
