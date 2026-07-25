# Authentication, Authorization & Tokens (JWT) — Notes

## 1. Authentication vs Authorization

Two different questions, often confused:

| | **Authentication (AuthN)** | **Authorization (AuthZ)** |
|--|----------------------------|---------------------------|
| Question | **Who are you?** | **What are you allowed to do?** |
| Checks | Identity (username + password, OTP, biometrics) | Permissions / roles on a resource |
| Happens | First — at login | After — on every protected action |
| Output | A proven identity (→ a **token**) | Allow / deny this specific request |
| Spring | `AuthenticationManager`, login filter | `@PreAuthorize`, `@Secured`, roles |

```
login ─▶ [AuthN: verify who you are] ─▶ issue token
request ─▶ [AuthN: token valid?] ─▶ [AuthZ: are you allowed?] ─▶ resource
```

> Mnemonic: **AuthN = identity**, **AuthZ = permission**. You authenticate
> **once**; you authorize on **every** request.

---

## 2. `@PreAuthorize` — authorization in Spring

`@PreAuthorize` runs a **permission check BEFORE the method executes**. It's an
**AOP aspect** (see [`10-AOP`](./10-AOP-Aspect-Oriented-Programming.md)): a proxy
wraps the bean and evaluates a **SpEL** expression; if it's `false`, the method
never runs and Spring throws `AccessDeniedException` (403).

```java
@Configuration
@EnableMethodSecurity                 // turns on @PreAuthorize (Spring Security 6+)
public class SecurityConfig { }
```

```java
@Service
public class AccountService {

    @PreAuthorize("hasRole('ADMIN')")                 // only ADMINs
    public void deleteUser(Long id) { ... }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")    // either role
    public void viewReports() { ... }

    // SpEL can reference method args and the logged-in principal:
    @PreAuthorize("#userId == authentication.principal.id")  // only your own data
    public User getProfile(Long userId) { ... }

    @PreAuthorize("hasAuthority('SCOPE_read')")       // OAuth2 scope / fine-grained
    public List<Item> list() { ... }
}
```

| Annotation | When it checks | Note |
|------------|----------------|------|
| `@PreAuthorize` | **before** the method | most common; can read args + principal |
| `@PostAuthorize` | **after**, can inspect return value | e.g. `returnObject.owner == authentication.name` |
| `@Secured("ROLE_ADMIN")` | before | older, no SpEL |
| `@RolesAllowed` | before | JSR-250 standard equivalent |

> Because it's a **proxy** (AOP), the same gotcha applies: **self-invocation**
> (one method calling another `@PreAuthorize` method on `this`) **bypasses** the
> check. Call through the injected bean.

---

## 3. Types of tokens (the big picture)

After you authenticate, the server gives you a **token** you send on every later
request (usually `Authorization: Bearer <token>`). Two families:

| | **JWT (self-contained)** | **Opaque token** |
|--|--------------------------|------------------|
| What it is | A **signed** string that **carries the data** (claims) | A **random string** — just an ID, carries nothing |
| Validation | Server **verifies the signature** locally — no DB/lookup | Server must **look it up** (DB / auth server) every time |
| State | **Stateless** (server stores nothing) | **Stateful** (server stores the token → user mapping) |
| Revoke | **Hard** (valid until it expires) | **Easy** (delete the record) |
| Size | Larger (carries claims) | Tiny |
| Example | `eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM...` | `a1b2c3d4-...` (UUID) |

> **Key mental model:** *"stateless"* = the server doesn't remember the token; it
> re-derives trust from the **signature** each time. *"stateful/opaque"* = the
> server remembers, so it can also **forget** (revoke) instantly.

---

## 4. JWT structure & claims

A JWT is **three Base64URL parts** joined by dots — **`header.payload.signature`**:

```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9 . eyJzdWIiOiIxMjMiLCJyb2xlIjoiQURNSU4ifQ . 4pcP_...signature
└──────────── HEADER ────────────┘   └──────────── PAYLOAD ───────────┘   └── SIGNATURE ──┘
    { "alg":"HS256", "typ":"JWT" }        { the CLAIMS }               HMAC/RSA over header.payload
```

