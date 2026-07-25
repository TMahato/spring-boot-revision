# Deep Dive: Repo URLs + Worked Code Notes

Companion to `Java-FullStack-Course-Study-Guide.md`. All repo links below are **verified live** (confirmed against GitHub API), matching the truncated links in the video description.

## ✅ Verified repository URLs

The course author uses **two** GitHub accounts. Newer/course-final versions live under `singhdevhub-lovepreet`; earlier demo versions under `AlphaDecodeX`.

| Course section | Repo | URL |
|---|---|---|
| Ticket Booking | ticketBooking | https://github.com/singhdevhub-lovepreet/ticketBooking |
| Ticket Booking (alt/demo) | TicketBookingSystem | https://github.com/AlphaDecodeX/TicketBookingSystem |
| Multithreaded Web Server | MultithreadedWebServer | https://github.com/AlphaDecodeX/MultithreadedWebServer |
| Spring Proxies | SpringBootProxies | https://github.com/AlphaDecodeX/SpringBootProxies |
| Spring AOP | SpringBootAOP | https://github.com/AlphaDecodeX/SpringBootAOP |
| Spring AOP (alt) | aop | https://github.com/singhdevhub-lovepreet/aop |
| **Auth Service (course version)** | authservice | https://github.com/singhdevhub-lovepreet/authservice |
| Expense Service | expenseService | https://github.com/singhdevhub-lovepreet/expenseService |
| Data-science / AI (LLM) service | dsService | https://github.com/singhdevhub-lovepreet/dsService |
| User Service | userservice | https://github.com/singhdevhub-lovepreet/userservice |
| Expense Tracker (React Native app) | expensetrackerapp | https://github.com/singhdevhub-lovepreet/expensetrackerapp |
| Expense Tracker infra deps | expenseTrackerAppDeps | https://github.com/singhdevhub-lovepreet/expenseTrackerAppDeps |
| Expense Tracker AWS CDK deploy | expenseTracker-awsCDK | https://github.com/singhdevhub-lovepreet/expenseTracker-awsCDK |
| Java mastery (basics/streams) | javamastery | https://github.com/singhdevhub-lovepreet/javamastery |
| Spring mastery | SpringBootMastery | https://github.com/AlphaDecodeX/SpringBootMastery |

> The full Expense Tracker microservices system = `authservice` + `userservice` + `expenseService` + `dsService` + `expensetrackerapp` (frontend) + `expenseTrackerAppDeps`/`expenseTracker-awsCDK` (infra).

---

# Deep Dive A — Multithreaded Web Server

Repo: https://github.com/AlphaDecodeX/MultithreadedWebServer
Structure: three folders showing the **evolution** of a server — `SingleThreaded/`, `Multithreaded/`, `ThreadPool/`. Read them in that order; that's the whole lesson.

### Stage 1 — SingleThreaded/Server.java (the naive baseline)
```java
ServerSocket socket = new ServerSocket(port);   // bind + listen on 8010
while (true) {
    Socket accepted = socket.accept();          // BLOCKS until a client connects
    PrintWriter toClient = new PrintWriter(accepted.getOutputStream(), true);
    toClient.println("Hello World from the server");
}
```
**Key ideas & problem:**
- `ServerSocket` = the listening socket. `.accept()` **blocks** the single thread until one client connects, returns a `Socket` for that client.
- Everything happens on the main thread. While you serve client A, client B waits. One slow client stalls everyone → **no concurrency**.

### Stage 2 — Multithreaded/Server.java (thread-per-client)
```java
while (true) {
    Socket clientSocket = serverSocket.accept();
    Thread thread = new Thread(() -> server.getConsumer().accept(clientSocket)); // one thread PER client
    thread.start();
}
```
- Each connection gets its **own thread**, so clients are served in parallel. Note the use of a `Consumer<Socket>` functional interface — this is your Streams/lambda knowledge (guide §3) applied.
- **Problem:** unbounded threads. 10,000 clients → 10,000 threads → memory blows up, CPU thrashes on context switches. Threads are expensive (~1MB stack each).

