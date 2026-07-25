# Chapter 3 — Building the Auth Service Yourself (Spring Boot + Maven)

> **Scope:** A hands-on, milestone-by-milestone guide for implementing the Auth
> Service from [Chapter 2](./chapter-2-auth-service.md) **by hand**, in Spring Boot
> with Maven, plus a reference section explaining every concept you'll touch.
>
> **Reference implementation:** a Gradle version of this service exists at
> `C:\Users\tanmay.mahato\Downloads\app-20260724T170252Z-1-001\app\src`.
> Treat it as a *hint sheet, not a template* — §9 lists the bugs in it you should
> not copy.

---

## 0. How to use this chapter

The point is to learn Spring Security, not to finish fast. So:

1. **Never paste the reference code.** Read §2 (concept map) for a milestone,
   write the code from your own understanding, *then* peek at the reference to
   compare. The gap between your version and theirs is where the learning is.
2. **Run after every milestone.** Each milestone below ends with a
   **✅ Verify** block — an actual command whose output you must see before
   moving on. A broken Spring Security config fails in confusing ways; small
   increments keep the blast radius one file wide.
3. **Answer the 🎯 Checkpoint questions** out loud before continuing. If you
   can't, you copied instead of learned — go back.
4. **Keep `logging.level.org.springframework.security=DEBUG` on** for the whole
   build. Spring Security logs the exact filter chain it constructed and which
   filter rejected a request. This is the single highest-value debugging habit
   for this project.

Estimated pace: milestones 1–5 in a first sitting, 6–9 in a second, 10–14 as
polish. Roughly 12–18 hours total if you're genuinely writing it yourself.

---

## 1. Where you are now

Your project already has:

```
D:\POC\Jassi\ExpenseTracker
├── pom.xml                                   ← deps already chosen (web, jpa,
│                                                security, oauth2-rs, validation,
│                                                jjwt 0.12.6, lombok, postgres, h2)
├── notes\
│   ├── chapter-1-about.md
│   ├── chapter-2-auth-service.md             ← the design you're implementing
│   └── chapter-3-implementation-guide.md     ← this file
└── src\
    ├── main\java\com\jassi\expensetracker\ExpenseTrackerApplication.java
    ├── main\resources\application.properties
    └── test\java\com\jassi\expensetracker\ExpenseTrackerApplicationTests.java
```

So the skeleton and dependency choices are done. Everything from §3 onward is
code **you** write.

### Target package layout

Build toward this. Package-by-layer is what the reference uses and what most
Spring tutorials assume, so it keeps the two comparable:

```
com.jassi.expensetracker
├── ExpenseTrackerApplication.java
├── auth\
│   ├── SecurityConfig.java          @Configuration — the filter chain & security beans
│   ├── JwtAuthFilter.java           OncePerRequestFilter — validates the bearer token
│   └── PasswordConfig.java          @Bean PasswordEncoder (keep separate — see §6.4)
├── controller\
│   ├── AuthController.java          /auth/v1/signup
│   ├── TokenController.java         /auth/v1/login, /auth/v1/refreshToken
│   └── PingController.java          /ping  (protected — proves the filter works)
├── entity\
│   ├── UserInfo.java                @Entity users
│   ├── UserRole.java                @Entity roles
│   └── RefreshToken.java            @Entity tokens
├── repository\
│   ├── UserRepository.java          extends JpaRepository<UserInfo, String>
│   └── RefreshTokenRepository.java  extends JpaRepository<RefreshToken, Long>
├── dto\
│   ├── SignupRequest.java           request DTOs (records or @Data classes)
│   ├── AuthRequest.java
│   ├── RefreshTokenRequest.java
│   └── JwtResponse.java             response DTO
├── service\
│   ├── UserDetailsServiceImpl.java  implements UserDetailsService + signup
│   ├── CustomUserDetails.java       implements UserDetails (adapter)
│   ├── JwtService.java              generate / parse / validate JWTs
│   └── RefreshTokenService.java     create / verify / rotate refresh tokens
└── exception\
    ├── GlobalExceptionHandler.java  @RestControllerAdvice
    └── (TokenRefreshException, UserAlreadyExistsException, ...)
```

**Why `entity` and `repository` sit under the same root package as the
application class:** Spring Boot's auto-configuration scans the package of the
`@SpringBootApplication` class *and everything below it*. Keep this layout and
you never need `@ComponentScan`, `@EnableJpaRepositories`, or `@EntityScan` — a
lesson the reference app learned the hard way (§9.1).

---

## 2. Concept map — everything this project teaches

Tick these off as you meet them. §6–§8 explain each in depth.

