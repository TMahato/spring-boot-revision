# Chapter 4 — How Spring Security Actually Works Inside This Project

> **Scope:** The runtime story behind the code you wrote in
> [Chapter 3](./chapter-3-implementation-guide.md): what `SecurityConfig` builds,
> what the filter chain is, where `JwtAuthFilter` sits in it, and what
> `AuthenticationManager` does when `/auth/v1/login` is called.
>
> Every code reference points at real files under
> `ExpenseTracker\src\main\java\com\jassi\expensetracker\`.

---

## 0. The one-paragraph version

Spring Security is **a servlet Filter that owns a list of other filters**. Every
HTTP request passes through that list before it ever reaches a `@RestController`.
Some filters *establish who you are* (authentication), one filter near the end
*checks whether that person is allowed here* (authorization), and if nobody
established an identity for a protected URL, that last filter throws and the
request dies with 401/403. In this project there are two completely separate ways
an identity gets established:

| Route | Who authenticates | Credential checked |
|---|---|---|
| `POST /auth/v1/login` | `AuthenticationManager`, called **by hand** in `TokenController` | username + password against the DB |
| every other protected request | `JwtAuthFilter`, automatically | signature + expiry of a bearer JWT |

Understanding that these two paths **do not touch each other** is 80% of
understanding this codebase.

---

## 1. The filter chain

### 1.1 What a filter chain is

A servlet container (Tomcat, embedded in Spring Boot) processes a request as:

```
Tomcat → [Filter] → [Filter] → [Filter] → ... → DispatcherServlet → your @Controller
```

Each filter gets `(request, response, chain)` and decides whether to call
`chain.doFilter(...)` (continue) or not (short-circuit, e.g. write a 401 and
return). Spring Boot registers **one** filter with Tomcat, named
`springSecurityFilterChain`. Inside it, `FilterChainProxy` holds your
`SecurityFilterChain` beans and delegates to the internal filter list.

So the real picture is:

```
Tomcat
  └─ springSecurityFilterChain  (FilterChainProxy)
       └─ SecurityFilterChain  ← the bean you return in SecurityConfig
            ├─ DisableEncodeUrlFilter
            ├─ WebAsyncManagerIntegrationFilter
            ├─ SecurityContextHolderFilter      ← loads/clears SecurityContext
            ├─ HeaderWriterFilter
            ├─ LogoutFilter
            ├─ JwtAuthFilter                    ← YOURS (inserted here)
            ├─ UsernamePasswordAuthenticationFilter
            ├─ BasicAuthenticationFilter        ← from .httpBasic(...)
            ├─ RequestCacheAwareFilter
            ├─ SecurityContextHolderAwareRequestFilter
            ├─ AnonymousAuthenticationFilter    ← "anonymous" identity if none set
            ├─ SessionManagementFilter
            ├─ ExceptionTranslationFilter       ← turns exceptions into 401/403
            └─ AuthorizationFilter              ← enforces authorizeHttpRequests
  └─ DispatcherServlet → controller
```

> **Debug tip:** set `logging.level.org.springframework.security=DEBUG` and Spring
> prints this exact list at startup, plus which filter rejected each request.
> Do not skip this — it is the fastest way to debug security in this project.

### 1.2 Where the chain is declared

`auth\SecurityConfig.java:43-56`:

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
    return http
        .csrf(AbstractHttpConfigurer::disable).cors(CorsConfigurer::disable)
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/auth/v1/login", "/auth/v1/refreshToken", "/auth/v1/signup").permitAll()
            .anyRequest().authenticated()
        )
        .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(Customizer.withDefaults())
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .authenticationProvider(authenticationProvider())
        .build();
}
```

`HttpSecurity` is a **builder**. Each call registers a *configurer*; `build()`
runs them all and produces the ordered filter list above. Line by line:

