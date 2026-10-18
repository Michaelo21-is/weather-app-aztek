# Weather App — Backend

Backend for a weather application. It has two parts:

- **Authentication system:** sign up, sign in, cookie-based JWT authentication, token refresh with rotation, sign out, CSRF protection and brute-force protection.
- **Weather service:** weather by city or coordinates, city autocomplete and per-user favorite locations, cached in Redis.

## Tech Stack

| Technology | Role |
|---|---|
| Java 17 · Spring Boot 4.1 | Application framework (Spring Web MVC) |
| Spring Security | Filter chain, CSRF, CORS, authorization |
| JJWT 0.13 | Signing and validating JWTs (HS256) |
| Spring Data JPA · PostgreSQL 17 | Users, refresh tokens and favorite locations |
| Spring Data Redis · Redis 7.4 | Failed-login counters, IP blocks and weather cache |
| OpenWeather · Open-Meteo | Weather data and geocoding · city autocomplete |
| BCrypt | Password hashing |
| Docker · Docker Compose | Multi-stage image and local stack |
| Maven · Lombok | Build and boilerplate reduction |

---

## Architecture

Layered Spring Boot app: **Controller → Service → Repository**. Every request first goes through the Spring Security filter chain. PostgreSQL holds persistent data. Redis holds short-lived rate-limit state.

```mermaid
flowchart LR
    Client["Browser / SPA<br/>WEBSITE_URL"]

    subgraph App["weather-app :8080"]
        direction LR
        subgraph Chain["Security Filter Chain"]
            direction TB
            CORS[CORS] --> CSRF[CSRF check] --> JWT[JwtAuthenticationFilter] --> AUTHZ[Authorization]
        end
        Controllers["AuthController<br/>CsrfController"]
        Auth[AuthService]
        Jwt[JwtService]
        Cookies[AuthCookieService]
        Attempts[LoginAttemptService]
        Repos["UsersRepository<br/>RefreshTokenRepository"]
        Redis[RedisService]
    end

    PG[("PostgreSQL<br/>users, refresh_token")]
    RD[("Redis<br/>login:* keys")]

    Client -- "HTTP + cookies" --> Chain
    Chain --> Controllers
    Controllers --> Auth
    Controllers --> Cookies
    Auth --> Jwt
    Auth --> Attempts
    Auth --> Repos
    Attempts --> Redis
    Repos --> PG
    Redis --> RD
```

| Component | Responsibility |
|---|---|
| `SecurityConfig` | Stateless filter chain, CSRF, public routes, 401 entry point |
| `JwtAuthenticationFilter` | Reads the `access_token` cookie and sets the user ID (`UUID`) as the principal |
| `AuthService` | Sign up, sign in, refresh, sign out |
| `JwtService` | Creates and validates `ACCESS` / `REFRESH` tokens |
| `AuthCookieService` | Writes, reads and clears the HttpOnly token cookies |
| `LoginAttemptService` | Per-IP failed-login counting and progressive blocking |
| `RedisService` | Redis wrapper, including an atomic `INCR` + `PEXPIRE` Lua script |

---

## Authentication Flows

> Every `POST` endpoint requires a valid CSRF token (`X-XSRF-TOKEN` header + `XSRF-TOKEN` cookie). Without one, the request is rejected with **403** before it reaches a controller.

### 1. CSRF Token Bootstrap — `GET /api/auth/csrf`

```mermaid
stateDiagram-v2
    [*] --> RequestCsrf : GET /api/auth/csrf
    RequestCsrf --> TokenIssued : Spring Security creates token
    TokenIssued --> [*] : 200 with headerName and masked token, sets XSRF-TOKEN cookie
```

The cookie is HttpOnly, so the frontend reads the (BREACH-masked) token from the response body and sends it in the `X-XSRF-TOKEN` header.

### 2. Sign Up — `POST /api/auth/sign-up`

```mermaid
stateDiagram-v2
    [*] --> CsrfCheck
    CsrfCheck --> Rejected403 : missing or invalid token
    CsrfCheck --> EmailCheck : valid
    EmailCheck --> Conflict409 : email already exists
    EmailCheck --> HashPassword : email is free
    HashPassword --> SaveUser : BCrypt
    SaveUser --> Created200 : saved
    SaveUser --> Error500 : database error
    Rejected403 --> [*]
    Conflict409 --> [*]
    Created200 --> [*]
    Error500 --> [*]
```