- **Header** — algorithm (`HS256`, `RS256`) + type.
- **Payload = the CLAIMS** — the actual data about the user.
- **Signature** — proves the token wasn't tampered with; only the server holds
  the key that can produce/verify it.

> ⚠️ **Base64 is NOT encryption.** Header + payload are **readable by anyone**
> (paste into jwt.io). The signature only guarantees **integrity**, not secrecy.
> → **Never put passwords or secrets in a JWT.**

### Claims (the payload)

```json
{
  "sub": "12345",              // subject — WHO the token is about (user id)
  "name": "Lovepreet Singh",
  "role": "ADMIN",             // custom claim used by @PreAuthorize / hasRole
  "iss": "auth.myapp.com",     // issuer — who created it
  "aud": "myapp-api",          // audience — who it's for
  "iat": 1690000000,           // issued-at (epoch seconds)
  "exp": 1690003600            // expiry — token invalid after this
}
```

| Claim type | Examples | Meaning |
|------------|----------|---------|
| **Registered** (standard) | `sub`, `iss`, `aud`, `exp`, `iat`, `nbf`, `jti` | reserved keys with defined meaning |
| **Public** | `role`, `email` | agreed-upon custom keys |
| **Private** | anything your app invents | app-specific data |

---

## 5. Access token vs Refresh token — and the "is it inside the JWT?" question

**This is the confusion to clear up:**

> **"Is both the access token and the refresh token present inside one JWT? And
> if not, what's the difference between an access token and a JWT token?"**

**Answer — two separate ideas:**

1. **JWT is a FORMAT, not a kind of token.** "Access token vs JWT" is a category
   mix-up. An **access token** is a *role/purpose*; **JWT** is one *encoding* it
   can use. An access token can **be** a JWT, or it can be **opaque**. So:
   - "access token" answers *what is this token for?* → calling the API.
   - "JWT" answers *how is this token encoded?* → signed `header.payload.sig`.

2. **Access and refresh are TWO SEPARATE tokens**, issued together at login — the
   refresh token is **not nested inside** the access-token JWT.

```
        LOGIN  ──▶  server returns TWO tokens:
                    ├─ access token  (short-lived, e.g. 15 min)  → sent on every API call
                    └─ refresh token (long-lived, e.g. 7 days)   → used ONLY to get a new access token
```

| | **Access token** | **Refresh token** |
|--|-------------------|-------------------|
| Purpose | Prove identity on **each API call** | Get a **new access token** when it expires |
| Lifetime | **Short** (5–30 min) | **Long** (days–weeks) |
| Sent to | Every protected endpoint | Only the `/refresh` endpoint |
| Format | Often a **JWT** (stateless) | Usually **opaque**, **stored in DB** |
| Stored server-side? | No (if JWT) | **Yes** — so it can be revoked |
| If stolen | Limited blast radius (expires fast) | Dangerous → that's why it's revocable |

### Why short access + long refresh?

Pure JWTs **can't be revoked** (valid until `exp`). If the access token is
long-lived and leaks, an attacker has access until it expires. So:

- **access token** = short-lived JWT → little damage if leaked, **no DB check**
  needed (fast).
- **refresh token** = long-lived, **opaque, stored in DB** → can be **revoked**
  instantly (delete the row) → you get back the ability to log someone out.

```
API call ─▶ access token expired (401)
         ─▶ POST /refresh  with refresh token
         ─▶ server checks refresh token IN DB (still valid? not revoked?)
         ─▶ issues a fresh access token  (and often rotates the refresh token)
```

### The "fingerprint" idea (Paytm-style)

Apps like Paytm bind the refresh/session token to the **device fingerprint**
(device id, biometrics, PIN). The long-lived credential lives in secure device
storage; the server stores a hash of it. On reopen, the app silently refreshes
using that device-bound token — so you stay "logged in" without re-entering the
password, but a stolen token from a different device/fingerprint is rejected and
can be revoked server-side.