**Spring core**
- [ ] IoC container, beans, `@Component` / `@Service` / `@Repository` / `@Configuration`
- [ ] `@Bean` factory methods vs. stereotype-annotation scanning
- [ ] Dependency injection: constructor injection (do this) vs. field `@Autowired` (don't)
- [ ] Component scanning and why package placement matters
- [ ] `application.properties`, `@Value`, `@ConfigurationProperties`, profiles
- [ ] Bean lifecycle & circular-dependency errors (you *will* hit one in §3.6)

**Spring MVC**
- [ ] `@RestController`, `@PostMapping`, `@RequestBody`, `ResponseEntity<T>`
- [ ] DTOs vs. entities, and why never to expose an entity over HTTP
- [ ] Bean Validation: `@Valid`, `@NotBlank`, `@Email`, `@Size`
- [ ] `@RestControllerAdvice` + `@ExceptionHandler` for uniform error responses

**Spring Data JPA**
- [ ] `@Entity`, `@Id`, `@GeneratedValue` strategies, `@Table`, `@Column`
- [ ] Relationships: `@OneToMany` / `@ManyToOne` / `@ManyToMany`, owning side, `@JoinColumn`, `@JoinTable`
- [ ] `FetchType.LAZY` vs `EAGER`, the N+1 problem, `LazyInitializationException`
- [ ] `JpaRepository` derived query methods (`findByUsername`)
- [ ] `@Transactional` and transaction boundaries
- [ ] `ddl-auto` modes, and why `create` is a trap (§9.7)

**Spring Security — the heart of this project**
- [ ] The servlet `FilterChainProxy` and `SecurityFilterChain`
- [ ] `SecurityContextHolder`, `SecurityContext`, `Authentication`, `Principal`
- [ ] `AuthenticationManager` → `ProviderManager` → `DaoAuthenticationProvider`
- [ ] `UserDetailsService` / `UserDetails` / `GrantedAuthority`
- [ ] `PasswordEncoder`, BCrypt, salting, work factor
- [ ] `UsernamePasswordAuthenticationToken` — as a *request* vs. as a *result*
- [ ] Custom `OncePerRequestFilter` and where to place it in the chain
- [ ] `SessionCreationPolicy.STATELESS` and why JWT needs it
- [ ] CSRF: what it is, why disabling it is correct *here specifically*
- [ ] `AuthenticationEntryPoint` vs `AccessDeniedHandler` (401 vs 403)
- [ ] Method security: `@EnableMethodSecurity`, `@PreAuthorize`, roles vs. authorities

**JWT & tokens**
- [ ] JWS structure: `header.payload.signature`, Base64URL
- [ ] Symmetric (HMAC-SHA256) vs. asymmetric (RSA/EC) signing — and when each wins
- [ ] Standard claims: `sub`, `iat`, `exp`, `iss`, `aud`, `jti`
- [ ] jjwt 0.12.x builder/parser API (**changed from 0.11** — §7.3)
- [ ] Access vs. refresh token lifetimes; refresh-token rotation & reuse detection
- [ ] Why a JWT can't be "logged out" without server-side state

**Production concerns**
- [ ] Secrets in env vars, never in source
- [ ] Structured error responses that don't leak whether a username exists
- [ ] Testing: `@WebMvcTest` + `spring-security-test`, `@DataJpaTest`, `@SpringBootTest`
- [ ] Reliable event publishing (transactional outbox) for the User Service event

---

## 3. The milestones

### M0 — Boot and see it run

**Goal:** confirm the skeleton starts before adding anything.

Run `mvnw spring-boot:run`. It will most likely **fail** with
`Failed to configure a DataSource: 'url' attribute is not specified` — because
`spring-boot-starter-data-jpa` is on the classpath with no database configured.
That's your first real lesson: *starters auto-configure themselves, and
auto-configuration fails loudly when it can't.*

Fix it the cheap way for now — add H2 at `runtime` scope temporarily, or point
`spring.datasource.url` at a real Postgres. Then note the second thing that
happens: `spring-boot-starter-security` on the classpath means **every endpoint
is already locked down**, and Boot printed a generated password
(`Using generated security password: ...`) at startup. You haven't written a
single line of security config and you already have HTTP Basic auth on
everything. Understand *why* before you turn it off.

✅ **Verify:** `curl -i localhost:8080/` returns `401 Unauthorized`.

🎯 **Checkpoint:** Which auto-configuration class created that default filter
chain? (Search the startup log with `--debug` for the auto-configuration report.)

---

### M1 — Database and configuration

**Goal:** a running database and an `application.properties` you understand line
by line.

Decide: Postgres in Docker (closest to production, matches your `pom.xml`) or H2
in-memory (zero setup, resets every run). Recommendation: **Postgres via Docker**
for the main profile, H2 for tests — that's what your `pom.xml` already implies.

```
docker run --name expense-pg -e POSTGRES_PASSWORD=secret -e POSTGRES_DB=authservice -p 5432:5432 -d postgres:16
```

Then write `application.properties` yourself. Every property should be one you
can explain:

- `spring.datasource.url` / `username` / `password` — use `${ENV_VAR:default}`
  placeholder syntax so nothing secret is hardcoded.
- `spring.jpa.hibernate.ddl-auto` — use `update` while learning, `validate` in
  anything resembling production. **Never `create`** (§9.7).
- `spring.jpa.show-sql=true` + `spring.jpa.properties.hibernate.format_sql=true` —
  you want to *see* the SQL JPA generates; that's half the JPA learning.
- `server.port` — pick one (9898 like the reference, or 8080).
- `logging.level.org.springframework.security=DEBUG`.
- `jwt.secret` / `jwt.access-token-expiration-ms` / `jwt.refresh-token-expiration-ms` —
  your own custom properties, read via `@Value` or `@ConfigurationProperties`.

✅ **Verify:** app starts, log shows a HikariCP pool connecting to Postgres.

🎯 **Checkpoint:** What's the difference between `ddl-auto=update` and a real
migration tool (Flyway/Liquibase)? Why would a production team never ship
`update`?

---

### M2 — Entities and repositories

**Goal:** the data model from Chapter 2 §6, expressed in JPA.

Write `UserInfo`, `UserRole`, `RefreshToken`. Points to think hard about:

- **ID type and generation.** The reference uses a `String` UUID for the user
  and `IDENTITY` for the token. Either is fine — but the repository's second
  type parameter **must match the `@Id` field's type** (the reference gets this
  wrong, §9.4). Decide deliberately: UUID (opaque, safe to expose, no
  DB round-trip to allocate) vs. `IDENTITY` bigint (compact, ordered, faster
  index).
