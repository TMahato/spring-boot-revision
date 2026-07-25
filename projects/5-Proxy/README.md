# Proxy with InvocationHandler (@Cacheable by hand)

A minimal, runnable example of a **JDK dynamic proxy** using `InvocationHandler`,
referenced from `learning/9-Proxy-In-Spring-Boot.md`. It re-creates Spring's
`@Cacheable` behaviour from scratch — no Spring, just the JDK.

## The idea

A **proxy** sits in front of the real object. Every method call hits the proxy
first, which can run extra logic before/after delegating to the real method.
Here the extra logic is **caching**:

```
service.getUser(5)  ──▶ [ PROXY / CacheableHandler ]
                              │  key = "getUser::[5]"
                              ├─ in cache?  yes ─▶ return cached, skip real method
                              └─ no ─▶ run real getUser(5), store result, return it
```

The business class (`UserServiceImpl`) contains **no caching code at all** — the
proxy adds it from outside. That separation is the essence of AOP.

## Files
```
5-Proxy/
└── Proxy/
    ├── Cacheable.java         <- our custom @Cacheable annotation (RUNTIME retention)
    ├── UserService.java       <- the interface (JDK proxy needs an interface)
    ├── UserServiceImpl.java   <- the real object; getUser() is @Cacheable
    ├── CacheableHandler.java  <- the InvocationHandler: reflection + cache logic
    └── Main.java              <- builds the proxy with Proxy.newProxyInstance
```

## How it works (the three moving parts)

1. **`Proxy.newProxyInstance(...)`** generates a proxy object that implements
   `UserService`, so callers use it like any `UserService`.
2. **`CacheableHandler.invoke(...)`** runs for *every* call on the proxy.
3. **Reflection** (`targetMethod.isAnnotationPresent(Cacheable.class)`) checks at
   runtime whether the method is annotated, and only then caches — using a key
   built from the method name + arguments.

## Run it

From this folder (`projects/5-Proxy`):
```bash
javac Proxy/*.java
java Proxy.Main
```

Expected output:
```
--- @Cacheable method (getUser) ---
[cache MISS] getUser::[5] -> running real method
   >> DB hit for user 5 (slow work ran)
result: User-5
[cache HIT ] getUser::[5] -> real method skipped
result: User-5
[cache MISS] getUser::[9] -> running real method
   >> DB hit for user 9 (slow work ran)
result: User-9

--- non-cached method (getTime) runs every time ---
time=...   (two DIFFERENT values -> proves it is NOT cached)
```

Notice: `getUser(5)` runs the "DB" only on the **first** call; the second call
is served from cache (no DB line). `getUser(9)` is a different argument, so it's
a fresh miss. `getTime()` has no `@Cacheable`, so it runs every time.

## Ties to chapter 9

- **JDK dynamic proxy** — `Proxy.newProxyInstance` + `InvocationHandler`.
- **Reflection** — reading annotations and invoking the real method at runtime.
- **@Cacheable** — implemented here exactly as Spring does it conceptually.
- Swap the caching logic for "start/commit transaction" and you have
  `@Transactional`; that generality is **AOP**.
