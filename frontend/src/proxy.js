import { NextResponse } from "next/server";

// Same-origin API: the browser calls /api/* on this site and the Next.js server forwards the request to the
// backend, which is not reachable from the internet. Cookies, the CSRF header and Cloudflare's CF-Connecting-IP
// header pass through unchanged. BACKEND_URL is read at runtime, so one image works in every environment.
// Without it (next dev), /api requests fall through and the browser calls NEXT_PUBLIC_API_BASE_URL directly.
export function proxy(request) {
  const backendUrl = process.env.BACKEND_URL;
  if (!backendUrl) {
    return NextResponse.next();
  }
  const { pathname, search } = request.nextUrl;
  return NextResponse.rewrite(new URL(pathname + search, backendUrl));
}

export const config = {
  matcher: "/api/:path*",
};
