#!/bin/sh
set -eu

: "${REDIS_PASSWORD:?REDIS_PASSWORD must be set}"

# App Service forwards the backend's localhost:6379 to this container's network interface, so a loopback-only bind
# is unreachable. Listening on all interfaces is safe: App Service only routes outside traffic to the main
# container's port, the app accepts requests only from the web app's subnet, and Redis requires the password.
BIND="${REDIS_BIND:-0.0.0.0}"

# The password goes into a file only the redis user can read, so it doesn't show up in the process list.
# Single quotes: Redis keeps backslashes literally there (inside double quotes "\x.." would be an escape sequence
# and silently change the password); only a single quote needs escaping.
CONF="$(mktemp)"
QUOTED="$(printf '%s' "$REDIS_PASSWORD" | sed "s/'/\\\\'/g")"
printf "requirepass '%s'\nbind %s\n" "$QUOTED" "$BIND" > "$CONF"
chown redis:redis "$CONF"

# Pure cache: no persistence, bounded memory, least-recently-used eviction. Every key the backend writes has a TTL.
# docker-entrypoint.sh drops root privileges, and exec makes redis-server PID 1 so it receives SIGTERM.
exec docker-entrypoint.sh redis-server "$CONF" \
    --protected-mode yes \
    --save '' \
    --appendonly no \
    --maxmemory "${REDIS_MAXMEMORY:-128mb}" \
    --maxmemory-policy allkeys-lru
