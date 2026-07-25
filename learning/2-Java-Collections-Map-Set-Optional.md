# Java Collections: Map, Set & Optional

A detailed note covering the `Map` interface, the `Set` interface, and the `Optional` class in Java.

---

## Table of Contents

1. [Map in Java](#map-in-java)
2. [Set in Java](#set-in-java)
3. [Optional in Java](#optional-in-java)
4. [ArrayList vs LinkedList — get()](#arraylist-vs-linkedlist--get)

---

# Map in Java

`Map` is another important interface in Java. Unlike `List`, which stores elements by index, a `Map` stores **key-value pairs**.

## What is a Map?

Think of a dictionary.

```
Word        Meaning
-----       -------
Apple   →   Fruit
Car     →   Vehicle
Dog     →   Animal
```

Here:

- **Key** = Apple
- **Value** = Fruit

In Java:

```java
Map<String, String> map = new HashMap<>();

map.put("Apple", "Fruit");
map.put("Car", "Vehicle");

System.out.println(map.get("Apple")); // Fruit
```

## Map Hierarchy

```
                Map (Interface)
                     │
      ┌──────────────┼──────────────┐
      │              │              │
   HashMap      LinkedHashMap    TreeMap
```

Just like:

```
List (Interface)
      │
 ┌────┴────┐
 │         │
ArrayList LinkedList
```

## Common Methods

```java
Map<String, Integer> marks = new HashMap<>();

marks.put("John", 90);       // Add
marks.put("Alice", 95);

System.out.println(marks.get("John"));      // 90

System.out.println(marks.containsKey("John"));   // true

System.out.println(marks.containsValue(95));     // true

marks.remove("John");

System.out.println(marks.size());

System.out.println(marks.isEmpty());
```

## Internal Visualization

```
HashMap
Key            Value
-----------------------
"John"   --->   90
"Alice"  --->   95
"Bob"    --->   80
```

You access values using the key:

```java
marks.get("Alice");   // 95
```

Not by index.

## HashMap vs LinkedHashMap vs TreeMap

| Feature            | HashMap         | LinkedHashMap        | TreeMap          |
|--------------------|-----------------|----------------------|------------------|
| Order maintained?  | ❌ No           | ✅ Insertion order   | ✅ Sorted by key |
| Lookup             | O(1) average    | O(1) average         | O(log n)         |
| Null key           | 1 allowed       | 1 allowed            | ❌ Not allowed   |
| Null values        | Allowed         | Allowed              | Allowed          |

## Why use a Map?

Suppose you have student marks.

### Using a List ❌

```java
List<Integer> marks = new ArrayList<>();

marks.add(90);
marks.add(95);
```

How do you know whose mark is 95?

You don't.

### Using a Map ✅

```java
Map<String, Integer> marks = new HashMap<>();

marks.put("John", 90);
marks.put("Alice", 95);

System.out.println(marks.get("Alice")); // 95
```

Now every value is associated with a meaningful key.

## Interview Questions

### Why can't a Map have duplicate keys?

```java
Map<String, Integer> map = new HashMap<>();

map.put("John", 90);
map.put("John", 80);

System.out.println(map);
```

Output:

```
{John=80}
```

The second `put()` replaces the previous value because a key must uniquely identify a value.

### Can values be duplicated?

Yes.

```java
map.put("John", 90);
map.put("Alice", 90);
```

Output:

```
{John=90, Alice=90}
```

Duplicate values are allowed.

## Interview One-liner

- **List** → Stores elements in order, accessed by index.
- **Set** → Stores unique elements.
- **Map** → Stores key-value pairs, accessed by key. HashMap, LinkedHashMap, and TreeMap are the most commonly used implementations.

---

# Set in Java

`Set` is another important interface in the Java Collections Framework.

A `Set` stores **unique elements**. It does not allow duplicates.

## Collection Hierarchy

```
             Collection (Interface)
                    │
      ┌─────────────┴─────────────┐
      │                           │
     List                        Set
      │                           │
 ┌────┴────┐          ┌───────────┼───────────┐
 │         │          │           │           │
ArrayList LinkedList HashSet LinkedHashSet TreeSet
```

Notice that `Map` is **NOT** part of `Collection`. It is a separate interface.

## Example

```java
Set<String> set = new HashSet<>();

set.add("Java");
set.add("Python");
set.add("Java");

System.out.println(set);
```

Output:

```
[Java, Python]
```

The second `"Java"` is ignored because it already exists.

## Visualization

Add `"Java"`

```
+------+
| Java |
+------+
```

Add `"Python"`

```
+--------+
| Java   |
| Python |
+--------+
```

Add `"Java"` again

```
+--------+
| Java   |
| Python |
+--------+
```

❌ Duplicate ignored

## Common Methods

```java
Set<Integer> set = new HashSet<>();

set.add(10);
set.add(20);

set.contains(10);   // true

set.remove(20);

set.size();

set.isEmpty();
```

## HashSet vs LinkedHashSet vs TreeSet

| Feature           | HashSet         | LinkedHashSet     | TreeSet          |
|-------------------|-----------------|-------------------|------------------|
| Duplicate allowed | ❌ No           | ❌ No             | ❌ No            |
| Order             | ❌ No guarantee | ✅ Insertion order| ✅ Sorted order  |
| Lookup            | O(1) average    | O(1) average      | O(log n)         |
| Null              | One null allowed| One null allowed  | ❌ null not allowed |

### HashSet

```java
Set<Integer> set = new HashSet<>();

set.add(30);
set.add(10);
set.add(20);

System.out.println(set);
```

Possible output:

```
[20, 10, 30]
```

Order is not guaranteed.

### LinkedHashSet

```java
Set<Integer> set = new LinkedHashSet<>();

set.add(30);
set.add(10);
set.add(20);

System.out.println(set);
```

Output:

```
[30, 10, 20]
```

Maintains insertion order.

### TreeSet

```java
Set<Integer> set = new TreeSet<>();

set.add(30);
set.add(10);
set.add(20);

System.out.println(set);
```

Output:

```
[10, 20, 30]
```

Automatically keeps elements sorted.

## When should you use a Set?

Suppose you have user IDs:

```
101
102
101
103
102
```

You want only unique IDs.

Using a Set:

```java
Set<Integer> users = new HashSet<>();

users.add(101);
users.add(102);
users.add(101);
users.add(103);
```

Result:

```
[101, 102, 103]
```

## List vs Set vs Map

| Feature             | List       | Set                       | Map                       |
|---------------------|------------|---------------------------|---------------------------|
| Stores              | Elements   | Unique elements           | Key-value pairs           |
| Duplicate elements  | ✅ Yes     | ❌ No                     | Keys ❌, Values ✅        |
| Ordered             | ✅ Yes     | Depends on implementation | Depends on implementation |
| Access              | By index   | By element                | By key                    |

## Interview Questions

### Why does HashSet not allow duplicates?

Internally, `HashSet` uses a `HashMap`.

When you call:

```java
set.add("Java");
```

Internally it's similar to:

```java
hashMap.put("Java", PRESENT);
```

Here, `"Java"` is the key, and `PRESENT` is just a dummy constant value.

Since a `HashMap` cannot have duplicate keys, a `HashSet` cannot have duplicate elements.

## Time Complexities (average case)

| Operation    | HashSet | LinkedHashSet | TreeSet  |
|--------------|---------|---------------|----------|
| add()        | O(1)    | O(1)          | O(log n) |
| contains()   | O(1)    | O(1)          | O(log n) |
| remove()     | O(1)    | O(1)          | O(log n) |

## Interview One-liner

- **List** → Ordered collection; duplicates allowed.
- **Set** → Unique elements; duplicates not allowed.
- **Map** → Key-value pairs; duplicate keys not allowed, duplicate values allowed.

---

# Optional in Java

`Optional` is a class introduced in **Java 8** to avoid `NullPointerException` (NPE) and make it explicit that a value may or may not be present.

Instead of returning `null`, a method can return an `Optional`.

## Without Optional

```java
public String getName() {
    return null;
}
```

Usage:

```java
String name = getName();

System.out.println(name.length()); // ❌ NullPointerException
```

If `getName()` returns `null`, your program crashes.

## With Optional

```java
public Optional<String> getName() {
    return Optional.of("Tanmay");
}
```

Usage:

```java
Optional<String> name = getName();

if (name.isPresent()) {
    System.out.println(name.get());
}
```

Now the caller knows that the value might be absent.

## Visualization

### Traditional approach

```
getName()
     │
     ▼
   "John"

OR

     ▼
    null  ❌
```

### Optional approach

```
getName()
     │
     ▼
+--------------------+
| Optional           |
|--------------------|
| Value = "John"     |
+--------------------+

OR

+--------------------+
| Optional.empty()   |
+--------------------+
```

Instead of returning `null`, Java returns an empty `Optional`.

## Creating Optional

### 1. Optional.of()

Use when you're sure the value is not null.

```java
Optional<String> name = Optional.of("Java");
```

If you do:

```java
Optional.of(null);
```

❌ Throws `NullPointerException`.

### 2. Optional.ofNullable()

Allows null.

```java
Optional<String> name = Optional.ofNullable(null);
```

Result:

```
Optional.empty
```

### 3. Optional.empty()

Creates an empty Optional.

```java
Optional<String> name = Optional.empty();
```

## Common Methods

### isPresent()

```java
Optional<String> name = Optional.of("Java");

if (name.isPresent()) {
    System.out.println(name.get());
}
```

Output:

```
Java
```

### get()

Returns the value.

```java
Optional<String> name = Optional.of("Java");

System.out.println(name.get());
```

Output:

```
Java
```

⚠️ If the Optional is empty:

```java
Optional.empty().get();
```

Throws:

```
NoSuchElementException
```

### orElse()

Provides a default value.

```java
Optional<String> name = Optional.empty();

System.out.println(name.orElse("Unknown"));
```

Output:

```
Unknown
```

### orElseGet()

Computes the default value only if needed.

```java
String value = name.orElseGet(() -> "Generated Name");
```

Useful when creating the default value is expensive.

### orElseThrow()

Throw your own exception if no value exists.

```java
String name = optional.orElseThrow(
    () -> new RuntimeException("Name not found")
);
```

### ifPresent()

Runs code only if a value exists.

```java
Optional<String> name = Optional.of("Java");

name.ifPresent(System.out::println);
```

Output:

```
Java
```

### map()

Transforms the value if present.

```java
Optional<String> name = Optional.of("java");

Optional<String> upper = name.map(String::toUpperCase);

System.out.println(upper.get());
```

Output:

```
JAVA
```

## Real-world Example

Without Optional:

```java
User user = repository.findById(1);

if (user != null) {
    System.out.println(user.getName());
}
```

With Optional:

```java
Optional<User> user = repository.findById(1);

user.ifPresent(u -> System.out.println(u.getName()));
```

Or:

```java
String name = repository.findById(1)
                        .map(User::getName)
                        .orElse("Guest");
```

## When is Optional used?

Very commonly in frameworks like Spring Boot.

Example:

```java
Optional<User> findById(Long id);
```

Instead of:

```java
User findById(Long id);
```

The caller is forced to think about the possibility that the user doesn't exist.

## Interview Questions

### Q1. Why was Optional introduced?

To reduce `NullPointerException` and make the absence of a value explicit.

### Q2. Difference between orElse() and orElseGet()?

```java
optional.orElse(createObject());
```

`createObject()` is **always executed**, even if the Optional contains a value.

```java
optional.orElseGet(() -> createObject());
```

`createObject()` is executed **only if the Optional is empty**.

### Q3. Should you use Optional as a field?

Generally no.

```java
class User {
    Optional<String> name; // ❌ Avoid
}
```

Optional is intended mainly for **method return types**, not for fields or method parameters.

## Interview One-liner

**Optional is a container object that may or may not contain a value. It helps avoid NullPointerException by making the absence of a value explicit and providing safe methods like `orElse()`, `ifPresent()`, and `map()`.**

---

# ArrayList vs LinkedList — get()

Yes! Since `LinkedList` implements the `List` interface, it also has the `.get()` method.

```java
LinkedList<String> list = new LinkedList<>();

list.add("A");
list.add("B");
list.add("C");

System.out.println(list.get(1)); // B
```

Or, using the interface reference (recommended):

```java
List<String> list = new LinkedList<>();

list.add("A");
list.add("B");
list.add("C");

System.out.println(list.get(1)); // B
```

## But here's the important difference

Although both `ArrayList` and `LinkedList` have a `.get()` method, their implementations are different.

### ArrayList

```java
list.get(2);
```

Internally:

```
Address = Base Address + (2 × element size)
```

➡️ Directly jumps to the element.

**Time Complexity: O(1)**

### LinkedList

```java
list.get(2);
```

Internally:

```
Head
 ↓
[A] → [B] → [C] → [D]
 1     2     3
```

Java traverses the nodes:

```
Go to A
Go to B
Go to C
```

Then it returns C.

**Time Complexity: O(n)**

## Does Java always start from the head?

For a `LinkedList`, Java actually uses a small optimization because it's a **doubly linked list**.

```
Head ←→ A ←→ B ←→ C ←→ D ←→ Tail
```

If you call:

```java
list.get(1);
```

Java starts from the **head**.

If you call:

```java
list.get(list.size() - 2);
```

Java starts from the **tail**.

It traverses from whichever end is closer, reducing the number of steps. Even with this optimization, the worst-case time complexity remains **O(n)**.

## Interview Question

**Q: If `LinkedList.get()` is O(n), why does LinkedList even implement List?**

**A:** Because the `List` interface defines the **behavior** (ordered collection with indexed access), not the **performance**. Every implementation must provide `get()`, but each is free to implement it differently. `ArrayList` provides O(1) access, while `LinkedList` provides O(n) access.
