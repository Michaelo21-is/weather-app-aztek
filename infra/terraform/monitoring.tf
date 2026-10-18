# Logs: container console output and HTTP logs of both apps. azapi, because azurerm_log_analytics_workspace keeps the
# workspace shared keys in the state. The daily cap keeps ingestion inside the 5 GB/month free allowance.
resource "azapi_resource" "log" {
  type      = "Microsoft.OperationalInsights/workspaces@2023-09-01"
  name      = "log-${local.name}"
  parent_id = data.azurerm_resource_group.app.id
  location  = local.location
  tags      = local.tags

  body = {
    properties = {
      sku              = { name = "PerGB2018" }
      retentionInDays  = 30
      workspaceCapping = { dailyQuotaGb = 0.15 }
      features         = { disableLocalAuth = true }
    }
  }

  lifecycle {
    ignore_changes = [tags["created_By"], tags["created_Date"]]
  }
}

resource "azurerm_monitor_diagnostic_setting" "sites" {
  for_each                   = { web = azapi_resource.web.id, api = azapi_resource.api.id }
  name                       = "to-log-analytics"
  target_resource_id         = each.value
  log_analytics_workspace_id = azapi_resource.log.id

  enabled_log { category = "AppServiceConsoleLogs" }
  enabled_log { category = "AppServiceHTTPLogs" }
  enabled_log { category = "AppServicePlatformLogs" }
}

# ---------------------------------------------------------------------------------------------------------------
# Alerts and budget (only when alert_email is set). Metric alerts cost ~$0.10/month each; budgets are free.
# ---------------------------------------------------------------------------------------------------------------

locals {
  alerts_enabled = var.alert_email != ""

  metric_alerts = {
    web-5xx = {
      scope       = azapi_resource.web.id, namespace = "Microsoft.Web/sites", metric = "Http5xx"
      aggregation = "Total", operator = "GreaterThan", threshold = 10, window = "PT5M", severity = 2
      description = "Website returns server errors"
    }
    api-5xx = {
      scope       = azapi_resource.api.id, namespace = "Microsoft.Web/sites", metric = "Http5xx"
      aggregation = "Total", operator = "GreaterThan", threshold = 10, window = "PT5M", severity = 2
      description = "Backend returns server errors"
    }
    api-health = {
      scope       = azapi_resource.api.id, namespace = "Microsoft.Web/sites", metric = "HealthCheckStatus"
      aggregation = "Average", operator = "LessThan", threshold = 100, window = "PT15M", severity = 1
      description = "Backend readiness probe failing"
    }
    db-alive = {
      scope       = azurerm_postgresql_flexible_server.main.id, namespace = "Microsoft.DBforPostgreSQL/flexibleServers"
      metric      = "is_db_alive", aggregation = "Minimum", operator = "LessThan", threshold = 1, window = "PT5M", severity = 1
      description = "PostgreSQL is down"
    }
    db-storage = {
      scope       = azurerm_postgresql_flexible_server.main.id, namespace = "Microsoft.DBforPostgreSQL/flexibleServers"
      metric      = "storage_percent", aggregation = "Maximum", operator = "GreaterThan", threshold = 80, window = "PT30M", severity = 2
      description = "PostgreSQL storage above 80%"
    }
    db-cpu-credits = {
      scope       = azurerm_postgresql_flexible_server.main.id, namespace = "Microsoft.DBforPostgreSQL/flexibleServers"
      metric      = "cpu_credits_remaining", aggregation = "Minimum", operator = "LessThan", threshold = 30, window = "PT30M", severity = 3
      description = "Burstable PostgreSQL is running out of CPU credits (consider B2s)"
    }
  }
}

resource "azurerm_monitor_action_group" "ops" {
  count               = local.alerts_enabled ? 1 : 0
  name                = "ag-${local.name}"
  resource_group_name = local.rg
  short_name          = "weatherops"
  tags                = local.tags

  email_receiver {
    name                    = "operator"
    email_address           = var.alert_email
    use_common_alert_schema = true
  }

  lifecycle {
    ignore_changes = [tags["created_By"], tags["created_Date"]]
  }
}

resource "azurerm_monitor_metric_alert" "main" {
  for_each            = local.alerts_enabled ? local.metric_alerts : {}
  name                = "alert-${local.name}-${each.key}"
  resource_group_name = local.rg
  scopes              = [each.value.scope]
  description         = each.value.description
  severity            = each.value.severity
  window_size         = each.value.window
  frequency           = "PT5M"
  tags                = local.tags

  criteria {
    metric_namespace = each.value.namespace
    metric_name      = each.value.metric
    aggregation      = each.value.aggregation
    operator         = each.value.operator
    threshold        = each.value.threshold
  }

  action {
    action_group_id = azurerm_monitor_action_group.ops[0].id
  }

  lifecycle {
    ignore_changes = [tags["created_By"], tags["created_Date"]]
  }
}

resource "azurerm_consumption_budget_resource_group" "monthly" {
  count             = local.alerts_enabled ? 1 : 0
  name              = "budget-${local.name}"
  resource_group_id = data.azurerm_resource_group.app.id
  amount            = var.monthly_budget_usd
  time_grain        = "Monthly"

  time_period {
    start_date = "2026-10-01T00:00:00Z"
  }

  dynamic "notification" {
    for_each = [
      { threshold = 50, type = "Actual" },
      { threshold = 80, type = "Actual" },
      { threshold = 100, type = "Actual" },
      { threshold = 100, type = "Forecasted" },
    ]
    content {
      enabled        = true
      threshold      = notification.value.threshold
      threshold_type = notification.value.type
      operator       = "GreaterThanOrEqualTo"
      contact_emails = [var.alert_email]
    }
  }

  lifecycle {
    ignore_changes = [time_period]
  }
}