Sign up does **not** sign the user in. No tokens are issued.

### 3. Sign In — `POST /api/auth/sign-in`

```mermaid
stateDiagram-v2
    [*] --> CsrfCheck
    CsrfCheck --> Rejected403 : invalid token
    CsrfCheck --> BlockCheck : valid
    BlockCheck --> Blocked429 : client IP is blocked
    BlockCheck --> VerifyCredentials : not blocked
    VerifyCredentials --> RecordFailure : unknown email or wrong password
    RecordFailure --> Unauthorized401 : below limit
    RecordFailure --> Blocked429 : limit reached, IP blocked
    VerifyCredentials --> ResetCounter : BCrypt match
    ResetCounter --> IssueTokens
    IssueTokens --> SignedIn200 : store refresh hash, set cookies
    Rejected403 --> [*]
    Blocked429 --> [*]
    Unauthorized401 --> [*]
    SignedIn200 --> [*]
```

- Unknown email and wrong password return the same `401` response.
- `IssueTokens` creates an access token and a refresh token. Only the refresh token's **SHA-256 hash** is saved, replacing any earlier one for that user.

### 4. Brute-Force Protection (per client IP, Redis)

```mermaid
stateDiagram-v2
    [*] --> Clean
    Clean --> Counting : failed login
    Counting --> Counting : failed login, below 6
    Counting --> Clean : successful login or 15m without failures
    Counting --> Blocked : 6th failure
    Blocked --> Clean : block expires, history kept 24h
    note right of Blocked
        Block time doubles per block in 24h
        5m, 10m, 20m, 40m, capped at 60m
    end note
```

| Redis key | Meaning | TTL |
|---|---|---|
| `login:attempts:<ip>` | Failed-attempt counter | 15m after last failure |
| `login:block:<ip>` | Active block | Block duration |
| `login:block-history:<ip>` | Block count used for escalation | 24h after last block |

### 5. JWT Request Authentication (every request)

```mermaid
stateDiagram-v2
    [*] --> ReadCookie
    ReadCookie --> Anonymous : no access_token cookie
    ReadCookie --> Validate : cookie present
    Validate --> Anonymous : bad signature, expired, or not an ACCESS token
    Validate --> Authenticated : valid, principal is user UUID
    Anonymous --> PublicRoute : public endpoint
    Anonymous --> Unauthorized401 : protected endpoint
    Authenticated --> Controller
    PublicRoute --> Controller
    Controller --> [*]
    Unauthorized401 --> [*]
```

An invalid token is ignored instead of rejected, so public endpoints like `/refresh` still work when the access token has expired. No protected business endpoints exist yet; every non-public route already requires authentication.

### 6. Token Refresh — `POST /api/auth/refresh`

```mermaid
stateDiagram-v2
    [*] --> CsrfCheck
    CsrfCheck --> Rejected403 : invalid token
    CsrfCheck --> ReadRefreshCookie : valid
    ReadRefreshCookie --> Unauthorized401 : no cookie
    ReadRefreshCookie --> ValidateJwt : cookie present
    ValidateJwt --> Unauthorized401 : invalid, expired, or not a REFRESH token
    ValidateJwt --> CompareHash : valid
    CompareHash --> Unauthorized401 : hash differs from stored token
    CompareHash --> Rotate : matches
    Rotate --> Refreshed200 : new token pair, old refresh token now invalid
    Rejected403 --> [*]
    Unauthorized401 --> [*]
    Refreshed200 --> [*]
```

### 7. Sign Out — `POST /api/auth/sign-out`

```mermaid
stateDiagram-v2
    [*] --> CsrfCheck
    CsrfCheck --> Rejected403 : invalid token
    CsrfCheck --> CheckRefreshToken : valid
    CheckRefreshToken --> RevokeToken : valid and current
    CheckRefreshToken --> ClearCookies : missing or invalid
    RevokeToken --> ClearCookies : delete DB row
    ClearCookies --> SignedOut200 : both cookies expired
    Rejected403 --> [*]
    SignedOut200 --> [*]
```

Sign out always succeeds and clears the cookies. The access token is stateless, so it is not revoked on the server and stays valid until it expires (max 15m).

