# Proxy in Java / Spring Boot — Notes

## 1. What is a Proxy?

A **proxy** is an **intermediate object** that stands in front of the real
object. When you call a method, the call hits the **proxy first**, which can run
extra logic **before** (and **after**) delegating to the real method.

```
   caller ──▶ [ PROXY ]  ──▶  real object (target)
                 │
                 ├─ before: logging, security, caching check, start transaction...
                 ├─ call the real method
                 └─ after:  store cache, commit transaction, measure time...
```

- The caller **thinks** it's talking to the real object — same interface/type.
- The proxy **wraps** the target and injects cross-cutting behaviour around it.

### Why Spring uses proxies
This is the engine behind Spring's "magic" annotations. When you add
`@Transactional`, `@Cacheable`, `@Async`, `@PreAuthorize`, etc., Spring does
**not** modify your class — it wraps your bean in a **proxy** that adds that
behaviour around your method. This whole idea is **AOP** (Aspect-Oriented
Programming): keep cross-cutting concerns (logging, caching, transactions)
**out** of your business code.

> **Key consequence:** the annotation only works when the call goes **through
> the proxy**. A method calling *another* annotated method on `this` (same
> object) **bypasses the proxy** → the annotation is ignored (the classic
> "self-invocation" gotcha).

> **Runnable example:** [`projects/5-Proxy`](../projects/5-Proxy) — a from-scratch
> JDK dynamic proxy (`InvocationHandler`) that re-implements `@Cacheable` using
> reflection, no Spring. See its `README.md`.

### Use cases of proxy (why you should care)

A proxy is the single mechanism behind a huge amount of framework "magic". The
same before/delegate/after pattern, with different logic in the middle, gives:

| Use case | What the proxy does around the real method |
|----------|--------------------------------------------|
| **`@Cacheable`** | Build a key from method + args → return cached value on a hit, else run the method and store the result (see §5). |
| **`@Transactional`** | `begin` a DB transaction before the method → `commit` if it returns normally → `rollback` if it throws. Your method has zero transaction code. |
| **AOP (aspects)** | The general form: run cross-cutting "advice" (logging, metrics/timing, security `@PreAuthorize`, retries, `@Async`) **around** business methods, keeping that concern out of your code. `@Cacheable`/`@Transactional` are just pre-built aspects. |
| **Dynamic proxies (JDK)** | Generate an **interface** implementation at runtime (`Proxy.newProxyInstance` + `InvocationHandler`) — no hand-written wrapper class (§3). |
| **CGLIB proxies** | Generate a **subclass** at runtime for classes with **no interface** (`Enhancer` + `MethodInterceptor`) (§4). |
| **Reflection** | The runtime toolkit proxies rely on: at runtime, **inspect classes/methods/annotations and invoke them** without knowing them at compile time. |

**Reflection — the runtime "look inside" toolkit** that makes all of the above
possible. It lets code, while running, discover and use types it wasn't compiled
against:

```java
Class<?> clazz   = obj.getClass();                 // the runtime class...
String name      = clazz.getName();                // ...its fully-qualified name
Method[] methods = clazz.getDeclaredMethods();     // all methods (incl. private)
Field[]  fields  = clazz.getDeclaredFields();      // all fields

// read an annotation on a method (this is how @Cacheable is detected)
boolean cached = method.isAnnotationPresent(Cacheable.class);

// invoke a method dynamically by reference
Object result = method.invoke(target, args);

// access a PRIVATE member by breaking normal access checks
Field secret = clazz.getDeclaredField("password");
secret.setAccessible(true);                        // bypass `private`
Object value = secret.get(obj);                    // read it at runtime
```

- **Get class name / methods / fields at runtime** → `getName()`,
  `getDeclaredMethods()`, `getDeclaredFields()`.
- **Read annotations at runtime** → `isAnnotationPresent()` /
  `getAnnotation()` (the annotation must be `@Retention(RUNTIME)`).
- **Access `private` members** → `setAccessible(true)` (how Spring injects into
  private `@Autowired` fields).
- **Invoke methods dynamically** → `method.invoke(target, args)` — the exact call
  an `InvocationHandler` makes to reach the real method.

> In short: **reflection** inspects and drives code at runtime; **proxies** use
> reflection to insert behaviour; **AOP** is the design idea; `@Cacheable`,
> `@Transactional`, `@Async`, `@PreAuthorize` are the ready-made results.

---

## 2. Two kinds of Spring proxies

Spring creates the proxy in one of two ways depending on whether your bean
implements an interface.

| | **JDK Dynamic Proxy** | **CGLIB Proxy** |
|--|-----------------------|-----------------|
| Based on | **Interfaces** | **Class subclassing** |
| Requires | The target must implement an interface | Works on plain classes (no interface needed) |
| How | Generates a class implementing the same **interface** | Generates a **subclass** of your class at runtime |
| Built into | The **JDK** (`java.lang.reflect.Proxy`) | External lib **CGLIB** (bundled in Spring) |
| Limitation | Can only proxy methods **declared in the interface** | Can't proxy **`final` classes/methods** (can't subclass/override) |
| Spring default | Used if the bean implements an interface | Used if the bean has **no** interface |

