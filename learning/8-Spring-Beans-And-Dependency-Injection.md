# Spring Beans & Dependency Injection — Notes

## 1. The Core Idea: IoC Container & Beans

- A **Bean** is just an object that is **created, configured, and managed by
  Spring** (not by you calling `new`).
- The **IoC container** (Inversion of Control) is what creates and wires those
  beans. "Inversion of control" = *you* don't create objects, the *container*
  does and hands them to you.
- **ApplicationContext** is the container. It reads your configuration (XML,
  annotations, or Java config), builds the beans, and gives them to you on
  request.

```
 Configuration (XML / annotations)
            │
            ▼
   ApplicationContext  ──creates & manages──▶  [ Bean ]  (default: singleton)
            │
            ▼
   context.getBean("id")  ──▶  the ready-to-use object
```

### Default scope = singleton
- By default Spring creates **one single instance** of each bean for the whole
  container and **reuses** it everywhere.
- Ask for the same bean twice → you get the **same object** back.

```java
ApplicationContext context =
        new ClassPathXmlApplicationContext("beans.xml");

UserService a = context.getBean("userService", UserService.class);
UserService b = context.getBean("userService", UserService.class);

System.out.println(a == b); // true  -> same instance (singleton)
```

Other scopes exist (`prototype` = new object each time, plus web scopes
`request` / `session`), but **singleton is the default**.

```xml
<!-- singleton (default) vs prototype -->
<bean id="userService" class="com.example.UserService" scope="singleton"/>
<bean id="report"      class="com.example.Report"      scope="prototype"/>
```

### The two container interfaces: `BeanFactory` vs `ApplicationContext`

The IoC container comes in two flavours — `ApplicationContext` is a superset of
`BeanFactory` and is what you almost always use.

| | `BeanFactory` | `ApplicationContext` (use this) |
|--|---------------|---------------------------------|
| Role | Basic container, bare-minimum DI | Full container, built on top of `BeanFactory` |
| Bean creation | **Lazy** — a bean is created only when first requested | **Eager** — singletons are created **at startup** |
| Extras | Just `getBean()` | Events, i18n messages, annotation support, AOP, auto `BeanPostProcessor` |
| When used | Very memory-constrained / rare | Almost always (and Spring Boot uses it) |

```java
// BeanFactory (lazy, minimal) — rarely used directly
BeanFactory factory = new XmlBeanFactory(new ClassPathResource("beans.xml"));

// ApplicationContext (eager singletons, full features) — the normal choice
ApplicationContext context = new ClassPathXmlApplicationContext("beans.xml");
```

### Bean lifecycle (what the container does for each bean)

```
1. Instantiate    -> container calls constructor / factory method
2. Populate       -> inject dependencies (constructor-arg / @Autowired / setters)
3. Initialize     -> call init hook  (init-method / @PostConstruct)
   ── bean is now READY and in use ──
4. Destroy        -> call destroy hook (destroy-method / @PreDestroy) on shutdown
```

```java
public class UserService {
    @PostConstruct                       // runs after dependencies are injected
    public void init()   { System.out.println("bean ready"); }

    @PreDestroy                          // runs before the container shuts down
    public void cleanup(){ System.out.println("bean destroyed"); }
}
```
```xml
<bean id="userService" class="com.example.UserService"
      init-method="init" destroy-method="cleanup"/>
```

> **Key point for the next section:** an `ApplicationContext` builds **all**
> singleton beans **eagerly at startup**. That is the default — *lazy loading*
> changes exactly this timing.

---

## 1b. Lazy Loading — create the bean only when it's first needed

By default a singleton bean is created **eagerly** (at container startup). With
**lazy loading**, Spring **delays** creating the bean until the **first time it
is actually requested** (via `getBean()` or because another bean needs it).

```
Eager (default): startup ──▶ [bean created] ... first use
Lazy:            startup ──▶ (nothing) ... first use ──▶ [bean created here]
```

### How to enable it

**XML — per bean:**
```xml
<bean id="heavyService" class="com.example.HeavyService" lazy-init="true"/>
```

**XML — make every bean lazy by default:**
```xml
<beans default-lazy-init="true"> ... </beans>
```

**Annotations / Spring Boot — `@Lazy`:**
```java
@Service
@Lazy                               // this bean is created only on first use
public class HeavyService {
    public HeavyService() {
        System.out.println("HeavyService constructed"); // watch WHEN this prints
    }
}
```

