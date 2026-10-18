# syntax=docker/dockerfile:1

# Node.js LTS. Next.js 16 needs >= 20.9; the Debian "slim" variant matches the native binaries
# (SWC, Lightning CSS, Tailwind oxide) that npm resolves from package-lock.json.
ARG NODE_VERSION=24-slim

# ---------- Dependencies ----------
FROM node:${NODE_VERSION} AS deps
WORKDIR /app
COPY package.json package-lock.json ./
# The cache mount keeps downloaded packages between builds.
RUN --mount=type=cache,target=/root/.npm \
    npm ci --no-audit --no-fund

# ---------- Build ----------
FROM node:${NODE_VERSION} AS build
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# NEXT_PUBLIC_* values are inlined into the browser bundle at build time, so they must never hold secrets.
# Production leaves the API base URL empty: the browser calls /api on its own origin and src/proxy.js
# forwards the request to the private backend.
ARG NEXT_PUBLIC_API_BASE_URL=""
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
RUN npm run build

# ---------- Runtime ----------
FROM node:${NODE_VERSION}
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0

# Only the traced standalone server, static assets and public files; no source, dev dependencies or .env files.
COPY --from=build --chown=node:node /app/public ./public
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static

# Security: run as the image's unprivileged "node" user (it owns .next for the prerender cache).
USER node
EXPOSE 3000

# BACKEND_URL (runtime) points the /api proxy at the backend, e.g. https://<backend-host>.
CMD ["node", "server.js"]