---

## Weather Service Flows

> `WeatherController` → `WeatherService` → `OpenWeatherClient` / `LocationAutocompleteClient` (external APIs), `RedisService` (cache) and `FavoriteLocationRepository` (PostgreSQL). Invalid query parameters are rejected with **400** before any cache or provider access.

### 1. Weather by City — `GET /api/weather/city`

```mermaid
stateDiagram-v2
    [*] --> ValidateParams : cityName, countryName
    ValidateParams --> BadRequest400 : blank, too long, bad characters, or country not 2 letters
    ValidateParams --> RequestedNameCache : valid
    RequestedNameCache --> Ok200 : hit
    RequestedNameCache --> GeocodeCache : miss
    GeocodeCache --> CityCache : hit
    GeocodeCache --> Geocode : miss
    Geocode --> NotFound404 : no result in the requested country
    Geocode --> ProviderError : call failed
    Geocode --> CityCache : pick best match, cache 7d
    CityCache --> Ok200 : hit (geocoded name)
    CityCache --> FetchWeather : miss
    FetchWeather --> ProviderError : call failed
    FetchWeather --> Ok200 : map response, cache until next full local hour
    BadRequest400 --> [*]
    NotFound404 --> [*]
    ProviderError --> [*]
    Ok200 --> [*]
```

- **Best match:** results are filtered to the requested country. A result named exactly like the query (ignoring case and accents, also checking `local_names`) wins. Otherwise the geocoder's first result is used.
- The city cache key is the country code plus the city name, ignoring case, accents and repeated spaces (`weather:city:GB:london`), so all visitors share it. The requested name is looked up first, so a city that is already cached (also by a coordinates search) needs no geocoding. On a miss, the weather is cached under the geocoded name, so aliases like `Munich` and `München` share one entry. The response uses the geocoded name, not the weather API's district name.
- Two cities with the same name in one country (e.g. Springfield, US) share one entry, since the request carries no state.
- `FetchWeather` calls OpenWeather **current weather** and then the **5 day / 3 hour forecast**. If either call fails, the request fails.

### 2. Weather by Location — `GET /api/weather/coordinates`

```mermaid
stateDiagram-v2
    [*] --> SessionFilter
    SessionFilter --> IssueSession : weather_session cookie missing or malformed
    SessionFilter --> ValidateParams : valid cookie
    IssueSession --> ValidateParams : new random ID, Set-Cookie
    ValidateParams --> BadRequest400 : lat or lon out of range
    ValidateParams --> SessionCache : valid
    SessionCache --> Ok200 : hit, request coordinates ignored
    SessionCache --> FetchWeather : miss
    FetchWeather --> ProviderError : call failed
    FetchWeather --> Ok200 : map response, cache until next full local hour
    BadRequest400 --> [*]
    ProviderError --> [*]
    Ok200 --> [*]
```

The cache is keyed by the visitor's weather session, not by coordinates. While the entry is valid (until the next full local hour), new coordinates from the same visitor return the cached location.

After a fetch, the weather is also cached for city searches under the place name and country OpenWeather returns (`weather:city:<CC>:<name>`, same TTL), unless that city is already cached. Positions without a place name (e.g. at sea) are not shared. Other visitors get this entry, so its coordinates are rounded to 0.1° (about 11 km) and do not reveal the searcher's position.

### 3. Redis Cache Hit / Miss

Used by every cached lookup: session weather, city weather, geocoding and autocomplete.

```mermaid
stateDiagram-v2
    [*] --> ReadCache : GET key
    ReadCache --> Hit : JSON found
    ReadCache --> Miss : key absent
    ReadCache --> ReadFailed : Redis unreachable or unreadable JSON
    ReadFailed --> Miss : log warning, treat as miss
    Hit --> [*] : return cached value
    Miss --> CallProvider
    CallProvider --> ProviderError : call failed, nothing cached
    CallProvider --> WriteCache : success
    WriteCache --> Returned : SET with TTL
    WriteCache --> WriteFailed : Redis or JSON error
    WriteFailed --> Returned : log warning
    Returned --> [*] : return fresh value
    ProviderError --> [*]
```

A Redis outage does not break the weather endpoints. Every request just goes to the provider instead.

