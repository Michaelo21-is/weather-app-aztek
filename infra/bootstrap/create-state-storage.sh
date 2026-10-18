#!/usr/bin/env bash
# One-time setup of the Terraform remote state in the existing terraform-state-rg resource group.
# Idempotent: re-running it changes nothing that already exists.
#
#   infra/bootstrap/create-state-storage.sh [storage-account-name]
#
# The account allows Entra ID (RBAC) access only: shared keys and public blob access are disabled, TLS 1.2 is required,
# and blob versioning plus 30-day soft delete keep earlier state versions recoverable.
set -euo pipefail
# Git Bash on Windows would otherwise rewrite Azure resource IDs ("/subscriptions/...") into file paths.
export MSYS_NO_PATHCONV=1

resource_group="terraform-state-rg"
container="tfstate"
subscription="$(az account show --query id -o tsv)"
location="$(az group show -n "$resource_group" --query location -o tsv)"
# Globally unique and stable for this subscription.
account="${1:-sttfstate$(printf '%s' "$subscription" | sha1sum | cut -c1-10)}"

if ! az storage account show -n "$account" -g "$resource_group" >/dev/null 2>&1; then
  az storage account create -n "$account" -g "$resource_group" -l "$location" \
    --sku Standard_LRS --kind StorageV2 --min-tls-version TLS1_2 \
    --allow-blob-public-access false --allow-shared-key-access false \
    --https-only true --tags application=weather-app purpose=terraform-state -o none
fi

az storage account blob-service-properties update -n "$account" -g "$resource_group" \
  --enable-versioning true \
  --enable-delete-retention true --delete-retention-days 30 \
  --enable-container-delete-retention true --container-delete-retention-days 30 -o none

# Data-plane access for the signed-in operator (Owner alone doesn't grant blob access).
me="$(az ad signed-in-user show --query id -o tsv)"
scope="$(az storage account show -n "$account" -g "$resource_group" --query id -o tsv)"
if [[ -z "$(az role assignment list --assignee "$me" --scope "$scope" --role "Storage Blob Data Contributor" --query '[0].id' -o tsv)" ]]; then
  az role assignment create --assignee-object-id "$me" --assignee-principal-type User \
    --role "Storage Blob Data Contributor" --scope "$scope" -o none
  echo "Waiting for the role assignment to propagate..."
  sleep 60
fi

az storage container create -n "$container" --account-name "$account" --auth-mode login -o none

cat <<EOF

Terraform state storage is ready. Create infra/terraform/backend.hcl:

resource_group_name  = "$resource_group"
storage_account_name = "$account"
container_name       = "$container"
key                  = "weather-prod.tfstate"
use_azuread_auth     = true
EOF
