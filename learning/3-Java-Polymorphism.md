# Polymorphism in Java

A detailed note covering **Polymorphism** — its types, real-world analogies, examples, internal working, and interview questions.

---

## Table of Contents

1. [What is Polymorphism?](#what-is-polymorphism)
2. [Types of Polymorphism](#types-of-polymorphism)
3. [Compile-Time Polymorphism (Method Overloading)](#compile-time-polymorphism-method-overloading)
4. [Runtime Polymorphism (Method Overriding)](#runtime-polymorphism-method-overriding)
5. [Overloading vs Overriding](#overloading-vs-overriding)
6. [Upcasting & Dynamic Method Dispatch](#upcasting--dynamic-method-dispatch)
7. [Runtime Polymorphism Step by Step (How it works during execution)](#runtime-polymorphism-step-by-step-how-it-works-during-execution)
8. [Polymorphism with Abstract Classes & Interfaces](#polymorphism-with-abstract-classes--interfaces)
9. [Why use Polymorphism?](#why-use-polymorphism)
10. [Special Cases & Gotchas](#special-cases--gotchas)
11. [Interview Questions](#interview-questions)
12. [Interview One-liner](#interview-one-liner)

---

# What is Polymorphism?

**Polymorphism** = *Poly* (many) + *Morph* (forms).

It means **one name, many forms** — the same method or object can behave differently depending on the context.

## Real-world analogy

Think of a single **person**:

```
A person
   │
   ├── acts as an Employee   at office
   ├── acts as a Father      at home
   └── acts as a Customer    at a shop
```

Same person, but **different behavior** in different situations. That is polymorphism.

Another analogy — the `+` operator:

```
2 + 3        →  5          (addition)
"Hi" + "Bye" →  "HiBye"    (string concatenation)
```

Same `+` symbol, different behavior based on operands.

---

# Types of Polymorphism

```
                Polymorphism
                     │
        ┌────────────┴────────────┐
        │                         │
  Compile-Time              Runtime
  (Static)                  (Dynamic)
        │                         │
  Method Overloading       Method Overriding
        │
  (Operator Overloading —
   only built-in in Java, e.g. +)
```

| Type          | Also called | Achieved by         | Resolved at   |
|---------------|-------------|---------------------|---------------|
| Compile-time  | Static      | Method Overloading  | Compile time  |
| Runtime       | Dynamic     | Method Overriding   | Runtime       |

---

# Compile-Time Polymorphism (Method Overloading)

**Method Overloading** = multiple methods with the **same name** but **different parameters** in the **same class**.

The compiler decides which method to call based on the arguments — so it is resolved at **compile time**.

## Example

```java
class Calculator {

    int add(int a, int b) {
        return a + b;
    }

    int add(int a, int b, int c) {
        return a + b + c;
    }

    double add(double a, double b) {
        return a + b;
    }
}
```

Usage:

```java
Calculator calc = new Calculator();

System.out.println(calc.add(2, 3));        // 5   -> int, int
System.out.println(calc.add(2, 3, 4));     // 9   -> int, int, int
System.out.println(calc.add(2.5, 3.5));    // 6.0 -> double, double
```

Same method name `add`, but chosen based on the number/type of arguments.

## Ways to overload a method

A method can be overloaded by changing:

1. **Number of parameters**

```java
void show(int a) { }
void show(int a, int b) { }
```

2. **Type of parameters**

```java
void show(int a) { }
void show(String a) { }
```

3. **Order of parameters**

```java
void show(int a, String b) { }
void show(String a, int b) { }
```

## Can you overload by changing only the return type?

❌ **No.** Return type alone is **not** enough to overload.

```java
int show() { }
double show() { }   // ❌ Compile error — same signature
```

The compiler cannot decide which one to call, because the return type is not part of the method signature.

### Why does this fail? — The method signature

The reason lies in **how the compiler picks which method to run**.

**A method call site has no return type.** A call is allowed to *discard* the returned value:

```java
show();   // which one? int show() or double show()?
```

Here the return value is ignored (perfectly legal in Java), so the compiler has nothing to distinguish the two versions — same name, same (empty) parameters.

**What actually defines a method is its signature:**

```
        signature
   ┌────────────────┐
   show  (int, String)
   ↑      ↑
  name   parameters      ← return type is NOT part of this
```

> **Method signature = method name + parameter list** (types, number, and order). The **return type is not part of it.**

So to the compiler these two:

```java
int    show() { }
double show() { }
```

have the *exact same signature* → `show()`. It treats them as **duplicate methods**:

```
error: method show() is already defined
```

### Why overloading normally works

Overloading is resolved from the **arguments at the call site**, not the return type:

```java
int add(int a, int b)          { ... }   // signature: add(int, int)
double add(double a, double b) { ... }   // signature: add(double, double)

add(2, 3);       // args int, int      → picks first
add(2.5, 3.5);   // args double, double → picks second
```

Even when return types differ, it's the **different parameter list** that makes it valid — never the return type:

```java
int    process(int x)    { ... }   // ✅ OK — different params
double process(String s) { ... }   // ✅ OK — different params
```

**In short:** overloading is resolved from the call's *arguments*, and a call can legally ignore the return value — so the return type carries no information the compiler can use. Since the signature (name + parameters) is identical, the two methods collide as duplicates.

## Overloaded methods are resolved at compile time

Let's see why.

```java
class Animal {

    void fn(int a) {
        System.out.println(a);
    }

    void fn() {
        System.out.println("Hi");
    }
}
```

Here, there are two methods named `fn`, but their parameter lists are different:

```
fn()
fn(int)
```

These are called **overloaded methods**.

### Example

```java
Animal a = new Animal();

a.fn();
```

**What happens?**

During **compile time**, the compiler sees:

```java
a.fn();
```

It looks at the arguments.

```
Arguments = none
```

So it immediately knows:

```
Call fn()
```

The generated **bytecode already refers to `fn()`**.

At **runtime**, the JVM simply executes that method — the decision was already made by the compiler. This is why overloading is called **compile-time (static) polymorphism**, in contrast to overriding, where the JVM chooses the method at runtime.

---

# Runtime Polymorphism (Method Overriding)

**Method Overriding** = a **subclass** provides its **own implementation** of a method already defined in its **parent class**.

Same method name, same parameters, same return type — but different body. The actual method that runs is decided at **runtime** based on the object type.

## Example

```java
class Animal {
    void sound() {
        System.out.println("Animal makes a sound");
    }
}

class Dog extends Animal {
    @Override
    void sound() {
        System.out.println("Dog barks");
    }
}

class Cat extends Animal {
    @Override
    void sound() {
        System.out.println("Cat meows");
    }
}
```

Usage:

```java
Animal a;

a = new Dog();
a.sound();   // Dog barks

a = new Cat();
a.sound();   // Cat meows
```

Even though the reference type is `Animal`, the **object type** decides which `sound()` runs. This is called **Dynamic Method Dispatch**.

## Rules for overriding

- Method name, parameters, and return type must be the **same** (covariant return types are allowed).
- The method **cannot** have a more restrictive access modifier than the parent (e.g. `public` cannot become `private`).
- `final`, `static`, and `private` methods **cannot** be overridden.
- Use the `@Override` annotation — it lets the compiler catch mistakes.

---

# Overloading vs Overriding

| Feature            | Overloading (Compile-time)     | Overriding (Runtime)               |
|--------------------|--------------------------------|------------------------------------|
| Where              | Same class                     | Parent–child (inheritance)         |
| Parameters         | Must be different              | Must be same                       |
| Return type        | Can be different               | Same (or covariant)                |
| Resolved at        | Compile time                   | Runtime                            |
| Also known as      | Static / Early binding         | Dynamic / Late binding             |
| `@Override`        | Not used                       | Recommended                        |
| Purpose            | Increase readability/flexibility | Provide specific behavior         |

---

# Upcasting & Dynamic Method Dispatch

**Upcasting** = referring to a subclass object using a parent class reference.

```java
Animal a = new Dog();   // Upcasting
a.sound();              // Dog barks (runtime decision)
```

- The **reference type** (`Animal`) decides **which methods are accessible** at compile time.
- The **object type** (`Dog`) decides **which overridden method actually runs** at runtime.

## Dynamic Method Dispatch

The mechanism by which a call to an overridden method is resolved at runtime rather than compile time.

```java
Animal[] animals = { new Dog(), new Cat(), new Animal() };

for (Animal animal : animals) {
    animal.sound();
}
```

Output:

```
Dog barks
Cat meows
Animal makes a sound
```

One reference type, many behaviors — the essence of runtime polymorphism.

---

# Runtime Polymorphism Step by Step (How it works during execution)

Runtime polymorphism (also called **dynamic method dispatch**) is one of the most important interview topics in Java.

The idea is simple:

- The **compiler** looks at the **reference type**.
- The **JVM** looks at the **actual object type**.

Let's understand it step by step.

## Step 1: Create a parent class

```java
class Animal {
    void sound() {
        System.out.println("Animal makes sound");
    }
}
```

## Step 2: Child overrides the method

```java
class Dog extends Animal {
    @Override
    void sound() {
        System.out.println("Dog barks");
    }
}
```

## Step 3: Another child

```java
class Cat extends Animal {
    @Override
    void sound() {
        System.out.println("Cat meows");
    }
}
```

## Step 4: Runtime polymorphism

```java
public class Main {

    public static void main(String[] args) {

        Animal a1 = new Dog();
        Animal a2 = new Cat();

        a1.sound();
        a2.sound();
    }

}
```

Output:

```
Dog barks
Cat meows
```

## But wait...

Look carefully.

```java
Animal a1 = new Dog();
```

The variable is `Animal` but the object is `Dog`. Then why didn't Java call `Animal.sound()` instead of `Dog.sound()`?

This is where runtime polymorphism comes in.

## What happens at compile time?

The compiler only checks: *"Is `sound()` available in `Animal`?"*

It finds:

```java
class Animal {
    void sound() {}
}
```

So compilation succeeds. The compiler does **NOT** decide which implementation will run. It simply says: *"Okay, Animal has this method."*

## What happens in memory?

Suppose we execute:

```java
Animal a1 = new Dog();
```

Memory looks like:

```
Stack

a1
 |
 |
 v

Heap

Dog Object
------------------
Animal fields
Dog fields

sound() -> Dog's implementation
```

Notice:

- The reference variable is `Animal`.
- But the actual object is `Dog`.

## When we call `a1.sound();`

The JVM performs these steps.

**Step 1** — Looks at `a1`. Reference type = `Animal`. Method exists? **Yes.** Proceed.

**Step 2** — Looks at the object stored inside. Actual object type is `Dog`.

**Step 3** — Checks: *"Does Dog override sound()?"* **Yes.** Execute `Dog.sound()`.

Output:

```
Dog barks
```

## What if Dog doesn't override?

```java
class Dog extends Animal {

}
```

Now:

```java
Animal a = new Dog();

a.sound();
```

The JVM checks: `Dog` has no `sound()`. Moves to the parent. Finds `Animal.sound()`.

Output:

```
Animal makes sound
```

## Bigger example (multi-level inheritance)

```java
class Animal {

    void sound() {
        System.out.println("Animal");
    }

}

class Dog extends Animal {

    @Override
    void sound() {
        System.out.println("Dog");
    }

}

class Puppy extends Dog {

    @Override
    void sound() {
        System.out.println("Puppy");
    }

}
```

Now:

```java
Animal a = new Puppy();

a.sound();
```

Output:

```
Puppy
```

Why? The JVM starts with the **actual object** (`Puppy`):

```
Puppy → Has sound()? YES → Call Puppy.sound()
```

It never goes to `Dog` or `Animal` because it already found the **most specific override**.

## Dynamic Method Dispatch (the vtable)

Imagine the object has a hidden table (often called a **virtual method table** or **vtable**) that tells the JVM where each overridden method lives.

```
Animal Object     sound() ---> Animal.sound()

Dog Object        sound() ---> Dog.sound()

Cat Object        sound() ---> Cat.sound()
```

When you write:

```java
a.sound();
```

the JVM checks the object's table:

```
a
|
v

Dog Object

sound() ---> Dog.sound()
```

So it jumps directly to `Dog.sound()`. This lookup happens at **runtime**, which is why it's called **runtime polymorphism**.

## Another interesting example (reference type limits access)

```java
class Animal {
    void sound() {
        System.out.println("Animal");
    }
}

class Dog extends Animal {
    @Override
    void sound() {
        System.out.println("Dog");
    }

    void eat() {
        System.out.println("Dog eating");
    }
}
```

Now:

```java
Animal a = new Dog();

a.sound();   // Works
a.eat();     // Error
```

Why? The compiler only knows `a` as an `Animal`. It checks:

- `Animal` has `sound()`? ✔
- `Animal` has `eat()`? ✘

So `a.eat()` is a **compile-time error**, even though the object is actually a `Dog`.

If you want to call `eat()`, you need to **downcast**:

```java
Dog d = (Dog) a;
d.eat();
```

## Summary

```java
Animal a = new Dog();
```

- **Reference type** (`Animal`) determines what methods the compiler allows you to call.
- **Actual object type** (`Dog`) determines which overridden implementation the JVM executes at runtime.

This runtime selection of the method implementation is called **runtime polymorphism** or **dynamic method dispatch**.

A simple way to remember it:

| Stage        | Uses               | Purpose                                          |
|--------------|--------------------|--------------------------------------------------|
| Compile time | Reference type     | Checks whether the method **can be called**      |
| Runtime      | Actual object type | Chooses the **overridden method to execute**     |

This separation between reference type and object type is the foundation of polymorphism in Java and enables flexible, extensible designs where the same code can work with many different subclasses.

---

# Polymorphism with Abstract Classes & Interfaces

Polymorphism shines when combined with abstraction.

## Abstract class example

```java
abstract class Shape {
    abstract double area();
}

class Circle extends Shape {
    double radius;
    Circle(double radius) { this.radius = radius; }

    @Override
    double area() {
        return Math.PI * radius * radius;
    }
}

class Rectangle extends Shape {
    double length, breadth;
    Rectangle(double length, double breadth) {
        this.length = length;
        this.breadth = breadth;
    }

    @Override
    double area() {
        return length * breadth;
    }
}
```

Usage:

```java
Shape s;

s = new Circle(5);
System.out.println(s.area());       // 78.53...

s = new Rectangle(4, 6);
System.out.println(s.area());       // 24.0
```

## Interface example

```java
interface Payment {
    void pay(int amount);
}

class CreditCard implements Payment {
    public void pay(int amount) {
        System.out.println("Paid " + amount + " via Credit Card");
    }
}

class UPI implements Payment {
    public void pay(int amount) {
        System.out.println("Paid " + amount + " via UPI");
    }
}
```

Usage:

```java
Payment p;

p = new CreditCard();
p.pay(500);          // Paid 500 via Credit Card

p = new UPI();
p.pay(500);          // Paid 500 via UPI
```

The calling code depends only on the `Payment` interface, not on the concrete class.

---

# Why use Polymorphism?

### Without Polymorphism ❌

```java
void makeSound(String type) {
    if (type.equals("Dog")) {
        System.out.println("Dog barks");
    } else if (type.equals("Cat")) {
        System.out.println("Cat meows");
    }
    // add more if-else for every new animal...
}
```

Every new animal forces you to edit this method.

### With Polymorphism ✅

```java
void makeSound(Animal animal) {
    animal.sound();
}
```

Add a new `Animal` subclass and it just works — **no change** to `makeSound()`.

### Benefits

- **Flexibility & extensibility** — add new types without changing existing code (Open/Closed Principle).
- **Cleaner code** — no long `if-else`/`switch` chains.
- **Reusability** — write code against a common type (parent/interface).
- **Maintainability** — behavior lives in each class, not scattered in conditionals.

---

# Special Cases & Gotchas

## 1. Static methods are NOT overridden (Method Hiding)

Static methods belong to the class, not the object. Redefining a static method in a subclass is called **method hiding**, not overriding.

```java
class Parent {
    static void show() { System.out.println("Parent static"); }
}

class Child extends Parent {
    static void show() { System.out.println("Child static"); }
}

Parent p = new Child();
p.show();   // Parent static  (decided by reference type, NOT object)
```

## 2. Fields are NOT polymorphic

Variables are resolved by **reference type**, not object type.

```java
class Parent { int x = 10; }
class Child extends Parent { int x = 20; }

Parent p = new Child();
System.out.println(p.x);   // 10  (reference type decides)
```

Only **methods** are polymorphic (dynamically dispatched); **fields** are not.

## 3. private and final methods

- `private` methods are not visible to subclasses — cannot be overridden.
- `final` methods cannot be overridden.

## 4. Constructors

Constructors are **not** polymorphic and cannot be overridden (they can be overloaded, though).

---

# Interview Questions

### Q1. What is polymorphism?

The ability of a single entity (method, object, or operator) to take **many forms**. Same name, different behavior depending on context.

### Q2. Types of polymorphism in Java?

- **Compile-time (static)** → Method Overloading, resolved by the compiler.
- **Runtime (dynamic)** → Method Overriding, resolved by the JVM at runtime.

### Q3. Difference between overloading and overriding?

- **Overloading:** same class, same method name, **different parameters**, resolved at compile time.
- **Overriding:** parent–child, same signature, **different body**, resolved at runtime.

### Q4. Can we overload a method by changing only the return type?

No. Return type is not part of the method signature, so the compiler cannot distinguish the calls.

### Q5. Can we override a static method?

No. Static methods are hidden, not overridden — the call is resolved by **reference type** at compile time (method hiding).

### Q6. Can we override a private or final method?

No. `private` methods are not inherited, and `final` methods are explicitly locked from overriding.

### Q7. Are fields (instance variables) polymorphic?

No. Fields are resolved by the **reference type**, not the object type. Only overridden methods are dynamically dispatched.

### Q8. What is Dynamic Method Dispatch?

The runtime mechanism where a call to an overridden method is resolved based on the **actual object type**, not the reference type.

### Q9. What is upcasting?

Assigning a subclass object to a parent class reference — e.g. `Animal a = new Dog();`. It enables runtime polymorphism.

### Q10. Does Java support operator overloading?

Not for user-defined types. The only built-in overloaded operator is `+` (numeric addition vs String concatenation). Unlike C++, Java does not let you define custom operator overloads.

### Q11. Why is polymorphism useful?

It lets you write flexible, extensible code — add new types without modifying existing logic (Open/Closed Principle), and avoid long `if-else`/`switch` chains.

---

# Interview One-liner

**Polymorphism means "one name, many forms." Java achieves it two ways: compile-time via method overloading (same name, different parameters) and runtime via method overriding (subclass redefines a parent method, resolved by the actual object type through dynamic method dispatch).**
