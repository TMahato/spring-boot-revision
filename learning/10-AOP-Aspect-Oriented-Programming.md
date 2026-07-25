# AOP — Aspect-Oriented Programming — Notes

## 1. What problem does AOP solve?

Some concerns are **not** part of your business logic but show up **everywhere**:
logging, security checks, transactions, caching, metrics/timing, retries. These
are called **cross-cutting concerns** — they "cut across" many classes and
methods.

Without AOP you copy-paste the same boilerplate into every method:

```java
public void placeOrder(Order o) {
    log.info("entering placeOrder");     // logging  ─┐
    long start = System.nanoTime();      // metrics   │  cross-cutting
    checkSecurity();                     // security  │  clutter repeated
    // ---- actual business logic ----               │  in EVERY method
    saveOrder(o);
    // --------------------------------              │
    log.info("exiting placeOrder");      // logging  ─┘
}
```

**AOP** lets you write that cross-cutting code **once**, in one place, and have
it automatically applied **around** the methods you choose — keeping your
business methods clean:

```java
public void placeOrder(Order o) {
    saveOrder(o);          // just the business logic, nothing else
}
// logging, timing, security are injected by an ASPECT, separately
```

> AOP is the **design idea**; the **proxy** (see [`9-Proxy-In-Spring-Boot`](./9-Proxy-In-Spring-Boot.md))
> is the **mechanism** Spring uses to make it happen. `@Transactional`,
> `@Cacheable`, `@Async` are pre-built aspects.

> **Runnable project:** [`projects/6-SpringBootAOP`](../projects/6-SpringBootAOP) —
> a full Spring Boot app with all 5 advice types, a Lombok `User`, an `@Autowired`
> singleton `UserService`, and an aspect that logs a "library" class you can't
> edit. See its `README.md`.

---

## 2. The 5 core terms (memorize these)

AOP has its own vocabulary. Learn these five and everything else clicks.

| Term | What it is | Plain-English meaning |
|------|-----------|-----------------------|
| **Aspect** | A module bundling a cross-cutting concern | "The logging feature" — the class holding the extra logic (`@Aspect`) |
| **Advice** | The actual code to run + **when** to run it | "Log before the method" — the method inside the aspect (`@Before`, `@After`, `@Around`…) |
| **Join point** | A point in program execution where advice *could* run | In Spring AOP, always a **method call** |
| **Pointcut** | An **expression that selects** which join points to match | "All methods in the `service` package" — the filter |
| **Weaving** | The act of **plugging** the advice into the target at those points | Spring does this at runtime by creating a proxy |

```
             ┌──────────────── ASPECT (@Aspect class) ────────────────┐
             │                                                        │
   POINTCUT  │   "which methods?"   ─── selects ───▶  JOIN POINTS     │
 (expression)│                                        (method calls)  │
             │                                            │           │
   ADVICE    │   "what code + when" ─── runs at ─────────▶┘           │
 (@Before…)  │                                                        │
             └────────────────────────────────────────────────────────┘
                              │
                          WEAVING = wiring advice into targets (via proxy at runtime)
```

**One-line mnemonic:**
- **Aspect** = the *what* feature (the whole module)
- **Advice** = the *code* + *when*
- **Join point** = a *place* it can run (a method)
- **Pointcut** = a *filter* choosing which places
- **Weaving** = the *plugging-in* act

---

## 3. Your key use case: hooking into library methods you can't edit

> *"When we use a library and want to log some methods inside the library that we
> don't have access to, AOP helps invoke our external methods inside the library
> methods."*

This is exactly what AOP is for. You **cannot** open the library's source and add
`log.info(...)`, but you **can** write an aspect whose pointcut **matches** the
library's methods, and Spring **weaves** your advice around them via a proxy.

```
   your code ──▶ [ PROXY of library bean ] ──▶ real library method
                        │
                        ├─ @Before: your log runs here  ◀── YOUR external method
                        ├─ call the library method
                        └─ @After:  your log runs here
```

```java
@Aspect
@Component
public class LibraryLoggingAspect {

    // pointcut: match every method inside the library's package
    @Before("execution(* com.thirdparty.library..*(..))")
    public void logBefore(JoinPoint jp) {
        // THIS is your external method being invoked inside the library call
        System.out.println("[LIB CALL] " + jp.getSignature());
    }
}
```

> **Caveat:** Spring AOP only weaves **Spring-managed beans** (proxied objects).
> If the library object is created by Spring (a bean), this works out of the box.
> If the library does `new SomeClass()` internally, Spring's proxy can't
> intercept it — you'd need **AspectJ compile-time/load-time weaving** (heavier,
> weaves bytecode directly, not limited to Spring beans). For most "log the
> service/repository calls" needs, plain Spring AOP is enough.

---

## 4. The 5 advice types (the "when")

Advice = code + **when** it runs relative to the matched method.

| Advice | Runs… | Typical use |
|--------|-------|-------------|
| `@Before` | Before the method | Logging entry, security check, validation |
| `@After` | After the method (finally — always, even on exception) | Cleanup, "method finished" log |
| `@AfterReturning` | After it returns **normally** (can read the return value) | Log/transform the result |
| `@AfterThrowing` | Only if it **throws** (can read the exception) | Error logging, alerting |
| `@Around` | **Wraps** the method — you control before **and** after, and whether it even runs | Timing, caching, transactions, retries |

`@Around` is the most powerful — it gets a `ProceedingJoinPoint` and must call
`proceed()` to run the real method:

```java
@Aspect
@Component
public class TimingAspect {

    @Around("execution(* com.myapp.service..*(..))")
    public Object measure(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();          // BEFORE

        Object result = pjp.proceed();           // ← run the real method

        long ms = (System.nanoTime() - start) / 1_000_000;   // AFTER
        System.out.println(pjp.getSignature().getName() + " took " + ms + "ms");
        return result;                           // you may modify/replace this
    }
}
```