You can also lazily inject an otherwise-eager bean at the **injection point**:
```java
@Service
public class OrderService {
    // HeavyService is only instantiated when OrderService actually uses it
    public OrderService(@Lazy HeavyService heavy) { ... }
}
```

### Demonstration of the timing difference

```java
ApplicationContext ctx = new ClassPathXmlApplicationContext("beans.xml");
System.out.println("Context started");
// If lazy-init="true":  "HeavyService constructed" has NOT printed yet.
// If eager (default):   it already printed during context startup.

HeavyService h = ctx.getBean("heavyService", HeavyService.class);
System.out.println("Bean fetched");
// NOW (lazy) it prints "HeavyService constructed" — created on first request.
```

### Eager vs Lazy — when to use which

| | **Eager** (default) | **Lazy** (`lazy-init` / `@Lazy`) |
|--|---------------------|----------------------------------|
| Created | At startup | On first use |
| Startup time | Slower (builds everything) | Faster (defers work) |
| Config errors | Surface **immediately** at startup | Surface **later**, only when the bean is used |
| Memory | Holds all beans from the start | Only what's been touched |
| Good for | Most beans; you want fail-fast | Rarely-used, heavy, or optional beans (e.g. an expensive report generator) |

> **Caveats:**
> - Lazy hides misconfiguration until runtime — you lose Spring's helpful
>   "fail-fast at startup" behaviour.
> - `@Lazy` on a bean that everything uses is pointless (it's needed immediately).
> - `prototype` beans are inherently lazy (created on each request anyway).

---

## 2. Defining a Service as a Bean using XML

Say we have a plain service class:

```java
package com.example;

public class UserService {
    public void greet() {
        System.out.println("Hello from UserService");
    }
}
```

Register it as a bean in an XML config file (`beans.xml`):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
           http://www.springframework.org/schema/beans/spring-beans.xsd">

    <!-- id  = the name you look the bean up by
         class = the fully-qualified class Spring should instantiate -->
    <bean id="userService" class="com.example.UserService"/>

</beans>
```

Use it:

```java
ApplicationContext context =
        new ClassPathXmlApplicationContext("beans.xml");

UserService service = context.getBean("userService", UserService.class);
service.greet(); // Hello from UserService
```

> Spring instantiates `UserService` for you (via its default constructor) the
> moment the container starts, and hands you the same instance whenever you ask.

---

## 3. Wiring Dependencies — with `ref` (XML, no annotations)

Now suppose one bean **needs another**. A `UserService` depends on a
`UserRepository`.

```java
package com.example;

public class UserRepository {
    public String findName() {
        return "Alice";
    }
}
```

```java
package com.example;

public class UserService {

    private final UserRepository repository;

    // dependency comes IN through the constructor
    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public void greet() {
        System.out.println("Hello, " + repository.findName());
    }
}
```

### Wire it with `constructor-arg` + `ref`

`ref` means "**inject another bean** (by its id) here", instead of a literal
value.

```xml
<beans ...>

    <!-- the dependency bean -->
    <bean id="userRepository" class="com.example.UserRepository"/>

    <!-- inject userRepository into userService's constructor -->
    <bean id="userService" class="com.example.UserService">
        <constructor-arg ref="userRepository"/>
    </bean>

</beans>
```

```java
ApplicationContext context =
        new ClassPathXmlApplicationContext("beans.xml");

UserService service = context.getBean("userService", UserService.class);
service.greet(); // Hello, Alice   <- repository was injected by Spring
```

**Injecting literal values** (not beans) uses `value` instead of `ref`:

```xml
<bean id="userService" class="com.example.UserService">
    <constructor-arg ref="userRepository"/>   <!-- another bean -->
    <constructor-arg value="production"/>     <!-- a plain String -->
</bean>
```

**Setter injection** is the alternative to constructor injection:

```xml
<bean id="userService" class="com.example.UserService">
    <property name="repository" ref="userRepository"/>  <!-- calls setRepository(...) -->
</bean>
```

---

## 4. Calling a Bean *with* annotations (`@Autowired`)

Instead of writing `<constructor-arg ref="...">` for every wire, annotations let
Spring inject automatically **by type**.

Two things are needed: (a) tell Spring to scan for annotations, (b) mark the
classes as beans.

```xml
<beans ...
    xmlns:context="http://www.springframework.org/schema/context"
    xsi:schemaLocation="... http://www.springframework.org/schema/context
        http://www.springframework.org/schema/context/spring-context.xsd">

    <!-- turn on annotation scanning for this package -->
    <context:component-scan base-package="com.example"/>

</beans>
```

```java
package com.example;

import org.springframework.stereotype.Repository;

@Repository                      // marks this class as a Spring bean
public class UserRepository {
    public String findName() { return "Alice"; }
}
```

```java
package com.example;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service                         // marks this class as a Spring bean
public class UserService {

    private final UserRepository repository;

    @Autowired                   // Spring injects a UserRepository bean by type
    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public void greet() {
        System.out.println("Hello, " + repository.findName());
    }
}
```

> **With annotation vs without:**
> - **Without** (§3): you explicitly say `<constructor-arg ref="userRepository"/>`.
> - **With** (`@Autowired` + `@Service`/`@Repository` + `component-scan`): Spring
>   finds the beans and matches them **by type** automatically — no `ref` needed.
>
> `@Component` is the generic stereotype; `@Service`, `@Repository`,
> `@Controller` are specialized `@Component`s that also convey *role*.

---

## 5. Factory Methods — custom bean creation

Sometimes Spring should **not** just call `new YourClass()`. Maybe the object
comes from a factory, needs special setup, or the constructor is private. Use a
**factory method**: Spring calls *your method* and registers whatever it returns
as the bean.

There are three common forms.

### 5a. Static factory method (no default constructor)

The class exposes a `static` method that builds the instance.

```java
package com.example;

public class Connection {

    private Connection() { }                 // private -> can't `new` it directly

    // static factory method
    public static Connection create() {
        System.out.println("Connection created via static factory");
        return new Connection();
    }
}
```

```xml
<!-- factory-method points Spring at the static method to call -->
<bean id="connection"
      class="com.example.Connection"
      factory-method="create"/>
```

```java
Connection c = context.getBean("connection", Connection.class);
// Spring called Connection.create() instead of `new Connection()`
```

### 5b. Instance factory method (default constructor + a method)

Here a **separate factory bean** is created normally (default constructor), then
one of *its instance methods* produces the target bean.

```java
package com.example;

public class ConnectionFactory {              // created with its default constructor

    public Connection buildConnection() {     // instance (non-static) method
        System.out.println("Connection built via instance factory");
        return new Connection();
    }
}
```

```xml
<!-- 1) create the factory bean the normal way -->
<bean id="connectionFactory" class="com.example.ConnectionFactory"/>