> **In one line:** *the access token is what you show; the refresh token is how
> you renew. JWT is just one way to write the access token. They are two tokens,
> not one nested token.*

---

## 6. Opaque tokens

An **opaque token** carries **no data** — it's a random handle. The server (or a
dedicated auth server) keeps the mapping and must **introspect** it each time.

```
client ─▶ Bearer a1b2c3d4-...   ─▶  API asks auth server "who is a1b2c3d4?"
                                     (RFC 7662 token introspection / DB lookup)
                                 ◀─  { active:true, sub:123, scope:"read" }
```

| | **JWT** | **Opaque** |
|--|---------|------------|
| Validation cost | cheap (local signature check) | network/DB lookup **every request** |
| Revocation | hard (must wait for `exp` / keep a blocklist) | trivial (delete server-side) |
| Data leakage | payload readable by anyone | nothing leaks (random string) |
| Best for | high-throughput internal APIs | when instant revoke / secrecy matters |

> Common real-world combo: **JWT access token** (fast, stateless) **+ opaque
> refresh token in DB** (revocable). Best of both.

---

## 7. Pros & cons of JWT

### Pros
- **Stateless / scalable** — no server-side session store; any server (or
  auto-scaled pod) can verify with just the signing key. Great for microservices.
- **Fast validation** — signature check is local, **no DB round-trip** per request.
- **Self-contained** — carries roles/claims, so `@PreAuthorize` can read `role`
  straight from the token; no user lookup needed.
- **Cross-domain / cross-service** — one token works across services that trust
  the issuer (SSO, OAuth2).

### Cons
- **Can't easily revoke** — valid until `exp`. Logout / "ban this user" needs a
  **blocklist** (which reintroduces state) or short expiry + refresh tokens.
- **No secrecy** — payload is only Base64, readable by anyone → no secrets inside.
- **Bigger than a session id** — sent on **every** request → more bytes on the
  wire (see latency below), and can bloat headers if you stuff many claims in.
- **Key management** — if the signing key leaks, **every** token is forgeable;
  rotating keys is operationally harder.
- **Stale claims** — a role baked into a 15-min token stays stale until it
  expires (revoked admin still shows `role: ADMIN` briefly).

> **Rule of thumb:** JWT for **short-lived access tokens**; opaque + DB for
> **refresh tokens** and anything that must be revocable immediately.

---

## 8. API latency: metrics & how JWT affects it

### Metrics to measure API response latency

| Metric | What it tells you |
|--------|-------------------|
| **p50 / median** | typical request time |
| **p95 / p99 (tail latency)** | the slow requests real users feel; watch these |
| **p99.9 / max** | worst case, outliers |
| **Average (mean)** | easily hidden by outliers — don't rely on it alone |
| **Throughput (req/s, RPS)** | how many requests handled per second |
| **Error rate** | % of 4xx/5xx |
| **TTFB** (time to first byte) | server processing + network before first byte |

- **RED** method (per request): **R**ate, **E**rrors, **D**uration.
- **Four Golden Signals**: latency, traffic, errors, saturation.
- Tools: **Micrometer + Prometheus + Grafana**, `@Timed`, Spring Boot Actuator
  (`/actuator/metrics/http.server.requests`), distributed tracing (Zipkin,
  OpenTelemetry) to see time spent in the **auth filter** specifically.

```java
// Micrometer: time a method, then read percentiles in Prometheus/Grafana
@Timed(value = "user.login", percentiles = {0.5, 0.95, 0.99})
public String login(...) { ... }
```

### How JWT adds latency

JWT is fast (local check) but **not free** — the cost is per request:

1. **Signature verification on every request** — HMAC (`HS256`) is cheap;
   **RSA/EC (`RS256`) is markedly slower** (asymmetric crypto) and runs on
   **every** call → shows up in p95/p99 under load.
2. **Bigger payload on the wire** — a JWT (hundreds of bytes to a few KB) is sent
   in the header on **every** request, vs a tiny session id. More bytes = more
   serialization + network time, worse over slow mobile links.
