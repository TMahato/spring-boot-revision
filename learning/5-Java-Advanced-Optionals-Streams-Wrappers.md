# Java Advanced: Optionals, Streams, Wrapper Classes & More

A deeper look at some of the most powerful (and most misused) parts of modern Java: `Optional`, the Streams API, wrapper classes (`Integer` vs `int`), and a few other important concepts every Java developer should know.

> Note 2 already introduced `Optional` at a basic level. This note goes further and pairs it with Streams, which is where `Optional` really shines.

---

## Table of Contents

1. [Wrapper Classes (`Integer` vs `int`)](#1-wrapper-classes-integer-vs-int)
2. [Autoboxing & Unboxing](#2-autoboxing--unboxing)
3. [`String` vs `string` (and why String is special)](#3-string-vs-string)
4. [Optional — Done Right](#4-optional--done-right)
5. [Streams API](#5-streams-api)
6. [Streams + Optional Together](#6-streams--optional-together)
7. [`map` vs `flatMap`](#7-map-vs-flatmap)
8. [Method References](#8-method-references)
9. [`var` — Local Variable Type Inference](#9-var--local-variable-type-inference)
10. [Records](#10-records)
11. [Quick Reference Cheat Sheet](#11-quick-reference-cheat-sheet)

---

# 1. Wrapper Classes (`Integer` vs `int`)

Java has **two kinds of types**:

| Kind | Examples | Stored as |
|------|----------|-----------|
| **Primitive** | `int`, `double`, `char`, `boolean`, `long`, `float`, `byte`, `short` | The raw value itself |
| **Wrapper (Object)** | `Integer`, `Double`, `Character`, `Boolean`, `Long`, `Float`, `Byte`, `Short` | An object on the heap |

Every primitive has a matching **wrapper class**:

```
int     -> Integer
double  -> Double
char    -> Character
boolean -> Boolean
long    -> Long
```

### Analogy: value vs gift box

- A **primitive** (`int`) is like a coin sitting in your hand — just the value, nothing else.
- A **wrapper** (`Integer`) is like that same coin **inside a gift box**. The box is an object; it can be labelled, passed around as an object, and — importantly — it can also be **empty** (`null`).

```java
int a = 5;              // just the value
Integer b = 5;          // an object wrapping the value 5
Integer c = null;       // allowed! an "empty box"
// int d = null;        // ❌ compile error — a primitive can never be null
```

## Why do wrapper classes exist? (Their benefits)

### 1. Collections & Generics only work with objects

You **cannot** write `List<int>`. Generics only work with reference types (objects), never primitives.

```java
List<int> nums;         // ❌ not allowed
List<Integer> nums;     // ✅ correct
```

**Why exactly?** It comes down to how Java generics are implemented — a mechanism called **type erasure**.

1. **Generics are a compile-time feature only.** When the compiler finishes, it *erases* the type parameter and replaces it with `Object` (or the upper bound). So at runtime, `List<Integer>` and `List<String>` are both just `List` storing `Object` references.

   ```java
   // What you write:
   List<Integer> nums = new ArrayList<>();

   // What the compiler effectively produces after erasure:
   List nums = new ArrayList();     // holds Object references internally
   ```

2. **A generic container stores `Object` references.** Because the backing storage is an `Object[]`, every element must *be* an object — something that can be pointed to by a reference on the heap.

3. **A primitive is not an object and has no reference.** An `int` is just 4 bytes of raw value sitting directly in memory — it isn't allocated on the heap and there is no reference pointing to it. So it simply cannot be stored where an `Object` reference is expected.

   ```
   int      ->  42                (raw value, no reference)
   Integer  ->  ref ──► [ 42 ]    (a reference pointing to an object on the heap)
   ```

Since `T` in `List<T>` must be substitutable for `Object`, and a primitive can never be an `Object`, the compiler rejects `List<int>` outright. You must use the wrapper class `Integer`, which **is** a real object and therefore has a reference that can live inside the list.

> In short: **generics need something you can hold a reference to (an object). `int` has no reference; `Integer` does.** (Thanks to autoboxing, you can still write `list.add(5)` — the compiler quietly wraps it as `Integer.valueOf(5)` for you.)

### 2. They can be `null`

A wrapper can represent "no value / unknown", which a primitive cannot.

```java
Integer age = null;     // "age is unknown"
int age = 0;            // 0 is a real value — you can't tell "unknown" from "zero"
```

This matters a lot with databases: a database column can be `NULL`, but `int` cannot represent that.

### 3. Useful utility methods & constants

Wrapper classes are full objects, so they carry helpful methods:

```java
int x = Integer.parseInt("42");        // String -> int
String s = Integer.toString(42);       // int -> String
int max = Integer.MAX_VALUE;           // 2147483647
int min = Integer.MIN_VALUE;
int bits = Integer.bitCount(7);        // number of 1-bits
int compare = Integer.compare(3, 5);   // -1
```

### 4. Required by the Streams / Optional / Collections APIs

`Optional<Integer>`, `Map<String, Integer>`, `Stream<Integer>` — all need the object form.

## When to use which?

| Situation | Use |
|-----------|-----|
| Performance-critical loops, counters, math | **Primitive** (`int`) — faster, no object overhead |
| Inside collections / generics / maps | **Wrapper** (`Integer`) |
| A value that may legitimately be missing | **Wrapper** (so it can be `null`) or better, `Optional` |

> **Rule of thumb:** default to `int` for local math, use `Integer` when the API forces an object or when `null` genuinely means "no value".

---

# 2. Autoboxing & Unboxing

Java automatically converts between primitives and wrappers. This is **autoboxing** (primitive → wrapper) and **unboxing** (wrapper → primitive).

```java
Integer boxed = 10;     // autoboxing:   int  -> Integer  (Integer.valueOf(10))
int raw = boxed;        // unboxing:     Integer -> int   (boxed.intValue())
```

### ⚠️ Trap 1: NullPointerException on unboxing

```java
Integer count = null;
int total = count;      // 💥 NullPointerException — tries to call null.intValue()
```

The compiler happily accepts this, but it **crashes at runtime**. This is one of the most common Java bugs.

### ⚠️ Trap 2: `==` compares references, not values

```java
Integer a = 1000;
Integer b = 1000;
System.out.println(a == b);        // false! (two different objects)
System.out.println(a.equals(b));   // true  (compares values)
```

> Always use `.equals()` to compare wrapper objects — never `==`.

**Step 1: What does `==` compare?** For objects, `==` compares **references** (memory addresses), not the values inside. `a` and `b` are two separate `Integer` objects on the heap, so their references differ → `false`.

**Step 2: What does `.equals()` compare?** Every object inherits `equals()` from `Object`. Wrapper classes like `Integer` **override** it so it checks the **stored value**. Conceptually, it's like this:

```java
public boolean equals(Object obj) {
    return this.value == ((Integer) obj).value;
}
```

So `a.equals(b)` unwraps both objects and compares the raw `int` values → `true`.

**Twist:** Java caches small `Integer` values (`-128` to `127`), so `Integer a = 100; Integer b = 100; a == b` is `true`. This inconsistency is exactly why you should always use `.equals()`.

### ⚠️ Trap 3: performance in loops

```java
Long sum = 0L;                     // wrapper — BAD
for (long i = 0; i < 1_000_000; i++) {
    sum += i;                      // boxes & unboxes a MILLION times
}
```

Use the primitive `long sum = 0L;` instead. Autoboxing in tight loops silently creates millions of objects.

---

# 3. `String` vs `string`

In Java there is **no** `string` (lowercase) type. That's a C# thing. Java only has `String` (capital S), which is a **class**, not a primitive.

```java
String name = "Jassi";        // ✅ correct
string name = "Jassi";        // ❌ compile error — no such type
```

So `String` behaves like an object (has methods, can be `null`), similar to a wrapper class.

## Key facts about String

### 1. Strings are immutable

Once created, a `String` can never be changed. Every "modification" creates a **new** object.

```java
String s = "hello";
s.toUpperCase();              // returns "HELLO" but does NOT change s
System.out.println(s);       // still "hello"

s = s.toUpperCase();         // reassigning is how you "change" it
System.out.println(s);       // "HELLO"
```

### 2. `==` vs `.equals()` — same trap as wrappers

```java
String a = new String("hi");
String b = new String("hi");
System.out.println(a == b);        // false — different objects
System.out.println(a.equals(b));   // true  — same characters
```

> Compare string **content** with `.equals()`, never `==`.

### 3. The String Pool

String literals are cached in a special "pool" so identical literals share one object:

```java
String x = "hi";
String y = "hi";
System.out.println(x == y);        // true — both point to the same pooled object
```

### 4. Use `StringBuilder` for heavy concatenation

Because `String` is immutable, building a string in a loop creates tons of garbage:

```java
// BAD — creates a new String every iteration
String result = "";
for (int i = 0; i < 1000; i++) result += i;

// GOOD — one mutable buffer
StringBuilder sb = new StringBuilder();
for (int i = 0; i < 1000; i++) sb.append(i);
String result = sb.toString();
```

---

# 4. Optional — Done Right

`Optional<T>` is a **box that either contains a value or is empty**. Its whole purpose is to make "a value might be missing" **explicit in the type system**, so you stop getting surprise `NullPointerException`s.

### Analogy: a parcel that may be empty

You receive a sealed parcel. Instead of ripping it open (and maybe finding nothing → crash), `Optional` forces you to ask *"is there anything inside?"* first.

## Creating an Optional

```java
Optional<String> a = Optional.of("hello");      // must be non-null
Optional<String> b = Optional.ofNullable(name); // null becomes empty
Optional<String> c = Optional.empty();          // explicitly empty
```

## The WRONG way to use it

```java
Optional<User> user = findUser(id);
if (user.isPresent()) {
    return user.get();       // ❌ this is just null-checking with extra steps
}
```

`.get()` and `isPresent()`/`get()` chains defeat the purpose. If you call `.get()` on an empty Optional it throws `NoSuchElementException` — you've just swapped one exception for another.

## The RIGHT way — functional style

```java
// Provide a default
String name = findUser(id)
        .map(User::getName)
        .orElse("Guest");

// Provide a default lazily (only computed if needed)
String name = findUser(id)
        .map(User::getName)
        .orElseGet(() -> loadDefaultName());

// Throw a meaningful exception if missing
User user = findUser(id)
        .orElseThrow(() -> new UserNotFoundException(id));

// Do something only if present
findUser(id).ifPresent(u -> System.out.println(u.getName()));

// Present-or-else (Java 9+)
findUser(id).ifPresentOrElse(
        u -> System.out.println(u.getName()),
        () -> System.out.println("No user"));
```

## Key methods

| Method | What it does |
|--------|--------------|
| `map(fn)` | Transform the value if present |
| `flatMap(fn)` | Transform when `fn` itself returns an `Optional` (avoids `Optional<Optional<T>>`) |
| `filter(pred)` | Keep the value only if it matches |
| `orElse(default)` | Return value or a default |
| `orElseGet(supplier)` | Same, but default is computed lazily |
| `orElseThrow(...)` | Return value or throw |
| `ifPresent(consumer)` | Run code only if present |
| `isPresent()` / `isEmpty()` | Boolean checks (use sparingly) |

## Best practices

- ✅ Use `Optional` as a **return type** for methods that may find nothing.
- ❌ Don't use `Optional` for **fields** or **method parameters** (adds overhead and noise).
- ❌ Never do `Optional.of(x)` when `x` might be `null` — use `ofNullable`.
- ❌ Avoid `.get()` — prefer `orElse` / `orElseThrow`.

---

# 5. Streams API

A **Stream** is a pipeline for processing a sequence of elements in a **declarative** way — you describe *what* you want, not *how* to loop.

### Analogy: a factory conveyor belt

Data items travel along a belt. Each station performs one operation — filter out bad items, stamp/transform each item, count them — and at the end you collect the result. You never write the loop yourself; you just arrange the stations.

## Old way vs Stream way

```java
// Imperative (old)
List<String> result = new ArrayList<>();
for (String name : names) {
    if (name.length() > 3) {
        result.add(name.toUpperCase());
    }
}

// Declarative (stream)
List<String> result = names.stream()
        .filter(name -> name.length() > 3)
        .map(String::toUpperCase)
        .collect(Collectors.toList());
```

## Anatomy of a stream

Every stream has **three parts**:

```
source  ->  intermediate operations  ->  terminal operation
names.stream()  .filter(...).map(...)     .collect(...)
```

- **Source:** where data comes from (`list.stream()`, `Stream.of(...)`, `IntStream.range(...)`).
- **Intermediate operations:** lazy, return a new stream, chainable (`filter`, `map`, `sorted`, `distinct`, `limit`).
- **Terminal operation:** triggers execution and produces a result (`collect`, `forEach`, `count`, `reduce`, `findFirst`). **Nothing runs until the terminal operation is called** — this is called *lazy evaluation*.

## Common intermediate operations

```java
.filter(x -> x > 10)        // keep elements matching a condition
.map(x -> x * 2)            // transform each element
.sorted()                   // natural order
.sorted(Comparator.reverseOrder())
.distinct()                 // remove duplicates
.limit(5)                   // keep first 5
.skip(2)                    // drop first 2
.peek(System.out::println)  // debug — look without consuming
```

## Common terminal operations

```java
.collect(Collectors.toList())          // gather into a List
.forEach(System.out::println)          // side effect per element
.count()                               // number of elements
.anyMatch(x -> x > 100)                // boolean
.allMatch(x -> x > 0)
.noneMatch(x -> x < 0)
.findFirst()                           // Optional<T>
.reduce(0, Integer::sum)               // combine into one value
.min(Comparator.naturalOrder())        // Optional<T>
.max(Comparator.naturalOrder())
```

## Collectors — the most useful terminal helper

```java
import static java.util.stream.Collectors.*;

// To a List / Set
List<String> list = stream.collect(toList());
Set<String> set   = stream.collect(toSet());

// Join into a String
String csv = names.stream().collect(joining(", "));      // "a, b, c"

// Group by a key  ->  Map<K, List<V>>
Map<Integer, List<String>> byLength =
        names.stream().collect(groupingBy(String::length));

// Count per group  ->  Map<K, Long>
Map<Integer, Long> countByLength =
        names.stream().collect(groupingBy(String::length, counting()));

// To a Map
Map<Integer, String> byId =
        users.stream().collect(toMap(User::getId, User::getName));

// Sum / average
int total = items.stream().collect(summingInt(Item::getPrice));
double avg = items.stream().collect(averagingInt(Item::getPrice));
```

## Numeric streams (avoid boxing!)

For heavy number crunching, use `IntStream` / `LongStream` / `DoubleStream` — they work on primitives directly, no autoboxing overhead:

```java
int sum = IntStream.rangeClosed(1, 100).sum();          // 5050
double avg = IntStream.of(3, 5, 7).average().getAsDouble();
int[] squares = IntStream.range(0, 5).map(n -> n * n).toArray();
```

## ⚠️ Streams gotchas

- A stream can be **consumed only once**. Calling a second terminal operation throws `IllegalStateException`.
- Streams are **not** for simple counting loops — a plain `for` loop is fine (and often clearer).
- Don't mutate external state inside `map`/`filter` — keep operations pure.
- Streams are lazy: if you forget the terminal operation, **nothing happens**.

---

# 6. Streams + Optional Together

Many stream terminal operations return an `Optional` because the result might not exist:

```java
Optional<String> first = names.stream()
        .filter(n -> n.startsWith("J"))
        .findFirst();

String result = names.stream()
        .filter(n -> n.startsWith("J"))
        .findFirst()
        .orElse("none found");

// max / min return Optional too
Optional<Integer> highest = scores.stream().max(Integer::compare);
```

This is the natural pairing: **streams find things, Optional safely represents "maybe nothing was found."**

---

# 7. `map` vs `flatMap`

Both appear in Streams **and** Optional. The difference is one level of nesting.

- **`map`** transforms each element into exactly **one** element.
- **`flatMap`** transforms each element into a **stream/Optional of elements**, then **flattens** them into one.

```java
// map: List<String> -> List<Integer> (lengths)
List<Integer> lengths = names.stream()
        .map(String::length)
        .collect(toList());

// flatMap: List<List<String>> -> flat List<String>
List<List<String>> nested = List.of(
        List.of("a", "b"),
        List.of("c", "d"));

List<String> flat = nested.stream()
        .flatMap(List::stream)        // flatten the inner lists
        .collect(toList());           // [a, b, c, d]
```

### Analogy

- `map` = repaint each box.
- `flatMap` = open each box that contains more boxes, and lay everything out on one table.

Use `flatMap` whenever `map` would give you a `Stream<Stream<T>>` or `Optional<Optional<T>>`.

---

# 8. Method References

A shorthand for a lambda that just calls one existing method. `::` is the method-reference operator.

```java
// Lambda                           // Method reference
x -> System.out.println(x)          System.out::println
s -> s.toUpperCase()                String::toUpperCase
(a, b) -> Integer.compare(a, b)     Integer::compare
() -> new ArrayList<>()             ArrayList::new
```

Four kinds:

| Kind | Syntax | Example |
|------|--------|---------|
| Static method | `Class::staticMethod` | `Integer::parseInt` |
| Instance method of an object | `obj::method` | `System.out::println` |
| Instance method of a class | `Class::method` | `String::toUpperCase` |
| Constructor | `Class::new` | `ArrayList::new` |

They make stream pipelines much cleaner:

```java
names.stream().map(String::toUpperCase).forEach(System.out::println);
```

---

# 9. `var` — Local Variable Type Inference

Since Java 10, `var` lets the compiler infer a **local variable's** type. It is **not** dynamic typing — the type is fixed at compile time, just not written out.

```java
var name = "Jassi";                       // inferred String
var count = 10;                           // inferred int
var list = new ArrayList<String>();       // inferred ArrayList<String>

for (var entry : map.entrySet()) { ... }  // handy for verbose generic types
```

**Rules / limits:**

- Only for **local variables** (not fields, not method parameters, not return types).
- Must be initialised on the same line (`var x;` is illegal — nothing to infer from).
- Don't overuse it — `var result = process();` hides the type and can hurt readability.

---

# 10. Records

Since Java 16, a `record` is a compact way to declare an **immutable data-carrier class**. The compiler auto-generates the constructor, getters, `equals()`, `hashCode()`, and `toString()`.

```java
// Old way — ~40 lines of boilerplate
public record Point(int x, int y) {}
```

Usage:

```java
Point p = new Point(3, 4);
System.out.println(p.x());        // 3   (accessor is x(), not getX())
System.out.println(p);            // Point[x=3, y=4]

Point q = new Point(3, 4);
System.out.println(p.equals(q));  // true — value-based equality
```

Great for DTOs, API responses, map keys, and anything that's "just data". Records are **immutable** — all fields are `final`.

---

# 11. Quick Reference Cheat Sheet

| Concept | One-liner |
|---------|-----------|
| `int` vs `Integer` | `int` = raw value (fast, can't be null); `Integer` = object (nullable, works in collections) |
| Wrapper benefit | Needed for generics/collections, can be `null`, has utility methods |
| Autoboxing trap | Unboxing a `null` wrapper → `NullPointerException` |
| Compare wrappers/Strings | Use `.equals()`, never `==` |
| `String` | A class (immutable); no lowercase `string` in Java |
| Heavy string building | Use `StringBuilder` |
| `Optional` purpose | Make "maybe missing" explicit; avoid NPE |
| Optional best practice | Return type only; use `map`/`orElse`/`orElseThrow`, avoid `.get()` |
| Stream | Declarative data pipeline: source → intermediate → terminal |
| Stream is lazy | Nothing runs until the terminal operation |
| `map` vs `flatMap` | `map` = 1→1; `flatMap` = 1→many + flatten |
| Numeric streams | Use `IntStream`/`LongStream` to avoid boxing |
| Method reference | `Class::method` shorthand for a simple lambda |
| `var` | Local-only type inference; still statically typed |
| `record` | Auto-generated immutable data class |

---

> **Big picture:** wrapper classes bridge primitives into the object world; `Optional` and Streams are that object world put to work — expressing "maybe a value" and "a pipeline of values" cleanly, without manual null checks or hand-written loops.