> If you **don't** call `pjp.proceed()`, the real method never runs (this is how
> `@Cacheable` skips the method on a cache hit — see
> [`9-Proxy-In-Spring-Boot`](./9-Proxy-In-Spring-Boot.md) §5).

---

## 5. Pointcut expressions (the "which")

The pointcut is a mini-language that selects join points. The most common is
`execution(...)`:

```
execution( modifiers?  return-type  package.Class.method(args)  )

execution( *            *            com.myapp.service.*.*(..)   )
             │            │            │        │       │  │
             │            │            │        │       │  └─ (..) = any arguments
             │            │            │        │       └──── any method name
             │            │            │        └──────────── any class in the package
             │            │            └───────────────────── the package
             │            └──────────────────────────────────  any return type
             └───────────────────────────────────────────────  any modifier (public/…)
```

Examples:

```java
// any method, any args, in the service package (one level)
@Before("execution(* com.myapp.service.*.*(..))")

// any method in service package AND all sub-packages  (note the "..")
@Before("execution(* com.myapp.service..*(..))")

// only methods returning String, named findX, taking a Long
@Before("execution(String com.myapp.*.find*(Long))")

// match by annotation: any method annotated @Loggable
@Before("@annotation(com.myapp.Loggable)")

// match all methods of classes annotated @Service
@Before("within(@org.springframework.stereotype.Service *)")
```

**Reusable named pointcut** — declare once, reference by name:

```java
@Aspect
@Component
public class LoggingAspect {

    // named pointcut (empty method = just holds the expression)
    @Pointcut("execution(* com.myapp.service..*(..))")
    public void serviceMethods() {}

    @Before("serviceMethods()")                 // reference it by name
    public void logEntry(JoinPoint jp) {
        System.out.println("→ " + jp.getSignature().toShortString());
    }

    @AfterReturning(pointcut = "serviceMethods()", returning = "result")
    public void logExit(JoinPoint jp, Object result) {
        System.out.println("← " + jp.getSignature().getName() + " = " + result);
    }
}
```

---

## 6. Full working example (custom `@Loggable` annotation)

A clean, annotation-driven aspect — you tag any method with `@Loggable` and it
gets logged. No changes to the business method's body.

```java
// 1) the marker annotation
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)     // must be visible at runtime (reflection)
@Target(ElementType.METHOD)
public @interface Loggable { }
```

```java
// 2) the aspect
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LoggingAspect {

    @Around("@annotation(Loggable)")     // match any method tagged @Loggable
    public Object log(ProceedingJoinPoint pjp) throws Throwable {
        String name = pjp.getSignature().getName();
        System.out.println("[before] " + name);
        try {
            Object result = pjp.proceed();               // run the real method
            System.out.println("[after]  " + name + " returned " + result);
            return result;
        } catch (Throwable t) {
            System.out.println("[error]  " + name + " threw " + t);
            throw t;                                      // rethrow — don't swallow
        }
    }
}
```

```java
// 3) use it — business code stays clean
@Service
public class OrderService {

    @Loggable
    public String placeOrder(String item) {
        return "ordered " + item;
    }
}
```

```
placeOrder("book")
  [before] placeOrder
  [after]  placeOrder returned ordered book
```

> Requires `spring-boot-starter-aop` on the classpath. Spring Boot auto-enables
> AOP; in plain Spring add `@EnableAspectJAutoProxy`.

---

## 7. How weaving actually works (ties back to proxies)

Spring AOP does **runtime weaving via proxies**:

1. Spring finds beans matched by any pointcut.
2. It wraps each in a **proxy** (JDK dynamic proxy if the bean has an interface,
   else CGLIB subclass — see [`9-Proxy-In-Spring-Boot`](./9-Proxy-In-Spring-Boot.md) §2–4).
3. The proxy's intercepted call runs your **advice** before/after/around the real
   method.

```
Spring AOP    = weaving at RUNTIME via proxies   (only Spring beans, public methods)
AspectJ       = weaving at COMPILE / LOAD time via bytecode  (any object, any method,
                even self-invocation & library internals) — heavier, more powerful
```

**Same gotchas as proxies** (because it *is* proxies):
- **Self-invocation bypasses AOP** — one method calling another `@Loggable`
  method on `this` skips the proxy, so the advice doesn't fire.
- **Only `public` methods** are advised by Spring AOP.
- Advice runs only when the call goes **through the proxy** (the injected bean).

---

### Quick recap
- **AOP** = keep cross-cutting concerns (logging, security, transactions,
  caching, metrics) **out** of business code, written once and applied
  automatically.
- **Aspect** = the module (`@Aspect` class); **Advice** = the code + when
  (`@Before/@After/@Around/@AfterReturning/@AfterThrowing`); **Join point** = a
  method-execution point; **Pointcut** = the expression selecting which methods;
  **Weaving** = plugging advice into targets (Spring does it at runtime via a
  proxy).
- **Key use case:** log/intercept **library methods you can't edit** — write an
  aspect whose pointcut matches them; Spring weaves your advice in (works for
  Spring-managed beans; library `new`-ed internals need AspectJ).
- **`@Around`** is the most powerful — it wraps the method and must call
  `proceed()` to run it (skip it to short-circuit, e.g. caching).
- **Pointcut** most common form: `execution(* pkg..*(..))`; also
  `@annotation(...)`, `within(...)`; reuse with `@Pointcut`.
- **Same proxy gotchas:** self-invocation bypasses it; only `public` methods;
  needs `spring-boot-starter-aop`.
