#!/usr/bin/env python3
"""End-to-end smoke test through the public entry point, i.e. the same path a browser takes.

    smoke_test.py BASE_URL                 read-only checks: pages, health, CSRF, public weather API, cache headers
    smoke_test.py BASE_URL --full          also signs up a throwaway user (smoke-<timestamp>@example.com), signs in,
                                           saves/lists/removes a favorite, refreshes the session and signs out
    smoke_test.py BASE_URL --full --spoof-client-ip
                                           also checks that the login limiter keys on the client IP header
                                           (only meaningful without Cloudflare in front, which overwrites it)

Only the Python standard library is used. Secure cookies are sent over plain HTTP too, so local tests work.
"""
import argparse
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from http.cookies import SimpleCookie

FAILURES = []


def check(condition, message):
    print(("  ok   " if condition else "  FAIL ") + message)
    if not condition:
        FAILURES.append(message)
    return condition


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


class Client:
    """Minimal browser stand-in: keeps cookies per path and sends the CSRF header on state-changing requests."""

    def __init__(self, base_url, origin, extra_headers=None):
        self.base = base_url.rstrip("/")
        self.origin = origin
        self.cookies = {}  # name -> (value, path)
        self.csrf = None
        self.extra_headers = extra_headers or {}
        self.opener = urllib.request.build_opener(NoRedirect)

    def request(self, method, path, body=None, headers=None):
        url = self.base + path
        request_path = urllib.parse.urlsplit(url).path
        hdrs = {"Accept": "application/json, text/html", "User-Agent": "weather-smoke-test"}
        hdrs.update(self.extra_headers)
        hdrs.update(headers or {})
        jar = [f"{k}={v}" for k, (v, p) in self.cookies.items() if request_path.startswith(p) and v]
        if jar:
            hdrs["Cookie"] = "; ".join(jar)
        data = None
        if body is not None:
            data = json.dumps(body).encode()
            hdrs["Content-Type"] = "application/json"
        if method in ("POST", "PUT", "PATCH", "DELETE"):
            hdrs["Origin"] = self.origin
            if self.csrf:
                hdrs[self.csrf[0]] = self.csrf[1]
        req = urllib.request.Request(url, data=data, method=method, headers=hdrs)
        try:
            resp = self.opener.open(req, timeout=60)
            status, resp_headers, payload = resp.status, resp.headers, resp.read()
        except urllib.error.HTTPError as e:
            status, resp_headers, payload = e.code, e.headers, e.read()
        for raw in resp_headers.get_all("Set-Cookie") or []:
            parsed = SimpleCookie()
            parsed.load(raw)
            for name, morsel in parsed.items():
                self.cookies[name] = (morsel.value, morsel["path"] or "/")
        return status, resp_headers, payload

    def json(self, method, path, body=None, headers=None):
        status, hdrs, payload = self.request(method, path, body, headers)
        # Like the frontend (src/app/api/api.js): Spring Security replaces the CSRF cookie after the session
        # changes, so a 403 on a state-changing request reloads the token and retries once.
        if status == 403 and self.csrf and method in ("POST", "PUT", "PATCH", "DELETE"):
            self.load_csrf()
            status, hdrs, payload = self.request(method, path, body, headers)
        try:
            return status, hdrs, json.loads(payload) if payload else None
        except ValueError:
            return status, hdrs, payload[:200]

    def load_csrf(self):
        status, _, data = self.json("GET", "/api/csrf")
        if status == 200 and isinstance(data, dict):
            self.csrf = (data["headerName"], data["token"])
        return status, data


def read_only_checks(base, origin):
    print("Public pages and headers")
    c = Client(base, origin)
    status, hdrs, body = c.request("GET", "/")
    check(status == 200 and b"<html" in body.lower(), f"GET / -> {status}")
    check("x-powered-by" not in {k.lower() for k in hdrs.keys()}, "no X-Powered-By header")
    check(hdrs.get("X-Content-Type-Options") == "nosniff", "X-Content-Type-Options: nosniff")
    check(hdrs.get("X-Frame-Options") == "DENY", "X-Frame-Options: DENY")
    status, _, data = c.json("GET", "/healthz")
    check(status == 200 and isinstance(data, dict) and data.get("status") == "UP", f"GET /healthz -> {status} {data}")
    status, _, _ = c.request("GET", "/sign-in")
    check(status == 200, f"GET /sign-in -> {status}")

    static_paths = sorted({p.split('"')[0] for p in body.decode(errors="ignore").split('src="')[1:]
                           if p.startswith("/_next/static/")})
    if check(bool(static_paths), "home page references /_next/static assets"):
        status, hdrs, _ = c.request("GET", static_paths[0])
        check(status == 200 and "immutable" in (hdrs.get("Cache-Control") or ""),
              f"static asset cacheable: {hdrs.get('Cache-Control')}")

    print("API through the same-origin proxy")
    status, data = c.load_csrf()
    check(status == 200 and c.csrf is not None, f"GET /api/csrf -> {status}")
    check("XSRF-TOKEN" in c.cookies and "weather_session" in c.cookies, "XSRF-TOKEN and weather_session cookies set")
    status, hdrs, data = c.json("GET", "/api/weather/city-suggestion?query=Lond")
    check(status == 200 and isinstance(data, list) and any(s.get("name") == "London" for s in data),
          f"city suggestions for 'Lond' -> {status} {data if status != 200 else len(data)}")
    cache_control = (hdrs.get("Cache-Control") or "").lower()
    check("no-store" in cache_control or "private" in cache_control, f"API response not cacheable: {cache_control}")
    status, _, data = c.json("GET", "/api/weather/city?cityName=London&countryName=GB")
    check(status == 200 and isinstance(data, dict) and data.get("location", {}).get("country") == "GB",
          f"weather for London,GB -> {status}")
    status, _, data = c.json("GET", "/api/weather/coordinates?lat=53.35&lon=-6.26")
    check(status == 200 and isinstance(data, dict) and "location" in data, f"weather for coordinates -> {status}")
    status, _, _ = c.json("GET", "/api/user/get-nickname")
    check(status == 401, f"protected endpoint without session -> {status} (expected 401)")
    status_no_csrf, _, _ = Client(base, origin).json("POST", "/api/auth/sign-in", {"email": "a@b.c", "password": "x"})
    check(status_no_csrf == 403, f"state-changing request without CSRF token -> {status_no_csrf} (expected 403)")
    status, _, _ = c.request("GET", "/actuator/health")
    check(status == 404, f"backend actuator not exposed publicly -> {status} (expected 404)")