| Redis key | Value | TTL |
|---|---|---|
| `weather:session:<sessionId>` | Weather for the visitor's location | Until the next full hour in the location's timezone |
| `weather:city:<CC>:<city>` | Weather for a city, shared by all visitors (written by city and coordinates searches) | Until the next full hour in the location's timezone |
| `weather:geocode:<CC>:<city>` | Resolved city (name, state, country, coordinates) | `weather.geocoding-ttl` (7d) |
| `weather:locations:<prefix>` | Autocomplete suggestions (also an empty list) | `weather.autocomplete-ttl` (24h) |

### 4. External API Calls and Error Mapping

Every outgoing call goes through `ExternalApiCall.execute` (connect timeout 3s, read timeout 5s, no retries).

```mermaid
stateDiagram-v2
    [*] --> SendRequest
    SendRequest --> CheckBody : 2xx
    SendRequest --> Unavailable503 : provider returned 429
    SendRequest --> BadGateway502 : other HTTP error or unparseable body
    SendRequest --> GatewayTimeout504 : timeout or I/O error
    CheckBody --> BadGateway502 : empty body
    CheckBody --> Success : body present
    Success --> [*]
    Unavailable503 --> [*]
    BadGateway502 --> [*]
    GatewayTimeout504 --> [*]
```

| Provider | Endpoint | Used for |
|---|---|---|
| OpenWeather | `/data/2.5/weather` | Current weather |
| OpenWeather | `/data/2.5/forecast` | 5 day / 3 hour forecast |
| OpenWeather | `/geo/1.0/direct` (limit 5) | City → coordinates |
| Open-Meteo | `/v1/search` (no key) | City autocomplete |

The OpenWeather key is added as `appid` on the server. Exception messages are never logged, because they contain the request URL with the key.

### 5. City Autocomplete — `GET /api/weather/city-suggestion`

```mermaid
stateDiagram-v2
    [*] --> ValidateParams : query
    ValidateParams --> BadRequest400 : under 2 or over 60 chars, or bad characters
    ValidateParams --> SuggestionCache : valid
    SuggestionCache --> Ok200 : hit
    SuggestionCache --> SearchOpenMeteo : miss
    SearchOpenMeteo --> ProviderError : call failed
    SearchOpenMeteo --> FilterResults : up to 10 candidates
    FilterResults --> Ok200 : true prefix matches, deduplicated, max 5, cache 24h
    BadRequest400 --> [*]
    ProviderError --> [*]
    Ok200 --> [*]
```

### 6. Save Favorite Location — `POST /api/weather/save-favorite-location`

```mermaid
stateDiagram-v2
    [*] --> CsrfCheck
    CsrfCheck --> Rejected403 : missing or invalid token
    CsrfCheck --> AuthCheck : valid
    AuthCheck --> Unauthorized401 : no valid access token
    AuthCheck --> LoadUser : authenticated
    LoadUser --> Unauthorized401 : user not found
    LoadUser --> LimitCheck : found
    LimitCheck --> Forbidden403 : limit reached, 10 by default
    LimitCheck --> DuplicateCheck : below limit
    DuplicateCheck --> Conflict409 : same city and country already saved
    DuplicateCheck --> Saved200 : insert row, counter plus 1
    Rejected403 --> [*]
    Unauthorized401 --> [*]
    Forbidden403 --> [*]
    Conflict409 --> [*]
    Saved200 --> [*]
```

- Body: `{ cityName, countryName }`. The limit comes from `favorite.location.max`, checked against `users.favorite_city_counter`.
- The insert and the counter update run in one transaction. The city is stored as sent. It is not validated or geocoded on save.

### 7. Remove Favorite Location — `DELETE /api/weather/remove-favorite-location`

```mermaid
stateDiagram-v2
    [*] --> CsrfCheck
    CsrfCheck --> Rejected403 : missing or invalid token
    CsrfCheck --> AuthCheck : valid
    AuthCheck --> Unauthorized401 : no valid access token
    AuthCheck --> LoadUser : authenticated
    LoadUser --> NotFound404 : user not found
    LoadUser --> DeleteRows : found
    DeleteRows --> NotFound404 : no matching favorite
    DeleteRows --> Removed200 : counter minus deleted rows
    Rejected403 --> [*]
    Unauthorized401 --> [*]
    NotFound404 --> [*]
    Removed200 --> [*]
```