<!-- 2) create the target bean by calling a method ON that factory bean -->
<bean id="connection"
      factory-bean="connectionFactory"
      factory-method="buildConnection"/>
```

> `factory-method` alone = **static** method on the `class`.
> `factory-bean` + `factory-method` = **instance** method on another bean.

### 5c. Static factory method that itself needs a dependency (DI into the factory)

The factory method takes arguments, and Spring injects **other beans** into it
via `constructor-arg` (yes, `constructor-arg` also supplies factory-method args).

```java
package com.example;

public class Connection {

    private final Config config;

    private Connection(Config config) { this.config = config; }

    // static factory that DEPENDS ON a Config bean
    public static Connection create(Config config) {
        System.out.println("Connection created with url: " + config.getUrl());
        return new Connection(config);
    }
}
```

```java
package com.example;

public class Config {
    private final String url;
    public Config(String url) { this.url = url; }
    public String getUrl() { return url; }
}
```

```xml
<!-- the dependency bean -->
<bean id="config" class="com.example.Config">
    <constructor-arg value="jdbc://localhost:5432/mydb"/>
</bean>

<!-- static factory method, with the Config bean injected as its argument -->
<bean id="connection"
      class="com.example.Connection"
      factory-method="create">
    <constructor-arg ref="config"/>   <!-- passed INTO create(Config) -->