def full_flow(base, origin):
    print("Account, favorites and session lifecycle")
    c = Client(base, origin)
    c.load_csrf()
    email = f"smoke-{int(time.time())}@example.com"
    password = "Smoke-test-" + str(int(time.time()))
    status, _, data = c.json("POST", "/api/auth/sign-up", {"email": email, "password": password, "nickname": "Smoke"})
    check(status == 200, f"sign-up {email} -> {status} {data or ''}")
    status, _, _ = c.json("POST", "/api/auth/sign-up", {"email": email, "password": password, "nickname": "Smoke"})
    check(status == 409, f"duplicate sign-up -> {status} (expected 409)")
    status, hdrs, _ = c.json("POST", "/api/auth/sign-in", {"email": email, "password": password})
    check(status == 200, f"sign-in -> {status}")
    set_cookies = " | ".join(hdrs.get_all("Set-Cookie") or [])
    check("access_token=" in set_cookies and "refresh_token=" in set_cookies, "access and refresh cookies issued")
    for attr in ("HttpOnly", "Secure", "SameSite=Lax"):
        check(set_cookies.count(attr) >= 2, f"auth cookies are {attr}")
    check("Path=/api/auth" in set_cookies, "refresh cookie scoped to /api/auth")
    status, _, data = c.request("GET", "/api/user/get-nickname")
    check(status == 200 and data == b"Smoke", f"nickname -> {status} {data}")

    favorite = {"cityName": "London", "countryName": "GB"}
    status, _, _ = c.json("POST", "/api/weather/save-favorite-location", favorite)
    check(status == 200, f"save favorite -> {status}")
    status, _, _ = c.json("POST", "/api/weather/save-favorite-location", favorite)
    check(status == 409, f"duplicate favorite -> {status} (expected 409)")
    status, _, data = c.json("GET", "/api/weather/get-user-favorite-location")
    check(status == 200 and isinstance(data, list) and any(f.get("cityName") == "London" for f in data),
          f"list favorites (persisted in PostgreSQL) -> {status}")

    old_access = c.cookies.get("access_token", ("", "/"))[0]
    status, _, _ = c.json("POST", "/api/auth/refresh")
    check(status == 200 and c.cookies.get("access_token", ("", "/"))[0] != old_access, f"refresh rotates tokens -> {status}")

    status, _, _ = c.json("DELETE", "/api/weather/remove-favorite-location", favorite)
    check(status == 200, f"remove favorite -> {status}")
    status, _, data = c.json("GET", "/api/weather/get-user-favorite-location")
    check(status == 200 and data == [], f"favorites empty after removal -> {status}")
    status, _, _ = c.json("POST", "/api/auth/sign-out")
    check(status == 200, f"sign-out -> {status}")
    status, _, _ = c.json("GET", "/api/user/get-nickname")
    check(status == 401, f"signed out session rejected -> {status}")
    return email


def limiter_check(base, origin, header):
    print(f"Login limiter keys on {header}")
    attacker = Client(base, origin, {header: "203.0.113.10"})
    attacker.load_csrf()
    statuses = [attacker.json("POST", "/api/auth/sign-in", {"email": "nobody@example.com", "password": "wrong-1"})[0]
                for _ in range(6)]
    check(statuses[:5] == [401] * 5 and statuses[5] == 429, f"6 bad logins from one IP -> {statuses}")
    other = Client(base, origin, {header: "203.0.113.11"})
    other.load_csrf()
    status = other.json("POST", "/api/auth/sign-in", {"email": "nobody@example.com", "password": "wrong-1"})[0]
    check(status == 401, f"another IP is not blocked -> {status} (expected 401)")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("base_url")
    parser.add_argument("--origin", help="Origin header for state-changing requests (default: base URL)")
    parser.add_argument("--full", action="store_true")
    parser.add_argument("--spoof-client-ip", action="store_true")
    parser.add_argument("--client-ip-header", default="CF-Connecting-IP")
    args = parser.parse_args()
    origin = args.origin or args.base_url.rstrip("/")

    read_only_checks(args.base_url, origin)
    if args.full:
        full_flow(args.base_url, origin)
    if args.spoof_client_ip:
        limiter_check(args.base_url, origin, args.client_ip_header)

    print(f"\n{'FAILED: ' + str(len(FAILURES)) + ' check(s)' if FAILURES else 'All checks passed'}")
    sys.exit(1 if FAILURES else 0)


if __name__ == "__main__":
    main()
