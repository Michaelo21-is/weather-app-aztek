# syntax=docker/dockerfile:1

# Redis for the backend's caches and login-attempt counters, run as an App Service sidecar next to the backend.
# The official image plus a start script, so the password comes from an environment variable
# (an App Service setting backed by Key Vault) instead of the container definition.
FROM redis:7.4-alpine

COPY start-redis.sh /usr/local/bin/start-redis.sh
RUN chmod 0755 /usr/local/bin/start-redis.sh

EXPOSE 6379
ENTRYPOINT ["start-redis.sh"]