</bean>
```

```java
Connection c = context.getBean("connection", Connection.class);
// Spring called Connection.create(config) with the injected Config bean
```

**Recap of the three factory forms:**

| Form | XML | What Spring calls |
|------|-----|-------------------|
| Static, no args | `factory-method="create"` | `YourClass.create()` |
| Instance method | `factory-bean="f"` + `factory-method="build"` | `f.build()` on the factory bean |
| Static + dependency | `factory-method="create"` + `<constructor-arg ref="..."/>` | `YourClass.create(dep)` |

---

## 6. Dependency Injection (DI) — the big picture

**Dependency Injection** = a bean does **not** create the things it depends on;
the container **supplies** them from outside. This is *how* IoC (§1) is achieved.

### Why DI?
- **Loose coupling** — depend on an interface, not a concrete `new`.
- **Testability** — inject a mock/fake in tests.
- **No manual wiring** — the container builds the whole object graph for you.

**Without DI (tight coupling):**
```java
public class UserService {
    // UserService is welded to this exact class; hard to test/swap
    private final UserRepository repo = new UserRepository();
}
```

**With DI (loose coupling):**
```java
public class UserService {
    private final UserRepository repo;
    public UserService(UserRepository repo) { this.repo = repo; } // handed in
}
```

### The three types of injection

| Type | How | XML | Annotation |
|------|-----|-----|------------|
| **Constructor** (preferred) | via constructor params | `<constructor-arg ref="..."/>` | `@Autowired` on constructor |
| **Setter** | via setter methods | `<property name="..." ref="..."/>` | `@Autowired` on setter |
| **Field** | injected straight into a field | — | `@Autowired` on field |

```java
// Constructor injection (recommended: fields can be final, easy to test)
@Service
public class UserService {
    private final UserRepository repo;
    @Autowired                                    // optional if it's the only constructor
    public UserService(UserRepository repo) { this.repo = repo; }
}

// Setter injection
@Service
public class UserService {
    private UserRepository repo;
    @Autowired
    public void setRepo(UserRepository repo) { this.repo = repo; }
}

// Field injection (concise, but hard to test — avoid in real code)
@Service
public class UserService {
    @Autowired
    private UserRepository repo;
}
```

### `@Autowired` + `@Qualifier` (choosing among many candidates)

If two beans match the same type, disambiguate by name:

```java
@Service
public class UserService {
    @Autowired
    public UserService(@Qualifier("mysqlRepo") UserRepository repo) { ... }
}
```

---

## 7. Spring Boot: how this changes in practice

Everything above is the **classic (XML) foundation**. In **Spring Boot** you
rarely write XML — but the concepts are identical:

- **No XML** — configuration is Java-based / auto-configured.
- `@SpringBootApplication` triggers **component scanning** automatically (no
  `<context:component-scan>`).
- Beans come from `@Component` / `@Service` / `@Repository` / `@Controller`, or
  from `@Bean` methods inside a `@Configuration` class.
- `@Autowired` still injects them (and is optional on a single constructor).

**Java `@Bean` = the modern equivalent of a `<bean>` / factory method:**

```java
@Configuration
public class AppConfig {

    @Bean                         // this method IS the factory; return value = the bean
    public UserRepository userRepository() {
        return new UserRepository();
    }

    @Bean
    public UserService userService(UserRepository repo) {  // repo injected by Spring
        return new UserService(repo);                      // like constructor-arg ref
    }
}
```

| XML (classic) | Spring Boot / annotations |
|---------------|---------------------------|
| `<bean id="x" class="..."/>` | `@Component` / `@Service` on the class, or `@Bean` method |
| `<constructor-arg ref="y"/>` | `@Autowired` constructor / `@Bean` method parameter |
| `factory-method="create"` | a `@Bean` method that calls the factory and returns it |
| `<context:component-scan>` | automatic via `@SpringBootApplication` |

---

### Quick recap
- **IoC container:** `ApplicationContext` (eager, full-featured) vs `BeanFactory`
  (lazy, minimal) — you use `ApplicationContext`. It runs each bean through the
  lifecycle: instantiate → inject → init → (destroy on shutdown).
- **Beans** are objects the container manages; default scope is **singleton**
  (one shared instance).
- **Lazy loading:** `lazy-init="true"` / `@Lazy` delays creation until first use
  (default is eager at startup); good for heavy/rarely-used beans, but hides
  config errors until runtime.
- **XML bean:** `<bean id="userService" class="com.example.UserService"/>`.
- **Wire without annotations:** `<constructor-arg ref="otherBeanId"/>`
  (`ref` = a bean, `value` = a literal).
- **Wire with annotations:** `@Service`/`@Repository` + `component-scan` +
  `@Autowired` → injected **by type**, no `ref`.
- **Factory methods:** static (`factory-method`), instance
  (`factory-bean` + `factory-method`), or static-with-dependency
  (`factory-method` + `constructor-arg ref`).
- **DI:** container supplies dependencies → loose coupling + testability;
  prefer **constructor injection**.
- **Spring Boot:** same ideas, no XML — `@Bean` methods and stereotypes replace
  `<bean>` and factory methods.
