#!/usr/bin/env bash
# Runs Terraform in infra/terraform with the application configuration from the backend .env file.
#
#   infra/scripts/tf.sh init -backend-config=backend.hcl
#   infra/scripts/tf.sh plan -out=tfplan
#   infra/scripts/tf.sh apply tfplan
#
# Secrets are exported as TF_VAR_* environment variables for this process only. They are declared ephemeral in
# Terraform and used only in write-only attributes, so they never reach a plan file, the state or the console.
# WEATHER_ENV_FILE overrides the location of the .env file (default: backend/.env).
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${WEATHER_ENV_FILE:-$repo_root/backend/.env}"
tf_dir="$repo_root/infra/terraform"

if [[ ! -f "$env_file" ]]; then
  echo "Missing $env_file (set WEATHER_ENV_FILE to the backend .env file)." >&2
  exit 1
fi

# Last KEY=value line wins; CR removed for files edited on Windows. Values are never printed.
env_value() {
  local value
  value="$(grep -E "^[[:space:]]*$1[[:space:]]*=" "$env_file" | tail -n 1 | cut -d= -f2- | tr -d '\r')"
  if [[ -z "$value" ]]; then
    echo "$1 is missing in $env_file" >&2
    exit 1
  fi
  printf '%s' "$value"
}

export TF_VAR_database_name="$(env_value DATABASE_NAME)"
export TF_VAR_database_username="$(env_value DATABASE_USERNAME)"
export TF_VAR_database_password="$(env_value DATABASE_PASSWORD)"
export TF_VAR_jwt_secret="$(env_value JWT_SECRET)"
export TF_VAR_redis_password="$(env_value REDIS_PASSWORD)"
export TF_VAR_open_weather_api_key="$(env_value OPEN_WEATHER_API)"

terraform_bin="${TERRAFORM:-terraform}"
exec "$terraform_bin" -chdir="$tf_dir" "$@"
