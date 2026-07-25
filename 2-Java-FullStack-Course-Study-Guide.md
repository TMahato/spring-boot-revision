# Complete Java Full Stack: AWS, Spring Boot, Microservices — Read-Only Study Guide

> Source video (Part 1): https://www.youtube.com/watch?v=fmX84zu-5gs
> Playlist "Full Courses [In Depth]": https://www.youtube.com/playlist?list=PL7CBVLpg0zqf_ggig9cOr72oZNGf9RZmZ
> Creator: SinghDevHub / AlphaDecodeX (thinkit.club) · Length: ~11h 17m · Audio: Hindi
> This guide reproduces the video's structure and teaches each section so you can learn + build **by reading**.

## The 4 projects you build
1. **🎫 Ticket Booking System** — object mapping, file I/O, Java core, functional interfaces.
2. **🌐 Multithreaded Web Server in Java** — socket programming, OS concepts, networking, multithreading.
3. **👨 Realtime User-Activity Tracking backend** — Kafka event streaming, Spring Boot producer/consumer, Linux.
4. **💰 Expense Tracker App** — microservices, API gateway, auth, AI/LLM microservice, React Native, AWS deploy.

---

## Chapter map (timestamps → what's taught)

| Time | Topic |
|------|-------|
| 00:00–06:45 | Intro, projects covered, info |
| 06:46–30:58 | JVM, JDK, Java internals |
| 30:59–1:08:25 | Java Basics |
| 1:08:26–1:43:46 | Java advanced: Optionals, Streams, Maps |
| 1:43:47–2:00:33 | Ticket Booking planning |
| 2:00:34–3:25:00 | Ticket Booking build (uses Java advanced) |
| 3:25:01–4:37:53 | Project 1: Multithreaded Web Server |
| 4:37:54–5:11:38 | Spring beans, IoC, Gradle + interview Qs |
| 5:11:39–5:36:20 | Spring proxies, reflection, @Cacheable, CGLIB |
| 5:36:21–6:03:24 | Spring AOP, pointcuts, proxy usage |
| 6:03:25–6:13:50 | Expense Tracker: High-Level Design |
| 6:13:51–6:40:15 | Authentication, Authorization, JWT, refresh tokens |
| 6:40:16–7:07:31 | SQL vs NoSQL, VMs, VPS |
| 7:07:32–7:37:28 | Low-level design, DB modelling |
| 7:37:29–9:23:30 | Spring Security with JWT + refresh tokens (Auth service) |
| 9:23:31–10:01:00 | Linux crash course |
| 10:01:01–11:17:16 | Kafka & RabbitMQ crash course |

### Repos & resources (VERIFIED URLs — full list + code walkthroughs in `Java-FullStack-DeepDive.md`)
- Ticket booking: https://github.com/singhdevhub-lovepreet/ticketBooking
- Multithreaded web server: https://github.com/AlphaDecodeX/MultithreadedWebServer
- Spring proxies: https://github.com/AlphaDecodeX/SpringBootProxies · Spring AOP: https://github.com/AlphaDecodeX/SpringBootAOP
- Auth service (course version): https://github.com/singhdevhub-lovepreet/authservice
- Expense Tracker system: `expenseService`, `userservice`, `dsService` (AI/LLM), `expensetrackerapp` (React Native), `expenseTracker-awsCDK` — all under https://github.com/singhdevhub-lovepreet
- Blog — ThreadPool vs EventLoop: `dev.to/ssd/multithreading-eve...` (in video description)
- Live cohort: https://live.thinkit.club/course/se-ai

---

## 1. JVM, JDK, Java internals (06:46–30:58)

**Goal:** understand what runs your Java code.

- **JDK vs JRE vs JVM**
  - *JDK* (Java Development Kit) = compiler (`javac`) + tools + JRE. You need it to develop.
  - *JRE* (Runtime Environment) = JVM + core libraries. Enough to *run* apps.
  - *JVM* (Java Virtual Machine) = the abstract machine that executes bytecode. This is what makes Java "write once, run anywhere."
- **Compilation flow:** `.java` → `javac` → `.class` (bytecode) → JVM interprets/JIT-compiles → native machine code.
- **JVM areas:** Method area, Heap (objects), Stack (per-thread frames), PC register, native method stack.
- **Class loading:** Loading → Linking (verify, prepare, resolve) → Initialization. ClassLoaders: Bootstrap → Platform → Application.
- **JIT compiler:** hot code paths get compiled to native code for speed.
- **Garbage Collection:** heap is split into Young (Eden + Survivor) and Old gen. Objects die young → minor GC; long-lived → major GC. Know G1 GC exists as the modern default.