### Stage 3 — ThreadPool/Server.java (the production pattern)
```java
private final ExecutorService threadPool = Executors.newFixedThreadPool(poolSize); // e.g. 10

while (true) {
    Socket clientSocket = serverSocket.accept();
    threadPool.execute(() -> handleClient(clientSocket)); // reuse a FIXED set of threads
}
// on exit: threadPool.shutdown();
```
- A **fixed pool** of N worker threads is reused across all requests. Extra requests **queue** until a thread is free. Bounded memory, bounded context-switching → scales predictably.
- This is exactly how Tomcat/Spring Boot serves HTTP requests under the hood.

### The interview payoff: Thread Pool vs Event Loop
- **Thread pool** (this repo / Tomcat / Java): N threads, each handles one blocking request at a time. Simple mental model; memory grows with pool size; good for CPU-bound + moderate concurrency.
- **Event loop** (Node.js / Nginx / Netty): **one** thread, non-blocking I/O, a loop dispatches events via callbacks. Handles tens of thousands of idle connections cheaply; bad if you block the loop with CPU work.
- Rule of thumb: I/O-bound massive concurrency → event loop; CPU-bound / simpler code → thread pool. (This is the dev.to blog referenced in the description.)

**Homework to "complete" this section:** extend `handleClient` to actually parse the HTTP request line (`GET /path HTTP/1.1`) from `clientSocket.getInputStream()` and return a proper HTTP response (`HTTP/1.1 200 OK\r\nContent-Length: ...\r\n\r\n<body>`), serving a file from disk.

---

# Deep Dive B — Auth Service (Spring Security + JWT + Refresh Tokens)

Repo: https://github.com/singhdevhub-lovepreet/authservice
This is the **backbone microservice** of the Expense Tracker. Package layout:
```
authservice/
├─ controller/  AuthController, TokenController, SecurityConfig
├─ auth/        JwtAuthFilter, UserConfig
├─ service/     JwtService, RefreshTokenService, UserDetailsServiceImpl, CustomUserDetails
├─ entities/    UserInfo, RefreshToken, UserRole
├─ repository/  UserRepository, RefreshTokenRepository
├─ request/     AuthRequestDTO, RefreshTokenRequestDTO
├─ response/    JwtResponseDTO
├─ eventProducer/ UserInfoProducer, UserInfoEvent   ← Kafka: publishes new-user events
└─ serializer/  UserInfoSerializer
```
Notice `eventProducer/` — when a user signs up, this service **publishes a Kafka event** so other services (userservice) learn about the new user. That ties directly into the Kafka section (guide §15).

### 1) How the pieces fit (request lifecycle)
```
Client → [JwtAuthFilter] → SecurityFilterChain → Controller → Service → Repository → DB
```
Every request first passes the JWT filter, which authenticates the caller *before* Spring's normal auth filters.