3. **Parsing/decoding** — Base64URL decode + JSON parse of the claims each request.
4. **Blocklist check (if you added revocation)** — to make JWTs revocable you
   often check a Redis/DB blocklist per request → **reintroduces the DB round-trip
   you used JWT to avoid**, adding latency.
5. **JWKS fetch (RS256)** — verifying keys may require fetching the issuer's
   public keys; cache them or every request pays a network hop.

> **Trade-off:** opaque tokens push latency to a **lookup** (network/DB) every
> request; JWTs push it to **crypto + bigger payloads** every request. JWT is
> usually faster **until** you add revocation/blocklists, which erode the
> stateless advantage. Measure p95/p99, not the average.

---

## 9. Where to store tokens on the client — Cookies vs localStorage

| | **`localStorage` / `sessionStorage`** | **Cookies (`HttpOnly` + `Secure`)** |
|--|--------------------------------------|-------------------------------------|
| JS access | **Yes** — any script can read it | **No** if `HttpOnly` — JS can't touch it |
| **XSS** risk | **High** — a malicious script steals the token | **Low** — `HttpOnly` hides it from JS |
| **CSRF** risk | Low (not auto-sent) | **Yes** — auto-sent → need `SameSite` + CSRF token |
| Sent automatically | No — you add the `Authorization` header manually | Yes — browser attaches it to matching requests |
| Works cross-site | Easy (you control the header) | Needs `SameSite=None; Secure` config |
| Survives refresh/tab | localStorage yes; sessionStorage per-tab | yes (until expiry) |

**Guidance:**
- **Prefer `HttpOnly`, `Secure`, `SameSite` cookies** for tokens (esp. refresh
  tokens) — immune to XSS token theft. Pair with a **CSRF token** to cover the
  CSRF exposure cookies bring.
- **Avoid `localStorage`** for long-lived/refresh tokens — one XSS bug = full
  account takeover. If you must (SPA calling a cross-origin API), keep it to a
  **short-lived access token** and store the **refresh token in an HttpOnly
  cookie**.

```
Set-Cookie: refresh=<token>; HttpOnly; Secure; SameSite=Strict; Path=/refresh
```

> **Best-practice combo:** short-lived **access token** in memory (JS variable,
> not localStorage) + **refresh token** in an **HttpOnly Secure cookie**, DB-backed
> so it's revocable. Minimizes both XSS and CSRF blast radius.

---

### Quick recap
- **AuthN = who you are** (login → token); **AuthZ = what you can do** (checked
  every request via `@PreAuthorize`, which is an **AOP** proxy check — beware
  self-invocation).
- **JWT is a FORMAT, not a token type.** An **access token** can be a JWT or
  opaque; "access token vs JWT" is a category mix-up.
- **Access and refresh are TWO separate tokens** (not nested): access = short,
  sent on every call, usually a **JWT**; refresh = long, opaque, **stored in DB**,
  used only to mint new access tokens → gives you **revocation**. Paytm binds it
  to a **device fingerprint**.
- **JWT** = `header.payload(claims).signature`; Base64 is **readable**, only
  signed → **no secrets inside**. Standard claims: `sub`, `exp`, `iat`, `iss`,
  `aud`, plus custom (`role`).
- **Opaque token** = random id, needs server lookup → **easy to revoke**; JWT =
  self-contained → **hard to revoke** but no lookup.
- **JWT pros:** stateless, scalable, fast local validation. **Cons:** hard to
  revoke, no secrecy, bigger payload, key management, stale claims.
- **Latency:** measure **p95/p99** (not average), RED / golden signals via
  Micrometer/Actuator. JWT adds cost per request: **signature verify (RS256
  slow)**, bigger payload on the wire, parsing, and any **blocklist check**
  (which brings back the DB round-trip).
- **Storage:** prefer **HttpOnly + Secure + SameSite cookies** (XSS-safe) over
  **localStorage** (XSS-exposed); add a CSRF token for cookies.
