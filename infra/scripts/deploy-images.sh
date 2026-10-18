#!/usr/bin/env bash
# Points the App Service apps at new images and restarts only what changed. Used by CI and for manual deployments.
#
#   RG=aztek-app-rg REGISTRY=<acr>.azurecr.io WEB_APP=<web app> API_APP=<api app> \
#   WEB_IMAGE=weather-web:<tag> API_IMAGE=weather-api:<tag> REDIS_IMAGE=weather-redis:<tag> \
#   infra/scripts/deploy-images.sh
set -euo pipefail
# Git Bash on Windows would otherwise rewrite Azure resource IDs ("/subscriptions/...") into file paths.
export MSYS_NO_PATHCONV=1

: "${RG:?}" "${REGISTRY:?}" "${WEB_APP:?}" "${API_APP:?}" "${WEB_IMAGE:?}" "${API_IMAGE:?}" "${REDIS_IMAGE:?}"
api_version="2025-03-01"

# Web: classic single-container app, image in linuxFxVersion.
want="DOCKER|$REGISTRY/$WEB_IMAGE"
have="$(az webapp config show -g "$RG" -n "$WEB_APP" --query linuxFxVersion -o tsv)"
if [[ "$have" == "$want" ]]; then
  echo "web: already running $WEB_IMAGE"
else
  echo "web: $have -> $want"
  az webapp config set -g "$RG" -n "$WEB_APP" --linux-fx-version "$want" -o none
fi

# API: sidecar app. Read the container definition, change only the image and write it back, so the registry
# identity and environment settings stay exactly as Terraform created them.
api_id="$(az webapp show -g "$RG" -n "$API_APP" --query id -o tsv)"
for pair in "backend=$API_IMAGE" "redis=$REDIS_IMAGE"; do
  container="${pair%%=*}"
  image="$REGISTRY/${pair#*=}"
  url="https://management.azure.com${api_id}/sitecontainers/${container}?api-version=${api_version}"
  current="$(az rest --method get --url "$url" -o json)"
  have="$(printf '%s' "$current" | python3 -c 'import json,sys; print(json.load(sys.stdin)["properties"]["image"])')"
  if [[ "$have" == "$image" ]]; then
    echo "api/$container: already running ${pair#*=}"
    continue
  fi
  echo "api/$container: $have -> $image"
  body="$(printf '%s' "$current" | python3 -c '
import json, sys
current = json.load(sys.stdin)["properties"]
writable = ("image", "isMain", "targetPort", "startUpCommand", "authType", "userName", "passwordSecret",
            "userManagedIdentityClientId", "volumeMounts", "inheritAppSettingsAndConnectionStrings",
            "environmentVariables")
props = {k: v for k, v in current.items() if k in writable and v is not None}
props["image"] = sys.argv[1]
print(json.dumps({"properties": props}))' "$image")"
  az rest --method put --url "$url" --body "$body" -o none
done