### 2) SecurityConfig — the filter chain (controller/SecurityConfig.java)
```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
    return http
        .csrf(AbstractHttpConfigurer::disable).cors(CorsConfigurer::disable)
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/auth/v1/login", "/auth/v1/refreshToken", "/auth/v1/signup", "/health").permitAll() // PUBLIC
            .anyRequest().authenticated())                                                                        // everything else needs a token
        .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // no HTTP session
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)             // insert our JWT filter first
        .authenticationProvider(authenticationProvider())
        .build();
}
```
**Learn these decisions:**
- `STATELESS` session policy → no server-side session; every request must carry the JWT. This is *the* microservices/REST pattern.
- `permitAll()` on login/signup/refresh/health (you can't have a token yet), `authenticated()` on everything else.
- `addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)` → our filter runs **before** Spring's default username/password filter.
- `csrf disable` is fine here *because* we're stateless + token-based (no cookies).
- `authenticationProvider()` = `DaoAuthenticationProvider` wired to `UserDetailsService` + `BCryptPasswordEncoder` — this is what checks username/password at login time.

### 3) JwtService — mint & verify tokens (service/JwtService.java)
```java
public String GenerateToken(String username) {
    return Jwts.builder()
        .setSubject(username)                                   // "sub" claim = who
        .setIssuedAt(new Date())                                // "iat"
        .setExpiration(new Date(System.currentTimeMillis() + 100000*60)) // "exp" (short-lived)
        .signWith(getSignKey(), SignatureAlgorithm.HS256)       // HMAC-SHA256 signature
        .compact();
}
public Boolean validateToken(String token, UserDetails userDetails) {
    final String username = extractUsername(token);
    return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
}
```
- A JWT = `header.payload.signature`. `extractUsername`/`extractExpiration` parse claims; validity = **username matches AND not expired**.
- Signing key is HMAC from a base64 secret. **Real-world note:** the repo hardcodes `SECRET` in source — for learning only. In production put it in env/secrets manager and rotate it. Point this out in interviews.
- `extractClaim` uses a `Function<Claims,T>` — again functional-interface knowledge from §3.

### 4) JwtAuthFilter — authenticate every request (auth/JwtAuthFilter.java)
```java
public class JwtAuthFilter extends OncePerRequestFilter {       // runs once per request
    protected void doFilterInternal(req, res, chain) {
        String authHeader = req.getHeader("Authorization");
        String token = null, username = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);                    // strip "Bearer "
            username = jwtService.extractUsername(token);
        }
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (jwtService.validateToken(token, userDetails)) {
                var authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                SecurityContextHolder.getContext().setAuthentication(authToken); // ← now "logged in" for this request
            }
        }
        chain.doFilter(req, res);   // always continue the chain
    }
}
```
**The core lesson:** extends `OncePerRequestFilter` → read `Authorization: Bearer <token>` → validate → put an `Authentication` into `SecurityContextHolder`. From that point the request is treated as authenticated and `@PreAuthorize`/`authenticated()` rules pass. If no/invalid token, context stays empty → protected endpoints return 401.

### 5) TokenController — login & refresh (controller/TokenController.java)
```java
@PostMapping("auth/v1/login")
public ResponseEntity AuthenticateAndGetToken(@RequestBody AuthRequestDTO dto) {
    Authentication auth = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(dto.getUsername(), dto.getPassword())); // verifies password via BCrypt
    if (auth.isAuthenticated()) {
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(dto.getUsername());
        return ResponseEntity.ok(JwtResponseDTO.builder()
            .accessToken(jwtService.GenerateToken(dto.getUsername()))  // short-lived JWT
            .token(refreshToken.getToken())                            // long-lived refresh token
            .build());
    }
}

@PostMapping("auth/v1/refreshToken")
public JwtResponseDTO refreshToken(@RequestBody RefreshTokenRequestDTO dto) {
    return refreshTokenService.findByToken(dto.getToken())
        .map(refreshTokenService::verifyExpiration)   // reject if refresh token expired
        .map(RefreshToken::getUserInfo)
        .map(user -> JwtResponseDTO.builder()
            .accessToken(jwtService.GenerateToken(user.getUsername())) // issue a FRESH access token
            .token(dto.getToken()).build())
        .orElseThrow(() -> new RuntimeException("Refresh Token is not in DB..!!"));
}
```
**The refresh-token flow (memorize this):**
1. `login` → returns **access token** (short-lived, ~minutes) + **refresh token** (long-lived, stored in DB).
2. Client uses the access token on every request (`Bearer`) until it expires.
3. On expiry, client calls `/refreshToken` with the refresh token → server verifies it's in DB and not expired → issues a **new access token** without re-entering the password.
4. Notice the elegant `Optional.map().map().orElseThrow()` chain — that's your §3 `Optional` knowledge in real code.

### 6) AuthController — signup + protected ping
- `POST /auth/v1/signup` → creates user (`userDetailsService.signupUser`), returns access+refresh tokens, and (via `UserInfoProducer`) fires a Kafka event.
- `GET /auth/v1/ping` → reads `SecurityContextHolder` to prove the JWT filter authenticated you (returns the userId, else 401). Great endpoint to test your token with.
- `GET /health` → public liveness check.

### How to "complete" this section by doing
1. Clone the repo, run its DB (see `Dockerfile`/deps), start the app.
2. `POST /auth/v1/signup` with a username/password → save the `accessToken` + refresh `token`.
3. Call `GET /auth/v1/ping` with header `Authorization: Bearer <accessToken>` → should return your userId.
4. Wait for the access token to expire (or shorten `setExpiration`) → `ping` returns 401 → call `/auth/v1/refreshToken` with the refresh token → get a new access token → `ping` works again.
5. Rebuild `JwtAuthFilter` and `JwtService` from scratch in a blank project — that's the skill interviewers test.

---

## Where to go next
- Once auth works, wire `userservice` + `expenseService` behind an API gateway; `dsService` is the AI/LLM microservice.
- Kafka (`eventProducer` package here + guide §15) connects auth → user provisioning asynchronously.
- Deployment: `expenseTracker-awsCDK` shows the AWS side.
