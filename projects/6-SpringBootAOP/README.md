# Spring Boot AOP — runnable demo

A minimal Spring Boot app that demonstrates **every core piece of AOP**, referenced
from [`learning/10-AOP-Aspect-Oriented-Programming.md`](../../learning/10-AOP-Aspect-Oriented-Programming.md).
Inspired by [AlphaDecodeX/SpringBootAOP](https://github.com/AlphaDecodeX/SpringBootAOP)
(cleaned up: consistent packages, Maven, all advice types, plus the
"log a library you can't edit" use case).

## The 5 AOP terms, mapped to the code

| Term | Where it lives |
|------|----------------|
| **Aspect** | `aspect/LoggingAspect.java`, `aspect/LibraryLoggingAspect.java` (`@Aspect`) |
| **Advice** | each method: `@Before`, `@After`, `@AfterReturning`, `@AfterThrowing`, `@Around` |
| **Join point** | a method execution on `UserService` / `PaymentGateway` (the `JoinPoint` arg) |
| **Pointcut** | `@Pointcut("execution(* ...UserService.*(..))")` — selects which methods |
| **Weaving** | Spring wrapping the beans in a **proxy** at startup (`@EnableAspectJAutoProxy`) |

## Files
```
6-SpringBootAOP/
├── pom.xml
└── src/main/java/com/jassi/aop/
    ├── App.java                         <- @SpringBootApplication + @EnableAspectJAutoProxy
    ├── model/User.java                  <- Lombok @AllArgsConstructor + @Getter/@Setter
    ├── service/UserService.java         <- @Service SINGLETON, the advised business class
    ├── controller/Api.java              <- @RestController, @Autowired singletons
    ├── aspect/LoggingAspect.java        <- @Before/@After/@AfterReturning/@AfterThrowing/@Around
    ├── aspect/LibraryLoggingAspect.java <- logs library methods we "can't edit"
    └── library/PaymentGateway.java      <- pretend third-party library
```

## Key ideas shown

- **Lombok** — `User` has no hand-written constructor/getters; `@AllArgsConstructor`,
  `@Getter`, `@Setter` generate them at compile time.
- **Singleton via `@Autowired`** — `@Service` makes `UserService` a single shared
  bean; `Api` receives that one instance (actually its **proxy**) by injection.
- **All 5 advice types** — see `LoggingAspect`. `@Around` wraps the method and must
  call `proceed()`; skipping it would short-circuit the real method.
- **Library use case** — `LibraryLoggingAspect` weaves logging around
  `PaymentGateway` **without editing it**: the pointcut matches its package and
  the advice runs "inside" each library call.

## Run it

```bash
cd projects/6-SpringBootAOP
mvn spring-boot:run
```

Then hit the endpoints and watch the **console** for the aspect logs:

| URL | Advice that fires |
|-----|-------------------|
| http://localhost:8080/login  | `@Before`, `@Around` (timing), `@After`, `@AfterReturning` |
| http://localhost:8080/user   | `@Before`, `@After`, `@AfterReturning` (reads the returned `User`) |
| http://localhost:8080/logout | `@Before`, `@AfterThrowing`, `@After` (method throws) |
| http://localhost:8080/pay    | library aspect: `[LIB @Before]` / `[LIB @After]` |

### Example console output for `/login`
```
--- /login ---
[@Before]         → about to run: UserService.logIn()
[@Around]         → START timing logIn
   [UserService] logging user in: Lovepreet Singh
[@Around]         → END   took 42 micros
[@After]          → finished (ok or error): logIn
[@AfterReturning] → logIn returned: null
```

## Gotchas (because AOP uses proxies)
- **Self-invocation bypasses AOP** — one `UserService` method calling another on
  `this` skips the proxy, so advice won't fire. Call through the injected bean.
- **Only `public` methods** are advised by Spring AOP.
- Works only on **Spring-managed beans**; objects created with `new` inside a
  library need full **AspectJ** load-time weaving.
