# Users, refresh tokens and favorite locations. Private access only (VNet-injected, no public endpoint), TLS required
# by the server default (require_secure_transport=on), 7-day point-in-time restore, backups copied to West Europe.
resource "azurerm_postgresql_flexible_server" "main" {
  name                          = "psql-${local.name}-${local.suffix}"
  location                      = local.location
  resource_group_name           = local.rg
  version                       = "17"
  sku_name                      = "B_Standard_B1ms"
  storage_mb                    = 32768
  storage_tier                  = "P4"
  auto_grow_enabled             = true
  backup_retention_days         = 7
  geo_redundant_backup_enabled  = var.postgres_geo_redundant_backup
  delegated_subnet_id           = azurerm_subnet.db.id
  private_dns_zone_id           = azurerm_private_dns_zone.postgres.id
  public_network_access_enabled = false
  tags                          = local.tags

  administrator_login               = var.database_username
  administrator_password_wo         = var.database_password
  administrator_password_wo_version = var.secrets_version

  authentication {
    password_auth_enabled         = true
    active_directory_auth_enabled = false
  }

  maintenance_window {
    day_of_week  = 0
    start_hour   = 3
    start_minute = 0
  }

  depends_on = [azurerm_private_dns_zone_virtual_network_link.postgres]

  lifecycle {
    # Holds user data: never destroyed by Terraform by accident.
    prevent_destroy = true
    ignore_changes  = [zone, tags["created_By"], tags["created_Date"]]
  }
}

resource "azurerm_postgresql_flexible_server_database" "app" {
  name      = var.database_name
  server_id = azurerm_postgresql_flexible_server.main.id
  charset   = "UTF8"
  collation = "en_US.utf8"

  lifecycle {
    prevent_destroy = true
  }
}
