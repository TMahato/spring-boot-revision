# Reference Type vs Actual Object Type (Compiler vs JVM)

> **The compiler looks at the reference type. The JVM looks at the actual object type.**

This is probably the single most important concept in Java OOP. Once you understand it, polymorphism becomes very easy.

---

## Analogy: TV Remote

Suppose you have a TV remote.

```java
Remote remote = new SamsungRemote();
```

- **Reference type** = `Remote`
- **Actual object** = `SamsungRemote`

The remote (reference) only exposes the buttons that *every* remote should have:

```
Power
Volume
Channel
```

It doesn't matter if it's Samsung, Sony, or LG.

When you press **Power**, who actually turns on? The **Samsung TV**, because the actual device connected is Samsung.

Java works exactly the same way.

---

## Now in Java

```java
class Animal {
    void sound() {
        System.out.println("Animal sound");
    }
}

class Dog extends Animal {
    @Override
    void sound() {
        System.out.println("Dog barks");
    }
}
```

Now:

```java
Animal a = new Dog();
```

This line has **two parts**.

### 1. Reference type

```java
Animal a
```

means *"Treat this variable as an Animal."* **The compiler only sees this.** It thinks: `a is an Animal`.

### 2. Actual object

```java
new Dog()
```

means *"Create a Dog object in memory."* **The JVM sees this at runtime.**

So memory looks like:

```
Stack                 Heap

a  ----------------->  Dog Object
                       ----------
                       sound() -> Dog's version
```

Notice:

- The **variable** says `Animal`.
- The **object** is actually `Dog`.

---

## When the compiler sees `a.sound();`

The compiler asks only one question:

> Does `Animal` have a method named `sound()`?

```java
class Animal {
    void sound() {}
}
```

Yes → **Compilation succeeds.** The compiler does **not** care that the object is a `Dog`.

---

## When the JVM runs

Now execution begins. The JVM follows the reference:

```
a
 |
 v
Dog Object
```

Then it asks: *"What kind of object am I pointing to?"* → **Dog**

Now it checks: *"Does Dog override sound()?"* → **Yes** → Execute `Dog.sound();`

Output:

```
Dog barks
```

---

## Why doesn't the compiler decide?

Imagine this method:

```java
void makeSound(Animal a) {
    a.sound();
}
```

Now call it like this:

```java
makeSound(new Dog());
makeSound(new Cat());
makeSound(new Cow());
```

At **compile time**, the compiler only knows: `Parameter type = Animal`. It has no idea whether you'll pass a `Dog`, `Cat`, or `Cow` when the program actually runs.

Only at **runtime** does the JVM know the real object. That's why the compiler cannot decide which implementation to call.

---

## The reference type limits what you can call

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

a.sound();    // ✅ Allowed — Animal has sound()

a.eat();      // ❌ Compile-time error
```

Why does `a.eat()` fail? Because the compiler checks only the **reference type** (`Animal`), and `Animal` doesn't have an `eat()` method. Even though the actual object is a `Dog`, the compiler doesn't use that information.

---

## Easy rule to remember

For this line:

```java
Animal a = new Dog();
```

Split it into two parts:

| Part        | Used by  | Purpose                                          |
|-------------|----------|--------------------------------------------------|
| `Animal a`  | Compiler | Decides **what methods you are allowed to call** |
| `new Dog()` | JVM      | Decides **which overridden method actually runs**|

So when you write `a.sound();`:

```
Compiler: "Is sound() present in Animal?"     ✅ Yes → compile.
JVM:      "Which object is a pointing to?"     → Dog.
JVM:      "Does Dog override sound()?"         ✅ Yes.
Result:   Dog.sound() is executed.
```

This is exactly what people mean by:

> *"The compiler looks at the reference type, while the JVM looks at the actual object type."*