Same body as save. Rows are matched on user, `cityName` and `countryName`, all in one transaction.

### 8. Get Favorite Locations — `GET /api/weather/get-user-favorite-location`

```mermaid
stateDiagram-v2
    [*] --> AuthCheck
    AuthCheck --> Unauthorized401 : no valid access token
    AuthCheck --> LoadFavorites : authenticated
    LoadFavorites --> Ok200 : no rows, empty list
    LoadFavorites --> CityWeather : one or more rows
    CityWeather --> CityWeather : next row
    CityWeather --> Ok200 : all rows resolved
    CityWeather --> Failed : any lookup fails
    Unauthorized401 --> [*]
    Failed --> [*] : whole request returns that error
    Ok200 --> [*]
```

Each favorite goes through the **Weather by City** flow (flow 1), including its geocoding and city caches, one by one. If one city fails (404 or a provider error), the whole request fails. There is no partial result. The response is a list of `{ cityName, countryName, weather }`.

### Weather Error Summary

| Status | Cause |
|---|---|
| `400` | Invalid query parameters (Bean Validation on the controller) |
| `401` | Favorites endpoint without a valid access token, or save for an unknown user |
| `403` | Missing CSRF token on save/remove, or favorite limit reached |
| `404` | City not found by geocoding, unknown user on remove, or favorite to remove does not exist |
| `409` | Favorite already saved |
| `502` | Provider HTTP error, unparseable or empty response |
| `503` | Provider rate limit (HTTP 429) |
| `504` | Provider timeout or connection error |

---

## Security

| Mechanism | Implementation |
|---|---|
| **Token storage** | JWTs live in `HttpOnly` cookies, so JavaScript (and XSS) cannot read them. `Secure` and `SameSite=Lax` are configurable. |
| **Cookie scoping** | `access_token` uses path `/`. `refresh_token` uses path `/api/auth`, so it is never sent with regular API calls. |
| **Token types** | A `token_type` claim (`ACCESS` / `REFRESH`). Each one is rejected where the other is expected. |
| **Lifetimes** | Access 15 minutes, refresh 14 days. HS256 secret of at least 32 characters, checked at startup. |
| **Refresh rotation** | One stored refresh token per user. Every sign-in or refresh replaces it, so old tokens stop working. |
| **Hashed refresh tokens** | Only SHA-256 hashes are stored, compared in constant time (`MessageDigest.isEqual`). |
| **Passwords** | BCrypt (salted, checked with `matches()`). |
| **CSRF** | Enabled for all state-changing requests, including sign-in and sign-up, because cookies are sent automatically. |
| **CORS** | Explicit origin allow-list (wildcards rejected at startup), credentials allowed, headers limited to `Content-Type` and `X-XSRF-TOKEN`. |
| **Brute force** | Per-IP counter and progressive blocks in Redis. Uses an atomic Lua script, so concurrent requests cannot skip the limit. |
| **IP source** | Uses `getRemoteAddr()`, not a raw `X-Forwarded-For`, so clients cannot forge their IP. |
| **Stateless** | No HTTP session. Form login, HTTP Basic and default logout are disabled. Unauthenticated requests get `401`. |
| **Secret hygiene** | `JwtProperties.toString()` masks the secret. `.env` is excluded from Git and from the Docker image. |

**Design decision:** a user can have only one active refresh token (`@OneToOne`), so signing in on a new device ends the refresh session on the previous one.

---

## Docker & Infrastructure

| Service | Image | Network alias | Host port | Notes |
|---|---|---|---|---|
| `weather-app` | built from `Dockerfile` | — | `8080` | Starts only after Postgres and Redis report healthy and `postgres-init` succeeds; runs with the `local` profile |
| `postgres` | `postgres:17-alpine` | `weather-postgres` | not exposed | Database `${DATABASE_NAME}`, volume `postgres-data`, `pg_isready` healthcheck |
| `postgres-init` | `postgres:17-alpine` | — | — | One-shot job: creates `${DATABASE_NAME}` if missing (idempotent, also for existing volumes) |
| `redis` | `redis:7.4-alpine` | `redis-cache` | not exposed | `--requirepass`, AOF persistence, volume `redis-data`, healthcheck requires `PONG` |