- **The user↔role relationship.** `@ManyToMany` with a `@JoinTable` gives you a
  `users_roles` link table. Note Chapter 2's ER diagram models roles as a simple
  `roles` column on the user — the reference went further with a real table.
  Either is defensible; implement the join table, since that's where the JPA
  learning is.
- **The user↔token relationship.** Chapter 2 says *one user → many refresh
  tokens* (multiple devices). So it's `@ManyToOne` on `RefreshToken`
  (owning side, holds the FK) — **not** the `@OneToOne` the reference used
  (§9.2). Write `@JoinColumn(name = "user_id")` and be sure you can say what
  `name` vs. `referencedColumnName` mean.
- **Fetch types.** Roles need to be available when building `UserDetails`, and
  that happens outside a transaction in the JWT filter — so either `EAGER`, or
  `LAZY` plus a `@Query` with `JOIN FETCH`. Understand the tradeoff before you
  pick; `EAGER` on a `@ManyToMany` is a classic N+1 source.
- **Indexes.** Chapter 2 §6 demands unique indexes on `username` and `token`.
  Express that: `@Column(unique = true, nullable = false)` or
  `@Table(indexes = @Index(...))`. This is the concrete answer to the
  "avoid long-running queries" NFR.
- **Lombok on entities.** `@Data` generates `equals`/`hashCode` over *all*
  fields, including relationships — which causes infinite recursion on
  bidirectional links and breaks JPA identity semantics. Prefer
  `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`, and write
  `equals`/`hashCode` on the ID only (or omit them). Also never put
  `@ToString` over a relationship field.

Then the repositories — two interfaces, no implementation:

```java
public interface UserRepository extends JpaRepository<UserInfo, String> {
    Optional<UserInfo> findByUsername(String username);   // Optional, not null
    boolean existsByUsername(String username);
}
```

Returning `Optional` rather than `null` is a real improvement over the reference
and makes the service layer cleaner.

✅ **Verify:** start the app, then inspect Postgres — `\dt` should show `users`,
`roles`, `users_roles`, `tokens` with the FK and unique constraints you declared.

