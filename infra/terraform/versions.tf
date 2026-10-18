terraform {
  # 1.11+ for ephemeral variables and write-only attributes (secrets never reach the plan or the state).
  required_version = ">= 1.11"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.81"
    }
    azapi = {
      source  = "azure/azapi"
      version = "~> 2.13"
    }
    azuread = {
      source  = "hashicorp/azuread"
      version = "~> 3.0"
    }
    http = {
      source  = "hashicorp/http"
      version = "~> 3.5"
    }
    time = {
      source  = "hashicorp/time"
      version = "~> 0.13"
    }
  }

  # Remote state in the terraform-state-rg storage account (Entra ID auth, no access keys).
  # Values come from backend.hcl; see infra/bootstrap/create-state-storage.sh.
  backend "azurerm" {}
}

provider "azurerm" {
  subscription_id     = var.subscription_id
  storage_use_azuread = true

  features {
    key_vault {
      # Purge protection is on; never try to purge, and recover a soft-deleted vault instead of failing.
      purge_soft_delete_on_destroy    = false
      recover_soft_deleted_key_vaults = true
    }
    resource_group {
      # aztek-app-rg is shared and not managed here, but keep the safety net on for any group Terraform touches.
      prevent_deletion_if_contains_resources = true
    }
  }
}

provider "azapi" {
  subscription_id = var.subscription_id
}
