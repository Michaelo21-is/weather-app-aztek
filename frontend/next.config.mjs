// Applied to every response, including the proxied /api responses. HSTS is set by Cloudflare at the edge.
const securityHeaders = [
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "X-Frame-Options", value: "DENY" },
  // The home page asks for the visitor's location; nothing else needs device APIs.
  { key: "Permissions-Policy", value: "geolocation=(self), camera=(), microphone=()" },
];

/** @type {import('next').NextConfig} */
const nextConfig = {
  /* config options here */
  // Self-contained server (server.js + traced node_modules) for the production Docker image.
  output: "standalone",
  poweredByHeader: false,
  cacheComponents: true,
  partialPrefetching: true,
  reactCompiler: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
};

export default nextConfig;