**Read-and-remember:** bytecode is platform-independent; the JVM is platform-specific. GC frees memory automatically — no manual `free()`.

## 2. Java Basics (30:59–1:08:25)

- **Primitives:** `byte, short, int, long, float, double, char, boolean` vs wrapper classes (`Integer`, etc.) and autoboxing.
- **Control flow:** `if/else`, `switch` (incl. switch expressions), `for`, `while`, `do-while`, enhanced for.
- **OOP pillars:** Encapsulation, Inheritance, Polymorphism (overloading vs overriding), Abstraction (`abstract` classes vs `interface`).
- **Classes/objects:** constructors, `this`, `static` vs instance, access modifiers (`public/private/protected/default`).
- **Strings:** immutable; `String` vs `StringBuilder`/`StringBuffer`; `==` vs `.equals()`; string pool.
- **Arrays**, **exceptions** (`try/catch/finally`, checked vs unchecked, custom exceptions), **enums**.
- **Collections intro:** `List` (ArrayList/LinkedList), `Set` (HashSet/TreeSet), `Map` (HashMap/TreeMap).

**Practice idea:** rebuild small snippets from memory — a class with fields/constructor, overriding `toString()`/`equals()`/`hashCode()`.

## 3. Java Advanced: Optionals, Streams, Maps (1:08:26–1:43:46)

- **`Optional<T>`:** avoids `NullPointerException`. `Optional.of/ofNullable/empty`, `.map`, `.filter`, `.orElse`, `.orElseGet`, `.orElseThrow`, `.ifPresent`. Don't use it for fields/parameters — use as return type.
- **Functional interfaces & lambdas:** `Function`, `Supplier`, `Consumer`, `Predicate`, `BiFunction`; method references (`Class::method`).
- **Streams API:** `stream()` → intermediate ops (`map`, `filter`, `sorted`, `distinct`, `limit`, `flatMap`) → terminal ops (`collect`, `forEach`, `reduce`, `count`, `anyMatch`).
  - `Collectors.toList/toSet/toMap/groupingBy/partitioningBy/joining`.
  - Lazy evaluation: nothing runs until a terminal op.
- **Maps deep-dive:** `HashMap` internals (buckets, hashing, treeify at 8 collisions), `computeIfAbsent`, `getOrDefault`, `merge`, iterating `entrySet()`.

**These are used directly to build the Ticket Booking project — learn them well.**

## 4. Ticket Booking System (planning 1:43:47, build 2:00:34–3:25:00)

**What you build:** a console/service app modeling users, trains/shows, seats, bookings.

- **Domain modelling:** classes like `User`, `Train`/`Show`, `Seat`, `Booking`, `Ticket`.
- **Object mapping:** convert between domain objects and stored/serialized form.
- **File I/O:** persist data to files (read/write, serialization or JSON). Learn `BufferedReader/Writer`, `Files`, try-with-resources.
- **Functional interfaces & streams:** filter available seats, search trains, sort by time/price.
- **Core concepts applied:** collections to hold bookings, exception handling for invalid bookings, enums for seat/booking status.

**Repo:** search GitHub "singhdevhub ticket booking". Clone → read the class structure → re-implement one feature yourself (e.g., "cancel booking").

## 5. Project 1 — Multithreaded Web Server in Java (3:25:01–4:37:53)

**What you build:** an HTTP server from scratch using raw sockets that handles many clients concurrently.

- **Sockets:** `ServerSocket` (listens on a port) `.accept()` returns a `Socket` per client. Read the request from `InputStream`, write response to `OutputStream`.
- **HTTP basics:** parse request line (`GET /path HTTP/1.1`), headers; write a status line + headers + body.
- **Concurrency models (key learning):**
  - *Thread-per-request* — simple but doesn't scale (thread overhead).
  - *Thread pool* (`ExecutorService`, `Executors.newFixedThreadPool`) — bounded threads reuse.
  - *Event loop* (single-threaded async, like Node/Nginx) — compared in the blog.
- **OS/networking concepts:** blocking vs non-blocking I/O, context switching, why thread pools cap resource use.

**Resources:** Web server repo `github.com/AlphaDecodeX/Multi...`; blog "ThreadPool vs EventLoop" on dev.to (`dev.to/ssd/multithreading-eve...`). Read the blog first — it's the conceptual backbone.