- **`.csrf(disable)`** — CSRF protection defends cookie/session-based browser
  forms. This API is token-based and stateless: no cookie is auto-sent by the
  browser, so there is nothing for CSRF to exploit. Disabling is correct here.
  *(If you ever move JWTs into cookies, turn CSRF back on.)*
- **`.cors(disable)`** — turns off Spring's CORS filter entirely. Fine while the
  API is called from Postman/curl; a browser SPA on a different origin will need
  this re-enabled with a real `CorsConfigurationSource`.
- **`.authorizeHttpRequests(...)`** — configures the final `AuthorizationFilter`.
  Rules are evaluated **top-down, first match wins**, which is why the three
  `permitAll()` paths must be listed *before* `anyRequest().authenticated()`.
  These are exactly the three endpoints that cannot require a token: you have no
  token yet when signing up or logging in, and a refresh call carries a refresh
  token, not a JWT.
- **`.sessionManagement(STATELESS)`** — never create an `HttpSession`, never load
  a `SecurityContext` from one. Every request must re-prove identity from its
  `Authorization` header. This is what makes `JwtAuthFilter` run on *every*
  request rather than only the first.
- **`.httpBasic(withDefaults())`** — adds `BasicAuthenticationFilter`. Not needed
  by the JWT design; it is a convenience left over from testing with basic auth.
  Harmless, but it means a client could still authenticate with
  `Authorization: Basic base64(user:pass)`. Consider dropping it once the JWT
  flow is fully verified.
- **`.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)`**
  — the insertion point. See §2.1.
- **`.authenticationProvider(...)`** — registers the `DaoAuthenticationProvider`
  for this chain (see §3.3).

### 1.3 `SecurityContextHolder` — the thing all of this exists to fill

```java
SecurityContextHolder.getContext().setAuthentication(authToken);
```

`SecurityContextHolder` is a static holder backed by a **`ThreadLocal`**. It
carries a `SecurityContext`, which holds one `Authentication`. Because it is
thread-local, the value set by a filter early in the chain is visible to the
controller, the service layer, and `@PreAuthorize` checks — all running on the
same request thread — without being passed as a parameter.

`SecurityContextHolderFilter` clears it in a `finally` block when the request
ends. That cleanup matters: Tomcat pools threads, and a leaked context would let
the next request run as the previous user.

The `Authentication` object itself:

| Method | Before auth | After auth |
|---|---|---|
| `getPrincipal()` | username `String` | `UserDetails` object |
| `getCredentials()` | raw password | `null` (wiped) |
| `getAuthorities()` | empty | roles from the DB |
| `isAuthenticated()` | `false` | `true` |

Both `TokenController` and `JwtAuthFilter` build a
`UsernamePasswordAuthenticationToken` — but they use **different constructors**,
and that difference *is* the authenticated/unauthenticated distinction. See §3.4.

---

## 2. `JwtAuthFilter` — authenticating every non-login request

Source: `auth\JwtAuthFilter.java`.

### 2.1 Why `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`

`addFilterBefore` only sets **position**, not behaviour. The reference filter is
just a well-known landmark that sits after the context/header/logout plumbing and
before all the authorization machinery. Placing your filter there guarantees:

- the `SecurityContext` has been initialised (so `setAuthentication` sticks), and
- it runs **before** `AuthorizationFilter`, so by the time the rules from
  `authorizeHttpRequests` are evaluated, the identity is already in place.

Get the order wrong — put it after `AuthorizationFilter` — and every protected
request 403s even with a perfect token, because authorization was decided before
anyone read the header.

### 2.2 Why `extends OncePerRequestFilter`

A single HTTP request can pass through the filter chain more than once —
`RequestDispatcher` forwards, `ERROR` dispatches, async re-dispatches.
`OncePerRequestFilter` sets a request attribute the first time it runs and skips
itself afterwards, so you never parse the same token twice or fight with an
already-populated context.

### 2.3 The body, decision by decision