> Spring Boot actually **defaults to CGLIB** for its proxies (since Spring Boot
> 2.0) via `proxyTargetClass=true`, but the JDK proxy is still used in plenty of
> places. Know both.

---

## 3. JDK Dynamic Proxy (interface-based)

Only works when the target implements an **interface**. The JDK generates a
proxy class that implements that same interface and routes every call through an
`InvocationHandler`.

```java
// 1) the interface
public interface PaymentService {
    void pay(int amount);
}

// 2) the real implementation (the "target")
public class PaymentServiceImpl implements PaymentService {
    @Override
    public void pay(int amount) {
        System.out.println("Paid " + amount);
    }
}
```

```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

// 3) the handler = the code that runs for EVERY method call on the proxy
public class LoggingHandler implements InvocationHandler {

    private final Object target;          // the real object we wrap

    public LoggingHandler(Object target) {
        this.target = target;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        System.out.println("[before] " + method.getName());   // extra logic BEFORE
        Object result = method.invoke(target, args);          // call the REAL method
        System.out.println("[after]  " + method.getName());   // extra logic AFTER
        return result;
    }
}
```

```java
// 4) build the proxy
public class Main {
    public static void main(String[] args) {
        PaymentService real = new PaymentServiceImpl();

        PaymentService proxy = (PaymentService) Proxy.newProxyInstance(
                real.getClass().getClassLoader(),         // classloader
                new Class[]{ PaymentService.class },        // interfaces to implement
                new LoggingHandler(real));                  // handler with our logic

        proxy.pay(100);
        // [before] pay
        // Paid 100
        // [after]  pay
    }
}
```

> The caller holds a `PaymentService` reference and never knows it's a proxy.
> `Proxy.newProxyInstance` needs the **interface** — that's why JDK proxies only
> work for interface-based beans.

---

## 4. CGLIB Proxy (class-level, subclassing)

When there is **no interface**, Spring uses **CGLIB**, which generates a
**subclass** of your class at runtime and **overrides** each method to insert
the proxy logic (then calls `super`).

```java
// a plain class — NO interface
public class OrderService {
    public void placeOrder() {
        System.out.println("Order placed");
    }
}
```

```java
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import java.lang.reflect.Method;

public class Main {
    public static void main(String[] args) {

        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(OrderService.class);   // subclass the target class

        enhancer.setCallback((MethodInterceptor) (obj, method, args1, proxy) -> {
            System.out.println("[before] " + method.getName());
            Object result = proxy.invokeSuper(obj, args1);   // call the REAL (super) method
            System.out.println("[after]  " + method.getName());
            return result;
        });

        OrderService proxy = (OrderService) enhancer.create(); // the generated subclass
        proxy.placeOrder();
        // [before] placeOrder
        // Order placed
        // [after]  placeOrder
    }
}
```

