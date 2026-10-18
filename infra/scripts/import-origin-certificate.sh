#!/usr/bin/env bash
# Issues a Cloudflare Origin CA certificate for the custom domain and imports it into Key Vault, where App Service
# picks it up (infra/terraform/domain.tf). The private key is generated locally and only ever leaves this machine
# inside the PFX uploaded to Key Vault; Cloudflare only receives the certificate signing request.
#
# Automated (API token with Zone > SSL and Certificates > Edit):
#   CLOUDFLARE_API_TOKEN=... infra/scripts/import-origin-certificate.sh auto <key-vault-name>
#
# Two steps (sign the CSR yourself: dashboard > SSL/TLS > Origin Server, or the Cloudflare API/MCP):
#   infra/scripts/import-origin-certificate.sh csr <work-dir>
#   infra/scripts/import-origin-certificate.sh import <work-dir> <certificate.pem> <key-vault-name>
#
# DOMAIN (default michael-site.com) and CERT_NAME (default cloudflare-origin-michael-site-com) can be overridden.
set -euo pipefail
# Git Bash on Windows would otherwise rewrite "/CN=..." and Azure resource IDs into file paths.
export MSYS_NO_PATHCONV=1
# A stale OPENSSL_CONF (some Windows installers set one) makes every openssl command fail.
if [[ -n "${OPENSSL_CONF:-}" && ! -f "$OPENSSL_CONF" ]]; then unset OPENSSL_CONF; fi

domain="${DOMAIN:-michael-site.com}"
cert_name="${CERT_NAME:-cloudflare-origin-${domain//./-}}"

make_csr() {
  local dir="$1"
  mkdir -p "$dir" && chmod 700 "$dir"
  # RSA 2048: supported everywhere App Service accepts certificates.
  openssl req -new -newkey rsa:2048 -nodes -sha256 \
    -keyout "$dir/origin.key" -out "$dir/origin.csr" \
    -subj "/CN=$domain" -addext "subjectAltName=DNS:$domain,DNS:*.$domain" 2>/dev/null
  chmod 600 "$dir/origin.key"
  echo "CSR: $dir/origin.csr"
}

import_cert() {
  local dir="$1" cert="$2" vault="$3"
  # Check that the certificate belongs to our key before uploading anything.
  if [[ "$(openssl x509 -noout -pubkey -in "$cert" | openssl sha256)" != "$(openssl pkey -pubout -in "$dir/origin.key" | openssl sha256)" ]]; then
    echo "The certificate does not match $dir/origin.key" >&2
    exit 1
  fi
  openssl x509 -noout -subject -issuer -enddate -in "$cert"
  # Empty export password: the PFX goes straight into Key Vault, which protects it.
  openssl pkcs12 -export -inkey "$dir/origin.key" -in "$cert" -out "$dir/origin.pfx" -passout pass:
  az keyvault certificate import --vault-name "$vault" --name "$cert_name" --file "$dir/origin.pfx" \
    --query "{name:name, expires:attributes.expires, thumbprint:x509ThumbprintHex}" -o table
  # Nothing private stays on disk.
  rm -f "$dir/origin.key" "$dir/origin.pfx" "$dir/origin.csr"
  echo "Imported as Key Vault certificate '$cert_name'. Local key material deleted."
}

case "${1:-}" in
  csr)
    make_csr "${2:?work dir}"
    ;;
  import)
    import_cert "${2:?work dir}" "${3:?certificate.pem}" "${4:?key vault name}"
    ;;
  auto)
    vault="${2:?key vault name}"
    : "${CLOUDFLARE_API_TOKEN:?CLOUDFLARE_API_TOKEN must be set}"
    dir="$(mktemp -d)"
    trap 'rm -rf "$dir"' EXIT
    make_csr "$dir"
    # 5475 days = 15 years, the Origin CA maximum.
    python3 -I -c 'import json,sys; print(json.dumps({"hostnames":[sys.argv[1],"*."+sys.argv[1]],"requested_validity":5475,"request_type":"origin-rsa","csr":open(sys.argv[2]).read()}))' \
      "$domain" "$dir/origin.csr" > "$dir/request.json"
    curl -fsS https://api.cloudflare.com/client/v4/certificates \
      -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" -H "Content-Type: application/json" \
      --data @"$dir/request.json" \
      | python3 -I -c 'import json,sys; d=json.load(sys.stdin); assert d["success"], d["errors"]; print(d["result"]["certificate"])' \
      > "$dir/origin.pem"
    import_cert "$dir" "$dir/origin.pem" "$vault"
    ;;
  *)
    sed -n '2,15p' "$0"
    exit 2
    ;;
esac