**Dockerfile:** multi-stage build. A JDK 17 stage builds the jar with a Maven cache mount. A JRE 17 runtime stage uses the layered jar and runs as a **non-root** `spring` user.

> **Schema:** the JPA entities are the source of truth. Docker Compose activates the `local` profile (`application-local.properties`), which sets `spring.jpa.hibernate.ddl-auto=update` so Hibernate creates missing tables, foreign keys and sequences. Without that profile `ddl-auto=none`, so production never changes the schema automatically.

---

## API Endpoints

Base path: `/api/auth`. All endpoints are public at the security layer. All `POST` requests need the CSRF header.

| Method | Path | Body | Description | Responses |
|---|---|---|---|---|
| `GET` | `/csrf` | — | Returns `{ headerName, token }` and sets the `XSRF-TOKEN` cookie | `200` |
| `POST` | `/sign-up` | `{ email, password }` | Creates a user with a BCrypt-hashed password | `200`, `409`, `500` |
| `POST` | `/sign-in` | `{ email, password }` | Sets the `access_token` and `refresh_token` cookies | `200`, `401`, `429` |
| `POST` | `/refresh` | — (cookie) | Rotates the token pair | `200`, `401` |
| `POST` | `/sign-out` | — (cookie) | Revokes the refresh token and clears the cookies | `200` |

Any `POST` without a valid CSRF token returns `403`.

### Weather — `/api/weather`

| Method | Path | Params / Body | Access | Description | Cache |
|---|---|---|---|---|---|
| `GET` | `/coordinates` | `lat`, `lon` | Public | Current weather + forecast for the visitor's location | `weather:session:{sessionId}`, also `weather:city:{CC}:{city}` (shared) |
| `GET` | `/city` | `cityName`, `countryName` (ISO 3166 alpha-2) | Public | Resolves the city with OpenWeather Geocoding, then returns its weather | `weather:city:{CC}:{city}` (shared) |
| `GET` | `/city-suggestion` | `query` (2–60 chars) | Public | Up to 5 `{ name, country }` prefix matches (Open-Meteo Geocoding) | `weather:locations:{prefix}` (24h) |
| `POST` | `/save-favorite-location` | `{ cityName, countryName }` | JWT + CSRF | Saves a favorite location | — |
| `DELETE` | `/remove-favorite-location` | `{ cityName, countryName }` | JWT + CSRF | Removes a favorite location | — |
| `GET` | `/get-user-favorite-location` | — | JWT | Favorite locations with their current weather | Via `/city` caches |

Weather entries expire at the **next full hour in the location's timezone**. Every visitor gets a random `weather_session` cookie (HttpOnly, 30 days). It only keys the personal weather cache, is independent of the JWTs and CSRF token, and is not changed by sign-in or sign-out.

OpenWeather's free plan covers Current Weather, the **5 day / 3 hour** forecast and Geocoding (60 calls/minute). So `hourly` uses 3-hour steps and `daily` covers about 5 days, aggregated from those steps.

---

## How to Run

**1. Create `.env`** in the project root, next to `docker-compose.yml`:

| Variable | Purpose |
|---|---|
| `JWT_SECRET` | HS256 signing secret, at least 32 characters |
| `DATABASE_HOST` | `weather-postgres` |
| `DATABASE_PORT` | `5432` |
| `DATABASE_NAME` | Database name, e.g. `weatherdb` (also used to initialize the container) |
| `DATABASE_USERNAME` | PostgreSQL user (also used to initialize the container) |
| `DATABASE_PASSWORD` | PostgreSQL password |
| `REDIS_HOST` | `redis-cache` |
| `REDIS_PORT` | `6379` |
| `REDIS_PASSWORD` | Redis password (also passed to `--requirepass`) |
| `OPEN_WEATHER_API` | OpenWeather API key (free plan). Used server-side only |
| `WEBSITE_URL` | Frontend origin allowed by CORS, e.g. `http://localhost:3000` (no wildcards) |

**2. Start the stack:**

```bash
docker compose up -d --build      # build and start all services
docker compose ps                 # check health status
docker compose logs -f weather-app
docker compose down               # stop (data volumes kept)
docker compose down -v            # stop and delete Postgres/Redis data
```

The API is available at `http://localhost:8080`. The only allowed frontend origin is the one in `WEBSITE_URL` (bound to `security.cors.allowed-origins`).