## 6. Spring Beans, IoC, Gradle (4:37:54–5:11:38)

- **Inversion of Control (IoC):** you don't `new` your dependencies; the Spring container creates and wires them.
- **Beans:** objects managed by Spring. Declared via `@Component/@Service/@Repository/@Configuration + @Bean`.
- **Dependency Injection:** constructor (preferred), setter, field (`@Autowired`). Understand why constructor injection is best (immutability, testability).
- **Bean scopes:** `singleton` (default), `prototype`, request/session (web).
- **Bean lifecycle:** instantiate → populate deps → `@PostConstruct` → use → `@PreDestroy`.
- **Gradle:** `build.gradle`, dependencies block, tasks, wrapper (`./gradlew`). Compare briefly with Maven.
- **Interview Qs to know:** difference between `@Component` and `@Bean`; how Spring resolves ambiguity (`@Qualifier`, `@Primary`).

## 7. Spring Proxies, Reflection, @Cacheable, CGLIB (5:11:39–5:36:20)

- **Reflection:** inspect/modify classes at runtime (`Class`, `Method`, `Field`). Spring uses it heavily.
- **Proxies:** Spring wraps beans in proxy objects to add behavior (transactions, caching, security) without changing your code.
  - *JDK dynamic proxies* — for interfaces.
  - *CGLIB* — subclasses your class when there's no interface.
- **`@Cacheable`/`@CacheEvict`:** method results cached by key; second call skips execution. Works **because** of the proxy — self-invocation (calling from within same class) bypasses the proxy (common gotcha).

**Repo:** `github.com/AlphaDecodeX/Spring...` (proxies).

## 8. Spring AOP — Aspect Oriented Programming (5:36:21–6:03:24)

- **Cross-cutting concerns:** logging, security, transactions, metrics — code that repeats across methods.
- **Core terms:** *Aspect* (the module), *Advice* (`@Before/@After/@Around/@AfterReturning/@AfterThrowing`), *Pointcut* (expression selecting where advice runs), *Join point* (a point in execution).
- **Pointcut expressions:** `execution(* com.app.service.*.*(..))`.
- Implemented via the same proxy mechanism from section 7.

**Repo:** `github.com/AlphaDecodeX/Spring...` (AOP). Try writing an `@Around` advice that logs method time.

## 9. Expense Tracker — High-Level Design (6:03:25–6:13:50)

- Microservices decomposition: **Auth service**, **Expense/Transaction service**, **API Gateway**, a **Data-science/AI (LLM) service**, plus **React Native** frontend.
- Services communicate over REST + async events (Kafka). Gateway handles routing/auth.
- Think about: service boundaries, shared vs per-service DB, how auth tokens flow through the gateway.

## 10. Authentication & Authorization — JWT, Refresh Tokens (6:13:51–6:40:15)