> Because CGLIB works by **subclassing + overriding**, it **cannot** proxy
> `final` classes or `final`/`private` methods (you can't override them). That's
> the main CGLIB limitation.

### Mental model: JDK vs CGLIB

```
JDK proxy:   proxy  implements  Interface   ──delegates to──▶  target instance
CGLIB proxy: proxy  extends     YourClass   ──calls super──▶   itself (overridden)
```

---

## 5. `@Cacheable` — how a proxy actually implements it

`@Cacheable` means: *"before running this method, check the cache. If the result
for these arguments is already there, return it and skip the method. Otherwise
run the method and store the result."* This is a textbook proxy behaviour.

### 5a. What Spring's proxy does (conceptually)

```
call getUser(5)
      │
      ▼
 [ PROXY ]  ── key = "getUser::5" ──▶ is it in cache?
      │                                   │
      │  yes ──▶ return cached value  ◀───┘   (method NEVER runs)
      │
      └─ no  ──▶ run real getUser(5) ──▶ put result in cache ──▶ return it
```

Using it in Spring is just the annotation (Spring builds the proxy for you):

```java
@Service
public class UserService {

    @Cacheable("users")                 // proxy caches by the method arguments
    public User getUser(Long id) {
        System.out.println("DB hit for " + id);   // prints only on a cache MISS
        return userRepository.findById(id).orElseThrow();
    }
}
```
```java
@SpringBootApplication
@EnableCaching                          // turns on the caching proxy machinery
public class App { ... }
```
```java
service.getUser(5L);  // DB hit for 5   -> miss, runs method, caches result
service.getUser(5L);  // (no DB hit)    -> hit, returns cached value via proxy
```

### 5b. Building the same thing by hand with a proxy

This is essentially what Spring generates — a JDK dynamic proxy whose handler
does the cache check.

```java
public interface UserService {
    String getUser(Long id);
}

public class UserServiceImpl implements UserService {
    @Override
    public String getUser(Long id) {
        System.out.println("DB hit for " + id);     // expensive work
        return "User-" + id;
    }
}
```

```java
import java.lang.reflect.*;
import java.util.HashMap;
import java.util.Map;

public class CacheableHandler implements InvocationHandler {

    private final Object target;
    private final Map<String, Object> cache = new HashMap<>();  // the cache store

    public CacheableHandler(Object target) { this.target = target; }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        // Only apply caching to methods marked @Cacheable (see 5c);
        // here we cache getUser for simplicity.
        String key = method.getName() + "::" + java.util.Arrays.toString(args);

        if (cache.containsKey(key)) {                 // CACHE HIT
            System.out.println("[cache hit] " + key);
            return cache.get(key);                    // real method is skipped
        }

        Object result = method.invoke(target, args);  // CACHE MISS -> run real method
        cache.put(key, result);                        // store for next time
        System.out.println("[cache store] " + key);
        return result;
    }
}
```

```java
public class Main {
    public static void main(String[] args) {
        UserService real = new UserServiceImpl();

        UserService proxy = (UserService) Proxy.newProxyInstance(
                real.getClass().getClassLoader(),
                new Class[]{ UserService.class },
                new CacheableHandler(real));

        proxy.getUser(5L); // DB hit for 5 + [cache store] getUser::[5]
        proxy.getUser(5L); // [cache hit] getUser::[5]   (no DB hit)
    }
}
```

### 5c. Making it honour a custom `@Cacheable` annotation

Real Spring checks each method for the annotation before caching. You can too:

```java
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)     // must be visible at runtime via reflection
@Target(ElementType.METHOD)
public @interface Cacheable { }
```

```java
public class UserServiceImpl implements UserService {
    @Cacheable                          // our custom marker
    @Override
    public String getUser(Long id) {
        System.out.println("DB hit for " + id);
        return "User-" + id;
    }
}
```

```java
@Override
public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

    // find the real method on the target to read its annotations
    Method targetMethod = target.getClass()
            .getMethod(method.getName(), method.getParameterTypes());

    // only cache if the method is annotated
    if (targetMethod.isAnnotationPresent(Cacheable.class)) {
        String key = method.getName() + "::" + java.util.Arrays.toString(args);
        if (cache.containsKey(key)) return cache.get(key);
        Object result = method.invoke(target, args);
        cache.put(key, result);
        return result;
    }

    // not annotated -> just delegate, no caching
    return method.invoke(target, args);
}
```

> This is the essence of Spring's cache abstraction: a proxy + an
> `InvocationHandler`/`MethodInterceptor` that reads `@Cacheable`, computes a
> **key** from the method + args, and consults a cache before invoking the real
> method. Spring adds real cache providers (Caffeine, Redis, EhCache), key
> generators, conditions (`condition`, `unless`), and eviction (`@CacheEvict`).

---

## 6. Practical gotchas (because it's a proxy)

- **Self-invocation fails.** Calling an annotated method from *another method in
  the same class* uses `this`, not the proxy, so `@Cacheable`/`@Transactional`
  is **skipped**. Call it through an injected reference / another bean instead.
- **Only `public` methods** are proxied by Spring's AOP (CGLIB can't override
  `private`/`final`, and Spring ignores non-public for these annotations).
- **`final` class/method + CGLIB = fails**, since CGLIB must subclass/override.
- The bean you get injected is the **proxy**, not your raw class — normal, but
  matters when debugging or comparing types.

---

### Quick recap
- **Proxy** = intermediate object that runs logic **before/after** delegating to
  the real method — the mechanism behind `@Transactional`, `@Cacheable`, etc.
- **Use cases:** `@Cacheable` (cache around), `@Transactional` (begin/commit/
  rollback around), `@Async`/`@PreAuthorize`/logging/metrics — all **AOP** (run
  cross-cutting advice around business methods).
- **Reflection** = the runtime toolkit proxies rely on: get class name / methods
  / fields, read annotations, access `private` (`setAccessible(true)`), and
  `method.invoke(...)` — all decided at runtime, not compile time.
- **JDK dynamic proxy** = interface-based (`Proxy.newProxyInstance` +
  `InvocationHandler`); proxies interface methods only.
- **CGLIB proxy** = class-based, generates a **subclass** (`Enhancer` +
  `MethodInterceptor`); needed when there's no interface; can't proxy `final`.
- **`@Cacheable`** = a proxy that builds a **key from method + args**, returns a
  cached value on a hit, else runs the method and stores the result; enabled
  with `@EnableCaching`.
- **Gotcha:** annotations only fire when the call goes **through the proxy**
  (self-invocation bypasses it).
