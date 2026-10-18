# Private network: App Service reaches PostgreSQL through VNet integration, and the database has no public endpoint.
# VNet, subnets, NSGs, delegation and service endpoints are free; only the private DNS zone is billed (~$0.50/month).

resource "azurerm_virtual_network" "main" {
  name                = "vnet-${local.name}"
  location            = local.location
  resource_group_name = local.rg
  address_space       = ["10.60.0.0/22"]
  tags                = local.tags

  lifecycle {
    ignore_changes = [tags["created_By"], tags["created_Date"]]
  }
}

# Outbound integration subnet for the App Service plan (one IP per instance; /26 covers scale-out and upgrades).
resource "azurerm_subnet" "app" {
  name                 = "snet-app"
  resource_group_name  = local.rg
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = ["10.60.0.0/26"]
  # Lets the backend's access restriction recognise calls from the frontend, which arrive from this subnet.
  service_endpoints = ["Microsoft.Web"]

  delegation {
    name = "app-service"
    service_delegation {
      name    = "Microsoft.Web/serverFarms"
      actions = ["Microsoft.Network/virtualNetworks/subnets/action"]
    }
  }
}

resource "azurerm_subnet" "db" {
  name                 = "snet-db"
  resource_group_name  = local.rg
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = ["10.60.1.0/28"]
  # Added by Azure when the server is placed in the subnet (backups and WAL archiving go to Azure Storage).
  service_endpoints = ["Microsoft.Storage"]

  delegation {
    name = "postgresql"
    service_delegation {
      name    = "Microsoft.DBforPostgreSQL/flexibleServers"
      actions = ["Microsoft.Network/virtualNetworks/subnets/join/action"]
    }
  }
}

resource "azurerm_network_security_group" "app" {
  name                = "nsg-${local.name}-app"
  location            = local.location
  resource_group_name = local.rg
  tags                = local.tags

  lifecycle {
    ignore_changes = [tags["created_By"], tags["created_Date"]]
  }
}

resource "azurerm_subnet_network_security_group_association" "app" {
  subnet_id                 = azurerm_subnet.app.id
  network_security_group_id = azurerm_network_security_group.app.id
}

# Only the App Service subnet may open PostgreSQL connections.
resource "azurerm_network_security_group" "db" {
  name                = "nsg-${local.name}-db"
  location            = local.location
  resource_group_name = local.rg
  tags                = local.tags

  security_rule {
    name                       = "allow-postgres-from-app-subnet"
    priority                   = 100
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "5432"
    source_address_prefix      = azurerm_subnet.app.address_prefixes[0]
    destination_address_prefix = azurerm_subnet.db.address_prefixes[0]
  }

  security_rule {
    name                       = "allow-within-db-subnet"
    priority                   = 110
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "*"
    source_port_range          = "*"
    destination_port_range     = "*"
    source_address_prefix      = azurerm_subnet.db.address_prefixes[0]
    destination_address_prefix = azurerm_subnet.db.address_prefixes[0]
  }

  security_rule {
    name                       = "deny-other-vnet-inbound"
    priority                   = 4000
    direction                  = "Inbound"
    access                     = "Deny"
    protocol                   = "*"
    source_port_range          = "*"
    destination_port_range     = "*"
    source_address_prefix      = "VirtualNetwork"
    destination_address_prefix = "*"
  }

  lifecycle {
    ignore_changes = [tags["created_By"], tags["created_Date"]]
  }
}

resource "azurerm_subnet_network_security_group_association" "db" {
  subnet_id                 = azurerm_subnet.db.id
  network_security_group_id = azurerm_network_security_group.db.id
}

# Resolves the server's name to its private address inside the VNet.
resource "azurerm_private_dns_zone" "postgres" {
  name                = "${local.name}.private.postgres.database.azure.com"
  resource_group_name = local.rg
  tags                = local.tags

  lifecycle {
    ignore_changes = [tags["created_By"], tags["created_Date"]]
  }
}

resource "azurerm_private_dns_zone_virtual_network_link" "postgres" {
  name                  = "vnet-${local.name}"
  resource_group_name   = local.rg
  private_dns_zone_name = azurerm_private_dns_zone.postgres.name
  virtual_network_id    = azurerm_virtual_network.main.id
  registration_enabled  = false
  tags                  = local.tags

  lifecycle {
    ignore_changes = [tags["created_By"], tags["created_Date"]]
  }
}