```java
String authHeader = request.getHeader("Authorization");
String token = null, username = null;

if (authHeader != null && authHeader.startsWith("Bearer ")) {
    token = authHeader.substring(7);              // strip "Bearer "
    username = jwtService.extractUsername(token); // ← verifies signature
}

if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
    if (jwtService.validateToken(token, userDetails)) {
        UsernamePasswordAuthenticationToken authenticationToken =
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
    }
}
filterChain.doFilter(request, response);
```

1. **No header, or not `Bearer`** → both locals stay `null`, nothing is set, and
   the request continues *unauthenticated*. The filter never rejects anything
   itself. That is deliberate: `/auth/v1/login` has no token and must still get
   through. Rejection is `AuthorizationFilter`'s job, later.
2. **`extractUsername(token)`** is not a passive read. It routes into
   `JwtService.extractAllClaims` (`services\JwtService.java:40-46`), which calls
   `Jwts.parser().verifyWith(getSignKey())`. A tampered or wrongly-signed token
   throws here — signature verification happens on this line, not in
   `validateToken`.
3. **`getAuthentication() == null`** — do not overwrite an identity another
   filter already established.
4. **`loadUserByUsername`** hits the DB (`UserDetailsServiceImpl:37-48`) so
   authorities are always current — a role revoked in the DB takes effect on the
   next request even though the old JWT is still cryptographically valid. The
   cost is one DB read per request; the benefit is that you are not trusting
   stale claims. (Roles are not in the token at all — `JwtService.generateToken`
   passes an empty claims map, so the DB read is the *only* source of
   authorities.)
5. **`validateToken`** (`JwtService:54-57`) then checks the subject matches the
   loaded user and the token is not expired.
6. **`setDetails(...)`** attaches remote IP and session id — useful for audit
   logs, unused by the authorization logic.
7. **`filterChain.doFilter(...)` is outside every `if`.** It must always run,
   including on a bad token: an invalid token leaves the context empty, and the
   request then fails at `AuthorizationFilter` with a proper 403 instead of
   hanging.

### 2.4 What happens on failure

`JwtService` throws (`ExpiredJwtException`, `SignatureException`, …) rather than
returning `false`. Nothing here catches it, so it propagates out of the filter
chain and Spring Boot's default error handling turns it into a 500. **This is the
main rough edge in the current code.** A production version wraps steps 2–5 in
`try/catch`, logs, leaves the context empty, and calls `doFilter` — letting
`ExceptionTranslationFilter` produce a clean 401.

---

## 3. `AuthenticationManager` — authenticating the login request

### 3.1 The bean

`auth\SecurityConfig.java:67-70`:

```java
@Bean
public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
}
```

`AuthenticationManager` is a one-method interface:

```java
Authentication authenticate(Authentication authentication) throws AuthenticationException;
```

Contract: *"here is an unauthenticated token — either return an authenticated one
or throw."* It never returns `false` and never returns `null` for a failed
attempt; failure is always an `AuthenticationException`.

Spring builds one internally while processing `HttpSecurity`, but that instance
is not automatically a bean you can inject. This method reaches into
`AuthenticationConfiguration` and **exposes the already-built manager as a bean**
so `TokenController` can `@Autowired` it. Without this three-line method, the
login endpoint would not compile-time fail — it would fail at startup with
"no qualifying bean of type AuthenticationManager".

The concrete implementation is `ProviderManager`, which holds a **list of
`AuthenticationProvider`s** and tries each until one supports the token type.

### 3.2 The call site

`controller\TokenController.java:33-46`:

```java
@PostMapping("auth/v1/login")
public ResponseEntity AuthenticateAndGetToken(@RequestBody AuthRequestDTO authRequestDTO) {
    Authentication authentication = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(authRequestDTO.getUsername(), authRequestDTO.getPassword()));
    if (authentication.isAuthenticated()) {
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(authRequestDTO.getUsername());
        return new ResponseEntity<>(JwtResponseDTO.builder()
            .accessToken(jwtService.generateToken(authRequestDTO.getUsername()))
            .token(refreshToken.getToken())
            .build(), HttpStatus.OK);
    }
    return new ResponseEntity<>("Exception in User Service", HttpStatus.INTERNAL_SERVER_ERROR);
}
```

Note this is a **manual** call. `/auth/v1/login` is `permitAll()`, so no filter
authenticated it; the controller drives the manager itself. Note also that the
`else` branch is effectively dead code — a failed authenticate() *throws*
`BadCredentialsException` and never reaches the `if`. That exception escapes the
controller and Spring turns it into a 500; adding
`@ExceptionHandler(AuthenticationException.class)` returning 401 is the natural
next improvement.

### 3.3 What happens inside `authenticate(...)`

```
TokenController
  └─ ProviderManager.authenticate(UsernamePasswordAuthenticationToken[unauthenticated])
       └─ DaoAuthenticationProvider          ← registered in SecurityConfig:59-65
            ├─ userDetailsService.loadUserByUsername("jassi")
            │     └─ UserRepository.findByUsername → UserInfo → new CustomUserDetails(user)
            ├─ passwordEncoder.matches(rawPassword, userDetails.getPassword())
            │     └─ BCryptPasswordEncoder — hashes the raw input with the stored
            │        salt and compares; never decrypts (BCrypt is one-way)
            ├─ checks isAccountNonExpired / isAccountNonLocked /
            │  isCredentialsNonExpired / isEnabled  ← CustomUserDetails:45-63, all true
            └─ returns UsernamePasswordAuthenticationToken[authenticated,
                                                          credentials erased]
```

The provider is assembled in `SecurityConfig:59-65`:

```java
@Bean
public AuthenticationProvider authenticationProvider() {
    DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
    authenticationProvider.setUserDetailsService(userDetailsServiceImpl);
    authenticationProvider.setPasswordEncoder(passwordEncoder);   // BCrypt, from UserConfig
    return authenticationProvider;
}
```

Two collaborators, and that is the whole of "authentication" as a mechanism:

- **`UserDetailsService`** — *"given a username, produce the stored user."*
  `UserDetailsServiceImpl:37-48` reads from `UserRepository` and wraps the entity
  in `CustomUserDetails`. Throws `UsernameNotFoundException` when absent, which
  `DaoAuthenticationProvider` converts to `BadCredentialsException` so callers
  cannot distinguish "no such user" from "wrong password" (user enumeration
  defence).
- **`PasswordEncoder`** — `BCryptPasswordEncoder`, defined once in
  `auth\UserConfig.java:12-15`. The same bean is used on the write side by
  `UserDetailsServiceImpl.signupUser:56`, which encodes before saving. Encode on
  signup, `matches` on login — never compare hashes as strings, and never store
  raw passwords.

Exceptions the provider can raise, all subclasses of `AuthenticationException`:
`BadCredentialsException`, `UsernameNotFoundException`, `DisabledException`,
`LockedException`, `AccountExpiredException`, `CredentialsExpiredException`.

### 3.4 The two constructors — the detail everyone misses

```java
// 2-arg: principal + credentials. isAuthenticated() == false.
// "Here is a claim, please verify it."
new UsernamePasswordAuthenticationToken(username, password);          // TokenController:35

// 3-arg: principal + credentials + authorities. isAuthenticated() == true.
// "This identity is already proven; here are its roles."
new UsernamePasswordAuthenticationToken(userDetails, null, authorities); // JwtAuthFilter:48
```

The 3-arg constructor sets the authenticated flag internally, and it is the only
sanctioned way to do so (`setAuthenticated(true)` throws). This is exactly why
`JwtAuthFilter` never needs an `AuthenticationManager` — the signature check *is*
the proof, so the filter constructs an already-authenticated token directly.