🎯 **Checkpoint:** Where does the `findByUsername` implementation come from?
(Answer involves a JDK dynamic proxy and Spring Data's query-derivation parser.)

---

### M3 — Password encoding and signup (no security config yet)

**Goal:** create a user with a BCrypt-hashed password.

Write `PasswordConfig` with a single `@Bean PasswordEncoder` returning
`new BCryptPasswordEncoder()`. **Keep this in its own `@Configuration` class**,
separate from `SecurityConfig` — that separation is what prevents the circular
dependency in §3.6/§9.5, and understanding *why* is worth more than the file.

Write `UserDetailsServiceImpl.signupUser(...)`. Correct order of operations:

1. Check existence **first** (`existsByUsername`) → fail fast with a clear error.
2. *Then* encode the password.
3. Build and save the entity, assigning a default role (e.g. `ROLE_USER`).
4. Return the created user / a DTO — never the entity with its hash in it.

Mark it `@Transactional`.

Write `AuthController.signup` accepting a `SignupRequest` DTO with `@Valid`
(`@NotBlank` username, `@Size(min = 8)` password, `@Email`). Note: your DTO is a
**separate class**, it does not extend the entity (§9.3).

✅ **Verify:**
```
curl -u user:<generated-password> -X POST localhost:9898/auth/v1/signup \
  -H 'Content-Type: application/json' \
  -d '{"username":"jassi","password":"password123","email":"j@example.com"}'
```
Then `SELECT username, password FROM users;` — the password column must start
with `$2a$` or `$2b$` and be ~60 chars. Sign up a *second* user with the *same*
password and confirm the two hashes **differ** — that's the per-user salt.

🎯 **Checkpoint:** Why is BCrypt deliberately slow, and what does its cost factor
(default 10 = 2¹⁰ rounds) control? Why can't you look up a user *by* password hash?

---

### M4 — `UserDetailsService` and `UserDetails`

**Goal:** teach Spring Security how to load *your* user.

Implement `loadUserByUsername(String)`:
- Return a `UserDetails`, or throw `UsernameNotFoundException` — never return `null`.
- Map your `UserRole` entities to `GrantedAuthority` (`SimpleGrantedAuthority`).
- Convention: authority names starting with `ROLE_` participate in `hasRole()`
  checks; anything else only works with `hasAuthority()`. Pick one convention and
  be consistent — this trips up almost everyone once.

For the `UserDetails` implementation you have three options; know all three:

1. Return Spring's built-in `org.springframework.security.core.userdetails.User`
   (simplest, no custom class).
2. Write `CustomUserDetails implements UserDetails` wrapping your entity
   (best — an adapter, lets you carry `userId` into the security context).
3. Make the entity itself implement `UserDetails` (common in tutorials, couples
   your persistence model to your security model — avoid).

Take option 2, and note the reference's version *both* extends the entity and
implements the interface, which is the worst of all worlds (§9.3).

✅ **Verify:** Temporarily set `spring.security.user.name` aside and log in with
your DB user via HTTP Basic:
`curl -u jassi:password123 localhost:9898/ping` → should now authenticate
against the database, not the generated password. Watch the DEBUG log show
`DaoAuthenticationProvider` calling your service.

🎯 **Checkpoint:** Trace the call: who invokes `loadUserByUsername`, and who
compares the submitted password to the stored hash? (Neither is your code.)

---

### M5 — `SecurityConfig`: your first real filter chain

**Goal:** replace Boot's default security with your own, still without JWT.

Write a `@Configuration` exposing `@Bean SecurityFilterChain filterChain(HttpSecurity http)`:

```java
http.csrf(AbstractHttpConfigurer::disable)
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/auth/v1/signup", "/auth/v1/login", "/auth/v1/refreshToken").permitAll()
        .anyRequest().authenticated())
    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
return http.build();
```

Concepts to actually absorb here, not just type:

- **Order matters** in `authorizeHttpRequests` — first match wins, so
  `anyRequest()` must be last. A misordered rule is how endpoints accidentally
  go public.
- **`permitAll()` still runs the filter chain**; it just doesn't *require*
  authentication. That distinction matters in M7.
- **CSRF disabled:** correct *only because* this is a stateless, token-in-header
  API — no cookie is auto-attached by the browser, so there's nothing to forge.
  If you ever put the JWT in a cookie, CSRF protection comes back on. Be able to
  explain the attack.
- **`STATELESS`:** Spring won't create or read an `HttpSession`, so the
  `SecurityContext` is discarded after each request. This is what makes the JWT
  the *only* source of identity — and why your filter must repopulate the
  context on every single request.

✅ **Verify:** `/auth/v1/signup` works with **no** credentials now; `/ping`
returns 401. The startup DEBUG log prints your filter chain — read the whole
list and identify at least six filters by name.

🎯 **Checkpoint:** What's the difference between `permitAll()`, `anonymous()`,
and adding a path to `WebSecurity#ignoring()`?

---

### M6 — `JwtService`

**Goal:** mint and verify signed tokens.

Methods: `generateToken(UserDetails)`, `extractUsername(String)`,
`extractExpiration(String)`, generic `extractClaim(token, Function<Claims,T>)`,
`isTokenExpired`, `validateToken(token, userDetails)`.

⚠️ **Your `pom.xml` pins jjwt 0.12.6, whose API differs from the reference's
code.** The reference calls `setClaims`, `setSubject`, `parser().setSigningKey()`,
`parseClaimsJws().getBody()` — all deprecated/removed in 0.12.x. The current API:

```java
Jwts.builder()
    .claims(extraClaims)
    .subject(username)
    .issuedAt(new Date())
    .expiration(new Date(System.currentTimeMillis() + accessTokenExpirationMs))
    .signWith(signingKey())          // algorithm inferred from key size
    .compact();

Jwts.parser()
    .verifyWith(signingKey())        // SecretKey, not Key
    .build()
    .parseSignedClaims(token)
    .getPayload();
```

Copying the reference verbatim here will not compile — good, that forces you to
read the jjwt docs, which is the point.

Other decisions to make consciously:

- **The secret comes from configuration** (`@Value("${jwt.secret}")` backed by an
  env var), never a `public static final String` in source (§9.6). HS256 needs
  ≥256 bits of key material; generate with
  `openssl rand -base64 32`.
- **Expiry:** the reference uses **1 minute**, which is a debugging value. Use
  15 minutes for access tokens, 7 days for refresh.
- **Claims:** put `sub` (username) and roles in. Never put anything secret in —
  the payload is Base64, not encrypted. Paste a generated token into
  https://jwt.io and *look at your own claims* — that experience alone teaches
  the "encoded ≠ encrypted" lesson permanently.
- **Handle parse failures.** `parseSignedClaims` throws
  `ExpiredJwtException`, `SignatureException`, `MalformedJwtException`.
  Catching these is what separates a 401 from a 500.

✅ **Verify:** a unit test (no Spring context) that generates a token and asserts
the round-trip; plus one that tampers with a character in the payload segment and
asserts a `SignatureException`. That second test is the whole point of signing.

🎯 **Checkpoint:** Why does HS256 mean any service that can *verify* a token can
also *forge* one — and what does RS256 change about that?

---

### M7 — `JwtAuthFilter`

**Goal:** turn a bearer token into an authenticated `SecurityContext`.

Extend `OncePerRequestFilter` (not `GenericFilterBean`: the "once per request"
guarantee matters because forwards/includes re-enter the chain).

The algorithm:

1. Read the `Authorization` header; if absent or not `Bearer `-prefixed,
   `filterChain.doFilter(...)` and return — **do not reject**. Rejecting is the
   authorization layer's job, and public endpoints must pass through untouched.
2. Extract the username from the token.
3. If username is non-null **and** `SecurityContextHolder.getContext()
   .getAuthentication() == null` (don't clobber an existing authentication):
   load `UserDetails`, validate the token against it.
4. On success, build `new UsernamePasswordAuthenticationToken(userDetails, null,
   userDetails.getAuthorities())`, set `WebAuthenticationDetailsSource` details,
   and put it in the `SecurityContextHolder`.
5. Always call `filterChain.doFilter(request, response)` at the end — exactly
   once, on every path. Forgetting this hangs the request; calling it twice
   throws.
6. Wrap token parsing in try/catch so an expired/garbage token becomes a clean
   401, not a stack trace.

Then register it: `.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)`.

**The three-argument constructor is the crux of this whole project.** The
two-arg `UsernamePasswordAuthenticationToken(principal, credentials)` builds an
*unauthenticated request* object; the three-arg version (with authorities) marks
it *authenticated*. Same class, two roles, and it's the most commonly
misunderstood thing in Spring Security. Read the constructor source.

✅ **Verify:** login → copy the access token →
`curl -H "Authorization: Bearer <token>" localhost:9898/ping` returns 200; the
same call with the last character changed returns 401; and after the token
expires, 401 again.

🎯 **Checkpoint:** Why check `getAuthentication() == null` first? And with
`STATELESS` sessions, where does the context get cleared between requests?

---

### M8 — Login via `AuthenticationManager`

**Goal:** `/auth/v1/login` exchanging credentials for tokens.

Expose `@Bean AuthenticationManager authenticationManager(AuthenticationConfiguration c)`
returning `c.getAuthenticationManager()`, and a `DaoAuthenticationProvider`
wired with your `UserDetailsService` and `PasswordEncoder`.

In the controller:

```java
Authentication auth = authenticationManager.authenticate(
    new UsernamePasswordAuthenticationToken(req.username(), req.password()));
```

Understand that this call either **returns an authenticated token or throws** —
it never returns `authentication.isAuthenticated() == false`. The reference's
`if (authentication.isAuthenticated()) ... else 500` is dead code (§9.8): the
failure path is a thrown `BadCredentialsException`, which you handle in your
`@RestControllerAdvice` as a **401** with a deliberately vague message
("Invalid username or password") — never "user not found", which is a username
enumeration oracle.

Then mint the access token + refresh token and return `JwtResponse`.

✅ **Verify:** correct credentials → 200 with two tokens. Wrong password → 401
with the generic message. Nonexistent user → **the same** 401, same message,
similar latency.

🎯 **Checkpoint:** Follow the chain `AuthenticationManager` → `ProviderManager`
→ `DaoAuthenticationProvider` → `UserDetailsService` + `PasswordEncoder.matches`.
Which link would you replace to add LDAP or OAuth login?

---

### M9 — Refresh tokens

**Goal:** `/auth/v1/refreshToken` — a new access token without re-login.

`RefreshTokenService`: `createRefreshToken(username)` (opaque
`UUID.randomUUID()`, expiry from config, persisted), `findByToken`,
`verifyExpiration` (delete + throw if past expiry).

The controller reads the token from the body, verifies it, and mints a new
access token.

Beyond the reference, implement **rotation** — it's a small change and the
single most important refresh-token security property:

- On each refresh, **delete/invalidate the old refresh token and issue a new
  one**. Return both new tokens.
- If a refresh token that was already used is presented again, that means it
  leaked: **revoke the entire token family for that user**, forcing re-login.

Also add `/auth/v1/logout` that deletes the refresh token. Then reason about the
uncomfortable truth: the *access* token stays valid until it expires, because
nothing checks a database on that path. That's the cost of stateless auth —
short expiry is the mitigation. Being able to articulate this tradeoff is a
senior-level answer in interviews.

✅ **Verify:** wait past access-token expiry → protected call 401s → refresh →
new access token works. Then replay the **old** refresh token and confirm it's
rejected.

🎯 **Checkpoint:** Why is the refresh token opaque (a random UUID) rather than
another JWT?

---

### M10 — Roles, authorities, and method security

**Goal:** authorization, not just authentication.

- Seed `ROLE_USER` / `ROLE_ADMIN` rows (a `CommandLineRunner` is fine for now).
- Add `@EnableMethodSecurity` and put `@PreAuthorize("hasRole('ADMIN')")` on an
  admin-only endpoint.
- Include roles as a JWT claim and check that `hasRole` works off the
  authorities your filter populated.
- Try `@PreAuthorize("#username == authentication.name")` to see SpEL access the
  security context.

✅ **Verify:** a `ROLE_USER` token gets **403** (not 401) on the admin endpoint.
Understand precisely why it's 403: you *are* authenticated, you're just not
authorized.

🎯 **Checkpoint:** Why does `hasRole('ADMIN')` match the authority `ROLE_ADMIN`?
Where is that prefix added?

---

### M11 — Error handling that doesn't leak

**Goal:** consistent, safe error responses.

- `@RestControllerAdvice` with handlers for `BadCredentialsException` (401),
  `UsernameNotFoundException` (401 — *same shape* as bad credentials),
  `MethodArgumentNotValidException` (400 with field errors),
  your `TokenRefreshException` (401), and a catch-all `Exception` (500 with a
  correlation id and **no** stack trace in the body).
- Add a custom `AuthenticationEntryPoint` (401 JSON) and `AccessDeniedHandler`
  (403 JSON) to `SecurityConfig`. Without these, filter-level rejections bypass
  `@RestControllerAdvice` entirely and return Boot's default HTML/whitelabel
  error — a genuinely surprising gap worth experiencing once.
- Replace every `catch (Exception ex) { return 500 }` you wrote earlier
  (the reference does this in `signup`, §9.9). Swallowing exceptions turns a
  duplicate-username 400 into an opaque 500.

✅ **Verify:** every failure mode returns JSON with a consistent shape, and no
response body ever contains a stack trace, SQL, or a class name.

---

### M12 — Tests

**Goal:** prove it works without curl.

- `@DataJpaTest` (H2 — already in your `pom.xml` at `test` scope) for
  repositories: unique constraints, `findByUsername`.
- Plain JUnit for `JwtService`: round-trip, expiry, tampering.
- `@WebMvcTest(AuthController.class)` with `@MockBean` services, using
  `spring-security-test`'s `@WithMockUser` and
  `SecurityMockMvcRequestPostProcessors` — note you must `@Import` your
  `SecurityConfig` for the filter chain to apply in a slice test.
- One `@SpringBootTest` end-to-end: signup → login → protected call → refresh.

✅ **Verify:** `mvnw test` green.

---

### M13 — The fault-tolerant User Service event

**Goal:** satisfy Chapter 2's "event published to User Service is recoverable and
fault tolerant."

The reference has `// pushEventToQueue` and stops. Do it properly, and
understand *why* the naive version is broken: if you `save(user)` and then
publish to a queue, a crash between the two loses the event; if you publish
first and the DB write fails, you've announced a user that doesn't exist. There
is no ordering of two systems that's atomic.

The **transactional outbox** fixes it: in the *same* database transaction as the
user insert, write a row to an `outbox_event` table. A separate poller (or CDC)
reads unpublished rows and pushes them to the queue, marking them sent. Atomic
by construction, retryable, at-least-once — so make the consumer idempotent.

Implement the outbox table + a `@Scheduled` poller. Even logging instead of a
real broker is fine; the pattern is the lesson.

🎯 **Checkpoint:** Why is at-least-once delivery the practical target rather than
exactly-once?

---

### M14 — Production hardening (stretch)

- Secrets via env vars / Spring Cloud Config; nothing sensitive in git.
- HTTPS only (Chapter 2 §2); `server.ssl.*` or TLS terminated at the gateway.
- Rate-limit `/login` and `/signup` (Bucket4j) — brute-force defense.
- Actuator health/metrics, with the endpoints themselves secured.
- Swap `ddl-auto` for **Flyway** migrations.
- Structured JSON logging with a request correlation id.
- Consider RS256 so the gateway can verify tokens with only the public key —
  this is where your `spring-boot-starter-oauth2-resource-server` dependency
  finally earns its place.

---

## 4. Suggested order of file creation

If you want a strict sequence to follow:

```
 1. application.properties          (M1)
 2. entity/UserInfo, UserRole, RefreshToken   (M2)
 3. repository/UserRepository, RefreshTokenRepository  (M2)
 4. auth/PasswordConfig             (M3)
 5. dto/SignupRequest, JwtResponse  (M3)
 6. service/UserDetailsServiceImpl  (M3 signup, M4 loadUserByUsername)
 7. service/CustomUserDetails       (M4)
 8. controller/AuthController       (M3)
 9. auth/SecurityConfig             (M5, extended in M7/M8/M11)
10. controller/PingController       (M5)
11. service/JwtService              (M6)
12. auth/JwtAuthFilter              (M7)
13. dto/AuthRequest                 (M8)
14. controller/TokenController      (M8, M9)
15. service/RefreshTokenService     (M9)
16. dto/RefreshTokenRequest         (M9)
17. exception/GlobalExceptionHandler (M11)
18. test/**                         (M12)
19. outbox + poller                 (M13)
```

---

## 5. Concept deep-dive: how a request actually flows

```
HTTP request
   │
   ▼
Servlet container (Tomcat)
   │
   ▼
FilterChainProxy  ("springSecurityFilterChain" delegate)
   │  picks the first SecurityFilterChain whose matcher matches
   ▼
┌──────────────────── the chain, in order ────────────────────┐
│ DisableEncodeUrlFilter                                       │
│ WebAsyncManagerIntegrationFilter                             │
│ SecurityContextHolderFilter    ← loads/clears SecurityContext│
│ HeaderWriterFilter                                           │
│ LogoutFilter                                                 │
│ ► JwtAuthFilter (yours)        ← you inserted it here        │
│ UsernamePasswordAuthenticationFilter                         │
│ RequestCacheAwareFilter                                      │
│ SecurityContextHolderAwareRequestFilter                      │
│ AnonymousAuthenticationFilter  ← anonymous token if none set │
│ ExceptionTranslationFilter     ← turns exceptions into 401/403│
│ AuthorizationFilter            ← enforces authorizeHttpRequests│
└──────────────────────────────────────────────────────────────┘
   │
   ▼
DispatcherServlet → your @RestController
```

Read this diagram again after M7 — the placement of `JwtAuthFilter` **before**
`AuthorizationFilter` is what makes authorization see your authentication, and
its placement before `AnonymousAuthenticationFilter` is why the null-check in
step 3 works.

### The authentication flow (login)

```
TokenController
   └─► AuthenticationManager.authenticate(UsernamePasswordAuthenticationToken)   ← unauthenticated
         └─► ProviderManager  (iterates providers that support() the token type)
               └─► DaoAuthenticationProvider
                     ├─► UserDetailsService.loadUserByUsername()  → your DB
                     ├─► PasswordEncoder.matches(raw, storedHash)
                     └─► returns UsernamePasswordAuthenticationToken             ← authenticated
                          (or throws BadCredentialsException)
```

### The authorization flow (every subsequent request)

```
JwtAuthFilter
   ├─ parse "Authorization: Bearer <jwt>"
   ├─ JwtService.validateToken()   ← signature + expiry, no DB on the happy path
   ├─ build authenticated UsernamePasswordAuthenticationToken
   └─ SecurityContextHolder.getContext().setAuthentication(token)
        │
        ▼
AuthorizationFilter checks the request against authorizeHttpRequests rules
        │
        ▼
@PreAuthorize on the method (if @EnableMethodSecurity)
        │
        ▼
Controller method runs
```

---

## 6. Concept deep-dive: the pieces

### 6.1 `SecurityContextHolder`
A holder around a `ThreadLocal<SecurityContext>`. "Who is the current user" is
answered from the thread handling the request. Two consequences: (a)
`@Async`/manually-spawned threads **don't inherit it** unless you change the
strategy to `MODE_INHERITABLETHREADLOCAL`; (b) with `STATELESS` sessions it's
cleared after each request, which is exactly why your filter must repopulate it
every time.

### 6.2 `Authentication`
One interface with three fields worth knowing: `getPrincipal()` (your
`UserDetails` after login), `getCredentials()` (the password — **null it out**
after authenticating, which is why the 3-arg constructor passes `null`), and
`getAuthorities()`. Inject it into a controller method (`Authentication auth`)
or read `SecurityContextHolder` — both work.

### 6.3 `UserDetailsService` vs. `AuthenticationProvider`
`UserDetailsService` only answers *"give me the user record for this
username."* The `AuthenticationProvider` decides *whether the credentials are
valid.* Custom login mechanisms (OTP, API key, LDAP) replace the **provider**;
a different user store replaces the **service**. Knowing which to swap is the
difference between fighting the framework and using it.

### 6.4 The `PasswordEncoder` circular dependency
Put `passwordEncoder()` inside `SecurityConfig` while `SecurityConfig` also
injects `UserDetailsServiceImpl`, which itself needs a `PasswordEncoder`, and
Spring reports
`The dependencies of some of the beans form a cycle`. This is a genuine rite of
passage — **do it once on purpose**, read the error, then fix it by moving the
encoder to its own `@Configuration`. Understanding this error is worth more than
avoiding it.

### 6.5 `OncePerRequestFilter`
A servlet request can re-enter the filter chain (RequestDispatcher forwards,
error dispatches, async). `OncePerRequestFilter` tracks a request attribute so
`doFilterInternal` runs exactly once. Extending plain `Filter` means your token
parsing can run two or three times per request.

### 6.6 Constructor injection vs. `@Autowired` fields
The reference sprinkles `@Autowired` on `final` fields alongside Lombok's
`@AllArgsConstructor` and `@Data` — redundant and fragile. Modern Spring: a
single constructor needs no annotation at all. Use
`@RequiredArgsConstructor` + `private final` fields. Benefits: true immutability,
fields guaranteed non-null at construction, trivially testable without a Spring
context, and cycles surface at startup instead of at runtime.

---

## 7. Concept deep-dive: JWT

### 7.1 Anatomy
```
eyJhbGciOiJIUzI1NiJ9 . eyJzdWIiOiJqYXNzaSIsImV4cCI6MTcwMDB9 . 4pcPyMD09olPSyXn
└─── header ─────────┘ └────────── payload ─────────────────┘ └── signature ──┘
      alg, typ              claims: sub, iat, exp, roles       HMACSHA256(
                                                                 b64(header)+"."+b64(payload),
                                                                 secret)
```
Header and payload are **Base64URL — reversible by anyone**. The signature only
proves *nobody altered them*. Integrity, not confidentiality.

### 7.2 Symmetric vs. asymmetric
- **HS256** — one shared secret signs and verifies. Simple; fine when one
  service does both. But every verifier can also forge.
- **RS256/ES256** — private key signs, public key verifies. The API Gateway and
  downstream services verify with the public key and *cannot* mint tokens. This
  is the right end-state for the architecture in Chapter 2, and where
  `spring-boot-starter-oauth2-resource-server` fits.

### 7.3 jjwt 0.11 → 0.12 API change
Old tutorials (and the reference) are on 0.11. Your pom is 0.12.6.

| 0.11 (reference, deprecated) | 0.12.x (yours) |
|---|---|
| `.setClaims(map)` | `.claims(map)` |
| `.setSubject(s)` | `.subject(s)` |
| `.setIssuedAt(d)` / `.setExpiration(d)` | `.issuedAt(d)` / `.expiration(d)` |
| `.signWith(key, SignatureAlgorithm.HS256)` | `.signWith(key)` (inferred) |
| `parser().setSigningKey(k)` | `parser().verifyWith(secretKey)` |
| `.parseClaimsJws(t).getBody()` | `.parseSignedClaims(t).getPayload()` |

### 7.4 Why you can't "log out" a JWT
Nothing is checked server-side, so a stolen access token works until `exp`.
Options: short expiry (do this), a denylist in Redis keyed by `jti` (reintroduces
statefulness — only for high-value operations), or a `tokenVersion` claim
compared against the user row (a DB read on every request, defeating the point).
There's no free lunch; know the menu.

---

## 8. Concept deep-dive: JPA traps in this project

- **`LazyInitializationException`** — loading roles lazily and touching them
  after the transaction closes (very likely inside `JwtAuthFilter`). Fixes:
  `EAGER`, `JOIN FETCH`, or map to `GrantedAuthority` *inside* the
  `@Transactional` service method. The third is best.
- **`@Data` on entities** — generated `equals`/`hashCode` traverse relationships
  → `StackOverflowError` on bidirectional links, and break `Set` membership once
  an ID is assigned.
- **`@ManyToMany` `EAGER`** — every user load pulls the join table. Acceptable
  for a small role set, disastrous as a general habit.
- **Missing `@Transactional`** — a multi-step signup (save user, assign roles,
  write the outbox row) that partially fails leaves inconsistent data.
- **Repository ID type mismatch** — `JpaRepository<UserInfo, Long>` when the
  `@Id` is a `String` (§9.4). Fails at runtime, not compile time.
- **`ddl-auto=create`** — drops and recreates the schema *on every start*. Your
  users vanish between runs and you'll waste an hour blaming your login code.

---

## 9. Reference-code gotchas — do **not** copy these

Reading these carefully is itself a code-review exercise. Every one is a real
defect in the reference implementation.

1. **`@ComponentScan(basePackages = {"authService.controller", ...})`** — wrong
   case (`authService` vs. actual package `authservice`) *and* it omits
   `entities`/`repository`. On Windows this may silently half-work. The fix is
   to delete both `@ComponentScan` and `@EnableJpaRepositories` entirely and rely
   on the `@SpringBootApplication` package root.
2. **`RefreshToken` uses `@OneToOne` with
   `@JoinColumn(name = "id", referencedColumnName = "user_id")`** — contradicts
   Chapter 2's "one user → many refresh tokens," and the FK column is named
   `id`, colliding with the PK. Use `@ManyToOne` + `@JoinColumn(name = "user_id")`.
3. **`UserInfoDto extends UserInfo`** and **`CustomUserDetails extends UserInfo
   implements UserDetails`** — inheriting from an `@Entity` drags JPA mapping
   into your DTO and security adapter, and `CustomUserDetails` ends up with two
   competing `username` fields (the inherited one is never set). DTOs and
   adapters should be **standalone classes**.
4. **`UserRepository extends CrudRepository<UserInfo, Long>`** while
   `UserInfo.userId` is a `String`. Type mismatch → runtime failure.
5. **`SecurityConfig` declares a `userDetailsService(...)` `@Bean` *and* injects
   `UserDetailsServiceImpl` as a field** — two instances of the same logical
   bean, plus a latent cycle with `PasswordEncoder`.
6. **`public static final String SECRET = "3576..."`** — a signing key committed
   to source control. Anyone with repo read access can forge tokens for any user.
   Move it to configuration, injected from an environment variable.
7. **`spring.jpa.hibernate.ddl-auto=create`** — wipes the database on every
   restart.
8. **`if (authentication.isAuthenticated()) ... else 500`** — unreachable else;
   `authenticate()` throws on failure. And a failed login is **401**, never 500.
9. **`catch (Exception ex) { return 500 "Exception in User Service" }`** in
   signup — converts a duplicate-username 400 into an opaque 500 and discards
   the cause.
10. **`.httpBasic(Customizer.withDefaults())` alongside stateless JWT** —
    leaves a second, unintended authentication path open.
11. **1-minute access-token expiry** — a debugging value shipped as config.
12. **No refresh-token rotation, no logout, no reuse detection** — a leaked
    refresh token is valid for its full lifetime with no way to revoke it.
13. **`@Autowired` on `final` fields + Lombok `@Data` + `@AllArgsConstructor` on
    components** — `@Data` on a Spring bean also generates `equals`/`hashCode`
    and setters that make the bean mutable at runtime.
14. **Empty `ValidationUtil` and a commented-out `validateUserAttributes` call** —
    signup accepts any input. Use Bean Validation (`@Valid`) instead of a util class.
15. **`// pushEventToQueue`** — the fault-tolerance requirement from Chapter 2
    §3 is entirely unimplemented (see M13).

---

## 10. Verification cookbook

```bash
# 1. signup
curl -s -X POST localhost:9898/auth/v1/signup \
  -H 'Content-Type: application/json' \
  -d '{"username":"jassi","password":"password123","email":"j@example.com"}' | jq

# 2. login  → capture tokens
ACCESS=$(curl -s -X POST localhost:9898/auth/v1/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"jassi","password":"password123"}' | jq -r .accessToken)
REFRESH=$(curl -s -X POST localhost:9898/auth/v1/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"jassi","password":"password123"}' | jq -r .token)

# 3. protected endpoint
curl -i -H "Authorization: Bearer $ACCESS" localhost:9898/ping        # 200

# 4. no token / tampered token / expired token
curl -i localhost:9898/ping                                           # 401
curl -i -H "Authorization: Bearer ${ACCESS}x" localhost:9898/ping     # 401

# 5. refresh
curl -s -X POST localhost:9898/auth/v1/refreshToken \
  -H 'Content-Type: application/json' -d "{\"token\":\"$REFRESH\"}" | jq

# 6. wrong password — must be 401, generic message, same shape as unknown user
curl -i -X POST localhost:9898/auth/v1/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"jassi","password":"wrong"}'

# 7. duplicate signup — must be 400, not 500
```

Also: decode your access token at https://jwt.io and read your own claims.

---

## 11. Self-assessment — can you explain these without notes?

If you can answer all of these, you've learned what this project is for.

1. Draw the Spring Security filter chain and mark where your JWT filter sits and why.
2. What does `SecurityContextHolder` hold, and when is it populated vs. cleared?
3. Why is the same `UsernamePasswordAuthenticationToken` class used for both the
   login request and the authenticated result? How does it know which it is?
4. Why is CSRF protection safe to disable here but not for a cookie-session app?
5. What exactly does `SessionCreationPolicy.STATELESS` change?
6. Why is BCrypt preferred over SHA-256 for passwords? What do the salt and cost
   factor each defend against?
7. Trace a `/login` request from the controller down to `PasswordEncoder.matches`.
8. Why can't you revoke an access token, and what are the three mitigations?
9. Why is a refresh token stored in the DB while an access token isn't?
10. What is refresh-token rotation and what attack does reuse detection catch?
11. What's the difference between a 401 and a 403, and which component produces each?
12. Why does the transactional outbox pattern solve a problem that "save then
    publish" cannot?
13. Which single change would let the API Gateway verify tokens without being
    able to mint them?
14. Where do `@PreAuthorize` checks run relative to the filter chain?
15. What breaks if you put `.anyRequest().authenticated()` *before*
    `.requestMatchers("/auth/v1/login").permitAll()`?

---

*Previous: [Chapter 2 — Auth Service](./chapter-2-auth-service.md) ·
[Chapter 1 — About the Expense Tracker App](./chapter-1-about.md)*