- **AuthN vs AuthZ:** *authentication* = who you are; *authorization* = what you can do.
- **JWT (JSON Web Token):** `header.payload.signature`, base64url-encoded, signed (HMAC/RSA). Stateless — server verifies signature, no session store needed.
- **Access token** (short-lived, ~15 min) + **Refresh token** (long-lived, stored securely) → get new access token without re-login.
- **Flow:** login → issue access+refresh → client sends access token in `Authorization: Bearer` → on expiry, call refresh endpoint.
- Security notes: never put secrets in payload (it's readable), always verify signature + expiry, rotate/revoke refresh tokens.

## 11. SQL vs NoSQL, VMs, VPS (6:40:16–7:07:31)

- **SQL (relational):** tables, schemas, ACID, joins, strong consistency (Postgres/MySQL). Good for structured, related data.
- **NoSQL:** document (MongoDB), key-value (Redis), wide-column, graph. Flexible schema, horizontal scale, eventual consistency. Good for high write volume / flexible data.
- **When to use which:** relationships + transactions → SQL; huge scale + flexible docs → NoSQL.
- **VM / VPS:** virtual machine = virtualized computer; VPS = a VM you rent. *Note from author: you can do everything locally — no cloud VM required to learn.*
- Resource links: install SQL on Ubuntu (DigitalOcean), remote access to Ubuntu (snapshooter blog).

## 12. Low-Level Design & DB Modelling (7:07:32–7:37:28)

- Translate HLD into concrete tables/collections: entities, columns, primary/foreign keys, indexes, relationships (1:1, 1:N, N:M via join table).
- Normalize (avoid duplication) but know when to denormalize for reads.
- Design doc is on Notion (`ginger-uranium-8af.notion.sit...` in description).
- Design the Expense Tracker schema: `users`, `expenses`, `categories`, `refresh_tokens`, etc.

## 13. Spring Security with JWT + Refresh Tokens — Auth Service (7:37:29–9:23:30)

**The biggest hands-on section.** You build a real Auth microservice.

- **Spring Security filter chain:** requests pass through filters; add a custom `JwtAuthenticationFilter`.
- **Key pieces:**
  - `SecurityFilterChain` bean (replaces old `WebSecurityConfigurerAdapter`) — configure which endpoints are public (`/login`, `/register`, `/refresh`) vs protected.
  - `UserDetailsService` + `PasswordEncoder` (BCrypt) for storing/checking passwords.
  - `AuthenticationManager` for login.
  - JWT util to generate/validate tokens; filter that reads `Authorization` header, validates, sets `SecurityContext`.
  - Refresh-token endpoint + storage (DB table) with rotation.
- **Endpoints:** register, login (returns access+refresh), refresh, protected resource.
- **Repo:** Auth Service on Google Drive (linked in description). Read it, then rebuild the JWT filter yourself.

## 14. Linux Crash Course (9:23:31–10:01:00)

- **Filesystem & navigation:** `pwd, ls, cd, mkdir, rm, cp, mv, cat, less, nano/vim`.
- **Permissions:** `chmod`, `chown`, read/write/execute, `sudo`.
- **Processes:** `ps, top/htop, kill`, background jobs (`&`, `nohup`), `systemctl` for services.
- **Networking:** `curl, wget, ping, netstat/ss`, ports.
- **Package mgmt:** `apt update/install`.
- **Text tools:** `grep, awk, sed, pipe |, redirection > >>`.
- *Author's note: no need for AWS Lightsail — practice locally (WSL on Windows works great).*
- Resources: AWS Lightsail setup video; install Kafka on Ubuntu (vegastack tutorial).

## 15. Kafka & RabbitMQ Crash Course (10:01:01–11:17:16)

- **Why messaging:** decouple services, buffer load, async processing, event streaming.
- **Kafka concepts:** *Producer*, *Consumer*, *Topic*, *Partition* (parallelism + ordering per partition), *Consumer group* (scaling consumers), *Offset* (position), *Broker*, *Zookeeper/KRaft*. Kafka = durable, replayable log; great for high-throughput event streaming.
- **RabbitMQ concepts:** *Exchange* (direct/topic/fanout), *Queue*, *Binding*, *Routing key*, ack/nack. Traditional message broker; great for task queues / complex routing.
- **Kafka vs RabbitMQ:** Kafka = high-throughput, retained log, replay, streaming. RabbitMQ = flexible routing, per-message ack, lower-latency task distribution.
- **Spring Boot integration:** `spring-kafka` → `@KafkaListener`, `KafkaTemplate`. For the user-activity project: producer publishes activity events, consumer processes/stores them.
- Resources (Google Drive, in description): React frontend, Java Producer/Consumer, and a doc.

---

## Suggested reading order (to "complete" the course without watching)
1. Read sections 1–3 here, then write small Java snippets to lock in Streams/Optionals/Maps.
2. Clone **Ticket Booking** repo → read code → re-implement one feature.
3. Read the **ThreadPool vs EventLoop blog** → clone **Web Server** repo → run it, hit it with `curl`.
4. Read Spring sections 6–8 → build a tiny Spring Boot app with a `@Service`, `@Cacheable`, and one AOP logging aspect.
5. Read HLD/auth/DB sections 9–13 → clone **Auth Service** repo → rebuild the JWT filter.
6. Do the **Linux** section hands-on in WSL/terminal.
7. Read **Kafka/RabbitMQ** → run the Producer/Consumer repo locally.

## Notes / caveats
- I could not extract the raw spoken transcript: the only caption track is **Hindi auto-generated**, and YouTube now blocks the caption-download endpoint without auth tokens. This guide is built from the video's official chapter list + linked repos + the standard content of each topic, so it covers the same material in readable form.
- Some links in the description are truncated by YouTube (`...`). Open the video description directly to click the full URLs (repos, Notion doc, Google Drive folders).
- Part 2 of the course: https://www.youtube.com/watch?v=vMWvPN1R3yI