---

## 4. Two full request walkthroughs

### 4.1 `POST /auth/v1/login`

```
1. Tomcat → FilterChainProxy → SecurityFilterChain
2. SecurityContextHolderFilter: STATELESS → empty context
3. JwtAuthFilter: no Authorization header → does nothing, doFilter()
4. AnonymousAuthenticationFilter: sets anonymous authentication
5. AuthorizationFilter: "/auth/v1/login" matches permitAll() → allowed
6. DispatcherServlet → TokenController.AuthenticateAndGetToken
7. authenticationManager.authenticate(2-arg token)
     → ProviderManager → DaoAuthenticationProvider
     → loadUserByUsername → BCrypt matches → authenticated token
8. refreshTokenService.createRefreshToken → UUID row saved, 10-min expiry
9. jwtService.generateToken → HS256 JWT, subject=username, 15-min expiry
10. 200 { accessToken, token }
```

### 4.2 `GET /some-protected-endpoint` with `Authorization: Bearer <jwt>`

```
1-2. as above
3. JwtAuthFilter:
     header starts with "Bearer " → strip prefix
     extractUsername → parser verifies HS256 signature  (throws if tampered)
     context is empty → loadUserByUsername (DB hit, fresh authorities)
     validateToken → subject matches && not expired
     build 3-arg token → SecurityContextHolder.setAuthentication(...)
     doFilter()
4. AnonymousAuthenticationFilter: context already populated → skips
5. AuthorizationFilter: anyRequest().authenticated() → context has an
   authenticated non-anonymous token → allowed
6. Controller runs; SecurityContextHolder still holds the user for
   @PreAuthorize and for auditing in the service layer
7. Response written; SecurityContextHolderFilter clears the ThreadLocal
```

