# Learnings from the Ticket Booking Project — Predicates, Serialization, Lombok, Jackson & `static` (Interview Ready)

A practical companion note built entirely from the **Train Ticket Booking** project. Where note 5.1 explained generics and type erasure in theory, this note shows them **doing real work**: filtering users with predicates, turning objects into JSON files, and calling helper methods from anywhere.

Every example here comes from actual project code (`UserBookingService`, `TrainService`, `UserServiceUtil`, `User`, `Train`).

---

## Table of Contents

1. [What is a Predicate?](#1-what-is-a-predicate)
2. [Predicates in the project — `filter`, `findFirst`, `removeIf`](#2-predicates-in-the-project--filter-findfirst-removeif)
3. [What is Serialization & Deserialization?](#3-what-is-serialization--deserialization)
4. [`ObjectMapper` — the Jackson engine](#4-objectmapper--the-jackson-engine)
5. [Why `TypeReference` is needed](#5-why-typereference-is-needed)
6. [The full serialize / deserialize flow in the project](#6-the-full-serialize--deserialize-flow-in-the-project)
7. [Lombok — killing boilerplate](#7-lombok--killing-boilerplate)
8. [`static` — calling methods from anywhere without an object](#8-static--calling-methods-from-anywhere-without-an-object)
9. [How all of these connect in one request](#9-how-all-of-these-connect-in-one-request)
10. [Frequently Asked Interview Questions](#frequently-asked-interview-questions)
11. [Summary](#summary)

---

## 1. What is a Predicate?

A **`Predicate<T>`** is a functional interface that takes one input and returns a **`boolean`**.

> *"Given one thing, answer yes or no."*

```java
@FunctionalInterface
public interface Predicate<T> {
    boolean test(T t);
}
```

Because it has exactly **one abstract method**, you can supply it with a **lambda**:

```java
Predicate<User> isAlice = user -> user.getName().equals("alice");

isAlice.test(someUser);   // true or false
```

That lambda `user -> user.getName().equals("alice")` **is** the `Predicate`. You never wrote `new Predicate<>() { ... }` — the compiler did.

### Why it matters

Predicates let you pass **behavior** (a condition) into a method as if it were data. Stream operations like `filter`, `anyMatch`, and collection methods like `removeIf` all accept a `Predicate`.

---

## 2. Predicates in the project — `filter`, `findFirst`, `removeIf`

### `filter` — search trains (a stream of Predicates)

From `TrainService.searchTrains`:

```java
public List<Train> searchTrains(String source, String destination) {
    return trainList.stream()
            .filter(train -> validTrain(train, source, destination))  // Predicate<Train>
            .collect(Collectors.toList());
}
```

- `train -> validTrain(...)` is a `Predicate<Train>`.
- `filter` keeps only the trains for which `test(...)` returns `true`.

### `findFirst` after `filter` — login check

From `UserBookingService.loginUser`:

```java
Optional<User> foundUser = userList.stream().filter(user1 ->
        user1.getName().equals(user.getName()) &&
        user1.getPassword().equals(user.getPassword())
).findFirst();

return foundUser.isPresent();
```

- The lambda passed to `filter` is a `Predicate<User>` combining **two** conditions with `&&`.
- `findFirst()` returns an `Optional<User>` — see note 5 for why `Optional` beats returning `null`.

### `removeIf` — cancel a booking

From `UserBookingService.cancelBooking`:

```java
boolean removed = user.getTickets()
        .removeIf(ticket -> ticket.getTicketId().equals(finalTicketId));  // Predicate<Ticket>
```

`removeIf` walks the list and **deletes every element** where the predicate returns `true`. One line replaces a manual iterator loop.

> **Interview point:** `filter` is *lazy* (part of a stream pipeline, only runs on a terminal operation like `collect`/`findFirst`). `removeIf` is *eager* (mutates the collection immediately). Both take a `Predicate`.

---

## 3. What is Serialization & Deserialization?

The project has **no real database** — it stores data in JSON files (`users.json`, `trains.json`). To move between Java objects and those files, we convert in both directions.

| Term | Direction | In the project |
|------|-----------|----------------|
| **Serialization** | Java object → text/bytes (JSON) | Saving a `User` to `users.json` |
| **Deserialization** | text/bytes (JSON) → Java object | Loading `users.json` into `List<User>` |

```
   Java World                         File World
+--------------+   serialize   +----------------------+
|  User object | ------------> |  { "name": "alice" } |
+--------------+               +----------------------+
+--------------+  deserialize  +----------------------+
|  User object | <------------ |  { "name": "alice" } |
+--------------+               +----------------------+
```

Serialization is how an in-memory object **survives** after the program ends (persistence), and how data **travels** (files, network, APIs).

---

## 4. `ObjectMapper` — the Jackson engine

`ObjectMapper` (from the **Jackson** library) is the object that actually performs serialization and deserialization.

```java
private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
```

Two core methods used in the project:

### Deserialize — read JSON into objects

```java
// UserBookingService.loadUserListFromFile
userList = objectMapper.readValue(new File(USER_FILE_PATH),
        new TypeReference<List<User>>() {});
```

### Serialize — write objects back to JSON

```java
// UserBookingService.saveUserListToFile
objectMapper.writeValue(new File(USER_FILE_PATH), userList);
```

### Why `registerModule(new JavaTimeModule())`?

`Ticket.dateOfTravel` is a `java.time.LocalDate`. Out of the box, Jackson does **not** know how to read `"2026-08-01"` into a `LocalDate` — that support lives in a separate module (`jackson-datatype-jsr310`). Registering it teaches the mapper the java.time types. Without it, loading a ticket throws at runtime.

> **Real bug we hit:** `Train.stationTime` was first typed `Map<String, java.sql.Time>`. Jackson cannot parse `"13:50:00"` into `java.sql.Time`, so deserialization failed. Switching to `Map<String, String>` fixed it. **Lesson: the field type must be something Jackson can actually map to the JSON value.**

---

## 5. Why `TypeReference` is needed

This is where **note 5.1 (Type Erasure)** pays off. Look again:

```java
objectMapper.readValue(file, new TypeReference<List<User>>() {});
```

Why not just `readValue(file, List.class)`?

Because of **type erasure**. At runtime, `List<User>` and `List<Train>` are **both just `List`** — the `<User>` / `<Train>` information is erased. If you pass `List.class`, Jackson builds a `List<Object>` where each element is a generic `LinkedHashMap`, **not** a `User`. You'd get `ClassCastException` the moment you called `user.getName()`.

### The trick

`TypeReference` is an **abstract class**. When you write:

```java
new TypeReference<List<User>>() {}   // note the {} — anonymous subclass
```

you create an **anonymous subclass** of `TypeReference`. The generic type `List<User>` becomes part of that subclass's **signature**, which the compiler bakes into class metadata. Jackson reads that metadata via reflection and recovers the **full** generic type `List<User>` — the one piece of information erasure would otherwise throw away.

```
List.class                    →  Jackson sees: "a List of ???"  →  List<LinkedHashMap>
new TypeReference<List<User>>(){}  →  Jackson sees: "a List of User"  →  List<User>  ✅
```

| Approach | Runtime knows element type? | Result |
|----------|-----------------------------|--------|
| `List.class` | ❌ erased to `List` | `List<LinkedHashMap>` → cast errors |
| `new TypeReference<List<User>>(){}` | ✅ preserved in subclass | correct `List<User>` |

> **Interview gold:** `TypeReference` is the standard workaround for type erasure when a library needs the *full* generic type at runtime. The `{}` (anonymous subclass) is what preserves it.

---

## 6. The full serialize / deserialize flow in the project

```
App starts
   │
   ▼
new UserBookingService()
   │  loadUserListFromFile()
   ▼
objectMapper.readValue(users.json, TypeReference<List<User>>)   ← DESERIALIZE
   │
   ▼
List<User> userList  (real User objects, tickets, trains, LocalDate…)
   │
   │  user signs up → userList.add(newUser)
   ▼
saveUserListToFile()
   │
   ▼
objectMapper.writeValue(users.json, userList)                   ← SERIALIZE
   │
   ▼
users.json updated on disk
```

For this to work, each entity (`User`, `Ticket`, `Train`) needs:

1. A **no-argument constructor** (Jackson creates an empty object first).
2. **Getters/setters** (or public fields) so Jackson can read/populate values.
3. Field types Jackson can map (hence the `Time` → `String` fix, and the `JavaTimeModule` for `LocalDate`).

---

## 7. Lombok — killing boilerplate

Every entity in this project is mostly **getters, setters, constructors, `toString`** — dozens of near-identical lines. **Lombok** is a library that generates that boilerplate for you at compile time via annotations.

### Our hand-written `Train` (abbreviated)

```java
public class Train {
    private String trainId;
    private String trainNo;
    // ... fields ...

    public Train() { }
    public Train(String trainId, ...) { this.trainId = trainId; ... }

    public String getTrainId() { return trainId; }
    public void setTrainId(String trainId) { this.trainId = trainId; }
    // ...repeat for every field...
}
```

### The Lombok version (what the STEPS.md guide uses)

```java
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data                 // getters + setters + toString + equals + hashCode
@Builder              // fluent builder: Train.builder().trainId("bacs").build()
@NoArgsConstructor    // public Train() {}
@AllArgsConstructor   // constructor with every field
public class Train {
    private String trainId;
    private String trainNo;
    private List<List<Integer>> seats;
    private Map<String, String> stationTime;
    private List<String> stations;
}
```

Same class, ~5 fields instead of ~80 lines.

### How does it work? (interview point)

Lombok is an **annotation processor**. During compilation it **injects** the getter/setter/constructor methods into the generated bytecode. That's why the build file needs it in **two** places:

```groovy
implementation 'org.projectlombok:lombok:1.18.22'
annotationProcessor 'org.projectlombok:lombok:1.18.22'   // runs at compile time
```

| Annotation | Generates |
|------------|-----------|
| `@Getter` / `@Setter` | getters / setters |
| `@Data` | getters, setters, `toString`, `equals`, `hashCode`, required-args constructor |
| `@Builder` | fluent builder pattern |
| `@NoArgsConstructor` | empty constructor (Jackson needs this!) |
| `@AllArgsConstructor` | constructor with all fields |

> **Gotcha:** in IntelliJ you must install the **Lombok plugin** and enable **Annotation Processing**, or the IDE shows "cannot find method `getTrainId()`" even though it compiles. In *our* build we chose to write the methods by hand and skip Lombok — both approaches produce the same class.

---

## 8. `static` — calling methods from anywhere without an object

Look at `UserServiceUtil`:

```java
public class UserServiceUtil {

    public static String hashPassword(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt());
    }

    public static boolean checkPassword(String plainPassword, String hashedPassword) {
        return BCrypt.checkpw(plainPassword, hashedPassword);
    }
}
```

Both methods are **`static`**. That's why we can call them like this — **no `new`**:

```java
UserServiceUtil.hashPassword("secret123");
UserServiceUtil.checkPassword("secret123", storedHash);
```

Compare with a **non-static** (instance) method — `UserBookingService.loginUser()` — which needs an object first:

```java
UserBookingService service = new UserBookingService();  // must instantiate
service.loginUser();
```

### Why `static` works "from anywhere"

A `static` member belongs to the **class itself**, not to any object.

```
NON-STATIC                          STATIC
─────────                           ──────
belongs to each object              belongs to the class
needs `new` first                   call on the class name
each object has its own copy        one shared copy for the whole app
UserBookingService s = new ...();   UserServiceUtil.hashPassword(...)
s.loginUser();
```

When the class is loaded, its `static` methods live in one fixed place in memory. Any code that can see the class can call them directly — that's the "call from anywhere without declaring an object" behavior.

### When should a method be `static`?

Make it `static` when it is a **pure utility / helper** that:

- depends **only on its arguments**, and
- does **not** read or change any per-object (instance) state.

`hashPassword` only needs the string you pass in — it doesn't care about "which `UserServiceUtil`". So it's static. This is exactly why utility classes (`Math`, `Collections`, `UserServiceUtil`) are full of static methods.

> **Interview point:** a `static` method **cannot** use `this` and cannot directly access instance fields — because there is no object. It can only touch its parameters and other static members.

---

## 9. How all of these connect in one request

Tracing **"user logs in"** touches every concept in this note:

```
1. App reads users.json
        └─ ObjectMapper.readValue(..., new TypeReference<List<User>>(){})
                 ├─ SERIALIZATION (deserialize direction)
                 └─ TypeReference defeats TYPE ERASURE → real List<User>

2. loginUser() searches the list
        └─ userList.stream().filter(u -> u.getName()... ).findFirst()
                 └─ PREDICATE decides which user matches

3. (BCrypt variant) verify the password
        └─ UserServiceUtil.checkPassword(raw, stored)
                 └─ STATIC method → called on the class, no object

4. Entities that got deserialized (User/Ticket/Train)
        └─ their getters/setters/constructors
                 └─ LOMBOK would auto-generate these (@Data/@Builder)
```

Five separate topics, one login.

---

## Frequently Asked Interview Questions

**Q1. What is a `Predicate`?**
A functional interface with a single method `boolean test(T)`. It represents a condition and is commonly supplied as a lambda to `filter`, `anyMatch`, `removeIf`, etc.

**Q2. Difference between `filter` and `removeIf`?**
`filter` is a lazy stream operation that produces a new stream and runs only on a terminal operation. `removeIf` is an eager `Collection` method that mutates the collection in place immediately. Both accept a `Predicate`.

**Q3. What is serialization vs deserialization?**
Serialization converts a Java object into a storable/transmittable form (here, JSON text). Deserialization reconstructs the object from that form.

**Q4. What does `ObjectMapper` do?**
It's Jackson's engine that performs serialization (`writeValue`) and deserialization (`readValue`) between Java objects and JSON.

**Q5. Why do we need `TypeReference` instead of `List.class`?**
Because of type erasure, `List<User>` becomes just `List` at runtime, so `List.class` loses the element type and Jackson produces `List<LinkedHashMap>`. `new TypeReference<List<User>>(){}` creates an anonymous subclass whose generic signature survives in class metadata, letting Jackson recover the full `List<User>` type.

**Q6. Why does a class need a no-arg constructor to be deserialized?**
Jackson first creates an empty instance, then fills fields via setters/reflection. Without a no-arg constructor it has nothing to instantiate (unless you configure a `@JsonCreator`).

**Q7. What is Lombok and how does it work?**
A library that generates boilerplate (getters, setters, constructors, `toString`, builders) at compile time via annotation processing, injecting the methods into the bytecode. It needs both an `implementation` and an `annotationProcessor` dependency.

**Q8. Why can we call `UserServiceUtil.hashPassword()` without `new`?**
Because it's `static` — it belongs to the class, not to an instance. Static members are loaded once with the class and are callable via the class name from anywhere with access.

**Q9. When should a method be static?**
When it's a pure helper that depends only on its arguments and touches no instance state — e.g., password hashing, `Math.max`, `Collections.sort`.

**Q10. Can a static method access instance fields or `this`?**
No. There is no object associated with a static call, so `this` doesn't exist and instance fields aren't accessible; it can only use its parameters and other static members.

---

## Summary

- A **`Predicate<T>`** is a yes/no test (`boolean test(T)`), usually written as a lambda; the project uses it in `filter`, `findFirst`, and `removeIf`.
- **Serialization** = object → JSON; **deserialization** = JSON → object. The project uses JSON files as a fake database.
- **`ObjectMapper`** (Jackson) does the conversion via `writeValue` (save) and `readValue` (load); modules like `JavaTimeModule` teach it extra types (`LocalDate`).
- **`TypeReference`** exists to defeat **type erasure** — its anonymous-subclass trick preserves the full generic type (`List<User>`) that `List.class` would lose.
- Field types must be **mappable** by Jackson (our `java.sql.Time` → `String` fix), and entities need a **no-arg constructor + accessors**.
- **Lombok** generates boilerplate (getters/setters/constructors/builders) at compile time via annotation processing.
- **`static`** members belong to the **class**, so utility methods like `hashPassword` are callable from anywhere without creating an object — ideal for stateless helpers.