Bad-token variant: step 3 throws, or `validateToken` returns false and the
context stays empty → step 5 denies → `ExceptionTranslationFilter` →
`AccessDeniedException` → 403 (401 once §2.4's `try/catch` is added).

---

## 5. The supporting cast, in one table

| Type | This project's implementation | Job |
|---|---|---|
| `SecurityFilterChain` | `SecurityConfig.securityFilterChain` | Declares the ordered filter list + URL rules |
| `AuthenticationManager` | `ProviderManager` (exposed at `SecurityConfig:68`) | Entry point: verify an `Authentication` or throw |
| `AuthenticationProvider` | `DaoAuthenticationProvider` (`SecurityConfig:59`) | The actual username/password check |
| `UserDetailsService` | `UserDetailsServiceImpl` | Username → stored user, from the DB |
| `UserDetails` | `CustomUserDetails` | Adapter: your `UserInfo` entity → what Security expects |
| `PasswordEncoder` | `BCryptPasswordEncoder` (`UserConfig:13`) | One-way hash + `matches` |
| `Authentication` | `UsernamePasswordAuthenticationToken` | The identity object carried in the context |
| `SecurityContextHolder` | (framework) | ThreadLocal holding the current `Authentication` |
| `OncePerRequestFilter` | `JwtAuthFilter` | Per-request bearer-token authentication |
| — | `JwtService` | Sign, parse, verify, expire JWTs |
| — | `RefreshTokenService` | Long-lived opaque UUID tokens stored in the DB |

### Why `@EnableMethodSecurity` (`SecurityConfig:25`)

URL rules in `authorizeHttpRequests` are coarse. `@EnableMethodSecurity` turns on
the AOP proxy that honours `@PreAuthorize("hasAuthority('ADMIN')")` and friends
on service/controller methods, checking against the same `Authentication` in the
context. The authorities come from `CustomUserDetails.getAuthorities()`, which
uppercases role names (`CustomUserDetails:25`) — so match on `'ADMIN'`, and note
that `hasRole('ADMIN')` would look for `ROLE_ADMIN` and fail with this mapping.
Prefer `hasAuthority` here, or add the `ROLE_` prefix when building authorities.

### Why two token types

| | Access token (JWT) | Refresh token |
|---|---|---|
| Form | Signed JWT, self-describing | Opaque UUID |
| Storage | Client only — server keeps nothing | Row in `refresh_token` table |
| Lifetime | 15 min (`JwtService:23`) | 10 min (`RefreshTokenService:28`) |
| Verified by | Signature check, no DB | DB lookup + `verifyExpiration` |
| Revocable | No — valid until it expires | Yes — delete the row |

Short JWT lifetime bounds the damage from a stolen access token, since it cannot
be revoked. The refresh token *is* revocable, so it can safely live longer — and
in a real deployment it should (days, not the 10 minutes currently configured;
right now the refresh token expires before it is much use, which is worth fixing).

---

## 6. Known rough edges in the current code

Fair-warning list — these are learning-project shortcuts, not blockers:

1. **`JwtService.SECRET` is hardcoded** (`JwtService:20`) and committed. Move to
   `application.properties` / an env var via `@Value`.
2. **No exception handling in `JwtAuthFilter`** (§2.4) — expired tokens surface
   as 500 instead of 401.
3. **No `@ExceptionHandler` for `AuthenticationException`** in `TokenController`
   — bad credentials also produce a 500 (§3.2).
4. **`CustomUserDetails extends UserInfo`** (`CustomUserDetails:13`) — an
   `@Entity` subclass used as a DTO. It works because the fields are shadowed,
   but it confuses JPA. Prefer plain composition: hold a `UserInfo` field.
5. **Refresh token TTL (10 min) is shorter than useful** — see §5.
6. **`.httpBasic()` is still enabled** (`SecurityConfig:52`) alongside JWT; drop
   it once the token flow is verified.
7. **`@Autowired` on `final` constructor-injected fields** (`SecurityConfig:29`,
   `JwtAuthFilter:27`) is redundant — Lombok's `@AllArgsConstructor` /
   `@RequiredArgsConstructor` already gives Spring a single constructor to use.

---

## 7. 🎯 Checkpoint questions

Answer without scrolling up:

1. Why does `JwtAuthFilter` call `filterChain.doFilter(...)` even when the token
   is invalid?
2. What breaks if `addFilterBefore` is changed to `addFilterAfter(jwtAuthFilter,
   AuthorizationFilter.class)`?
3. Why does `TokenController` need an `AuthenticationManager` while
   `JwtAuthFilter` does not?
4. What is the difference between the 2-arg and 3-arg
   `UsernamePasswordAuthenticationToken` constructors, and where is each used?
5. `SessionCreationPolicy.STATELESS` is removed. What behaviour changes?
6. Where exactly is the JWT's signature verified — name the file and method.
7. Why must the three `permitAll()` matchers be listed before
   `anyRequest().authenticated()`?
8. Where does the password hash get created, and where does it get compared?

---

## 8. Try it yourself

```bash
# 1. Sign up
curl -X POST http://localhost:8080/auth/v1/signup \
  -H "Content-Type: application/json" \
  -d '{"username":"jassi","password":"secret"}'

# 2. Log in → copy accessToken
curl -X POST http://localhost:8080/auth/v1/login \
  -H "Content-Type: application/json" \
  -d '{"username":"jassi","password":"secret"}'

# 3. Call a protected endpoint with the token
curl http://localhost:8080/ping -H "Authorization: Bearer <accessToken>"

# 4. Same call with no token, and with a tampered token — watch the DEBUG log
#    to see which filter rejects each one
curl -i http://localhost:8080/ping
curl -i http://localhost:8080/ping -H "Authorization: Bearer garbage.token.here"
```

Set breakpoints at `JwtAuthFilter:37` and `TokenController:35`, run both flows,
and step through. Reading §4 is useful; watching the two stack traces diverge is
what makes it stick.
