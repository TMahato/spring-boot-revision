# Classes, Abstract Classes & Interfaces — Complete Notes

> Language focus: **Java** (concepts also apply to C#, C++, etc. with minor syntax changes).

---

## 1. Class

A **class** is a blueprint/template from which objects are created. It bundles **state (fields)** and **behavior (methods)** together.

### Key points
- Can be instantiated directly using `new`.
- Can have fields, constructors, methods (all concrete/implemented).
- Supports the 4 OOP pillars: Encapsulation, Inheritance, Polymorphism, Abstraction.

### Example
```java
public class Car {
    // State (fields)
    private String brand;
    private int speed;

    // Constructor
    public Car(String brand) {
        this.brand = brand;
        this.speed = 0;
    }

    // Behavior (methods)
    public void accelerate(int delta) {
        this.speed += delta;
    }

    public String getBrand() { return brand; }
    public int getSpeed()    { return speed; }
}

// Usage
Car myCar = new Car("Toyota");   // object creation
myCar.accelerate(30);
System.out.println(myCar.getSpeed()); // 30
```

---

## 2. Abstract Class

An **abstract class** is a partially implemented class that **cannot be instantiated**. It's meant to be **extended** by subclasses. Use it when classes share common code AND you want to force subclasses to implement certain behavior.

### Key points
- Declared with the `abstract` keyword.
- **Cannot** be instantiated directly (`new AbstractClass()` → compile error).
- Can have **both** abstract methods (no body) and concrete methods (with body).
- Can have **constructors, fields, static methods, and any access modifiers**.
- A subclass **must** implement all abstract methods (or be declared abstract itself).
- A class can extend **only ONE** abstract class (single inheritance).

### Example
```java
public abstract class Shape {
    protected String name;

    public Shape(String name) {      // abstract classes CAN have constructors
        this.name = name;
    }

    // Abstract method — no body, subclass MUST implement
    public abstract double area();

    // Concrete method — shared by all subclasses
    public void describe() {
        System.out.println(name + " has area = " + area());
    }
}

public class Circle extends Shape {
    private double radius;

    public Circle(double radius) {
        super("Circle");
        this.radius = radius;
    }

    @Override
    public double area() {           // must implement abstract method
        return Math.PI * radius * radius;
    }
}

public class Rectangle extends Shape {
    private double w, h;

    public Rectangle(double w, double h) {
        super("Rectangle");
        this.w = w; this.h = h;
    }

    @Override
    public double area() {
        return w * h;
    }
}

// Usage
// Shape s = new Shape("x");  // ❌ ERROR: cannot instantiate abstract class
Shape c = new Circle(5);
c.describe();                    // "Circle has area = 78.53..."
```

---

## 3. Interface

An **interface** is a fully abstract "contract" that defines **what** a class must do, not **how**. It lists method signatures that implementing classes must fulfill.

### Key points
- Declared with the `interface` keyword; classes use `implements`.
- All methods are implicitly `public abstract` (before Java 8).
- All fields are implicitly `public static final` (constants).
- A class can implement **MULTIPLE** interfaces → solves the "diamond problem" / lack of multiple inheritance.
- **Cannot** have constructors and cannot be instantiated.
- **Java 8+:** can have `default` and `static` methods (with body).
- **Java 9+:** can have `private` methods (to share code between default methods).

### Example
```java
public interface Drawable {
    // constant (public static final by default)
    String VERSION = "1.0";

    // abstract method (public abstract by default)
    void draw();

    // Java 8+ default method — has a body, optional to override
    default void render() {
        System.out.println("Rendering v" + VERSION);
        draw();
    }

    // Java 8+ static method
    static Drawable empty() {
        return () -> System.out.println("Nothing to draw");
    }
}

public interface Resizable {
    void resize(double factor);
}

// A class can implement MULTIPLE interfaces
public class Button implements Drawable, Resizable {
    @Override
    public void draw() {
        System.out.println("Drawing button");
    }

    @Override
    public void resize(double factor) {
        System.out.println("Resizing by " + factor);
    }
}

// Usage
Button b = new Button();
b.render();          // calls default method, which calls draw()
b.resize(1.5);
```

---

## 4. Abstract Class vs Interface — Comparison Table

| Feature | Abstract Class | Interface |
|---|---|---|
| Keyword | `abstract class` | `interface` |
| Instantiation | No | No |
| Methods | Abstract + concrete | Abstract + `default`/`static` (Java 8+) |
| Fields | Any (instance, static, non-final) | Only `public static final` constants |
| Constructor | Yes | No |
| Access modifiers on members | Any (public/protected/private) | Public only (methods) |
| Multiple inheritance | No (extend only 1) | Yes (implement many) |
| Inheritance keyword | `extends` | `implements` |
| When to use | Share common code + state among related classes ("is-a") | Define a capability/contract ("can-do"), unrelated classes |
| State | Can hold state | Stateless (only constants) |

---

## 5. When to Use Which? (Rule of Thumb)

- **Class** → concrete thing you need to create objects of.
- **Abstract class** → related classes share **common implementation and state**, and you want a base type. Think *"is-a"* (a `Circle` **is a** `Shape`).
- **Interface** → define a **capability/behavior** that possibly unrelated classes can have. Think *"can-do"* (a `Bird`, a `Plane`, and a `Superman` can all `Fly`).

> Modern guideline: **Prefer interfaces** for flexibility; use abstract classes only when you need shared state or shared implementation.

> ⭐ **Golden rule (memorize this):** When it's an **identity** → keep it in a **class / abstract class**. When it's a **capability** → keep it in an **interface**.
> - Identity = *what an object IS* (`Bird` **is an** `Animal`) → class / abstract class.
> - Capability = *what an object CAN DO* (`Bird` **can** fly) → interface.

---

## 5b. Deep Dive: "Why interface, not abstract class?" (Classic Interview Case)

> One of the MOST common interview questions. At first glance an abstract class *seems* enough because you can put a concrete `fly()` inside it. Let's analyze why an **interface** is the right choice.

### The setup
```java
interface Flyable {
    void fly();
}

class Bird     implements Flyable { public void fly() { System.out.println("Bird flies");     } }
class Plane    implements Flyable { public void fly() { System.out.println("Plane flies");    } }
class Superman implements Flyable { public void fly() { System.out.println("Superman flies"); } }
```

### Step 1 — "Why not just an abstract class?"
If **every** object flew the *same* way, an abstract class with a concrete method would be perfectly fine:
```java
abstract class FlyingObject {
    void fly() { System.out.println("Flying..."); }
}
class Bird     extends FlyingObject {}
class Plane    extends FlyingObject {}
class Superman extends FlyingObject {}
// Output: Flying... / Flying... / Flying...  → no interface needed
```

### Step 2 — But in reality they DON'T fly the same way
```
Bird.fly()      -> flap wings
Plane.fly()     -> start engines
Superman.fly()  -> use superpower
```
An interface just says *"Anything Flyable must provide a fly() method"* — it does **not** dictate **how**.

### Step 3 — "But an abstract class can force that too!"
True — with an **abstract method**:
```java
abstract class FlyingObject {
    abstract void fly();   // subclasses must implement
}
```
This works. So why do developers *still* prefer an interface here? 👇

### Step 4 — The REAL reason: flying is a *capability*, not an *identity*
Look at what each thing actually **is** — they live in **completely different hierarchies**:
```
Bird     ------> Animal
Plane    ------> Vehicle
Superman ------> Human
```
They share only **one ability**: Fly.

### Step 5 — Java has NO multiple class inheritance
With an abstract class you'd be stuck:
```java
class Bird extends Animal, FlyingObject   // ❌ ERROR — only one parent class allowed
```
With an interface, each class keeps its **natural identity** AND advertises the capability:
```java
class Bird     extends Animal  implements Flyable   // ✅
class Plane    extends Vehicle implements Flyable   // ✅
class Superman extends Human   implements Flyable   // ✅
```
**This is the single biggest reason interfaces exist.**

### Step 6 — Multiple capabilities: the Duck
A duck **is an** Animal, but **can** fly AND **can** swim. You can't create `FlyingAnimal` + `SwimmingAnimal` and inherit both. Interfaces solve it cleanly:
```java
class Duck extends Animal implements Flyable, Swimmable { ... }
```

### Step 7 — Different hierarchy entirely: the RobotBird
A `RobotBird` **is a** `Robot` (not an Animal) but **can** fly:
```java
class RobotBird extends Robot implements Flyable      // ✅ easy
// class RobotBird extends Robot, FlyingObject        // ❌ impossible
```

### Step 8 — "But what about shared fly() code?"
Since **Java 8**, interfaces support **default methods**, removing the old advantage of abstract classes:
```java
interface Flyable {
    default void takeOff() { System.out.println("Preparing to fly"); }
    void fly();
}
// Every class gets takeOff() for free while still writing its own fly().
```

### ✅ Interview one-liner
> **Abstract classes model "what an object IS"; interfaces model "what an object CAN DO."**
> `Bird` **is** an Animal, `Plane` **is** a Vehicle, `Superman` **is** a Human → each inherits its own base class.
> All three **can** fly → they all `implements Flyable`.
> That's why `Flyable` is an **interface**, not an abstract class.

### Summary rule
| Use an **abstract class** when… | Use an **interface** when… |
|---|---|
| Classes share the **same parent** | You describe a **capability / behavior** |
| They have common **state (fields)** | **Unrelated** classes need the same functionality |
| They share a lot of **implementation** | You need **multiple inheritance of behavior** |
| It's an **"is-a"** (identity) relationship | It's a **"can-do"** (capability) relationship |
| e.g. `Animal { int age; void eat(); }` | e.g. `Flyable`, `Runnable`, `Comparable`, `Serializable`, `AutoCloseable` |

---

## 6. Real-World Analogy

- **Interface** = job description (list of duties, no implementation). "Must be able to cook, clean, drive."
- **Abstract class** = a trainee employee with partial training already done — some tasks known, some still to learn.
- **Class** = a fully trained employee ready to work (instantiate).

---

# 📝 Interview Questions & Answers

## Basic

**Q1. What is a class?**
A blueprint for creating objects that encapsulates state (fields) and behavior (methods).

**Q2. What is an abstract class?**
A class declared with `abstract` that cannot be instantiated and may contain abstract (unimplemented) methods. Used as a base class.

**Q3. What is an interface?**
A contract that specifies method signatures a class must implement, defining *what* to do, not *how*.

**Q4. Can you instantiate an abstract class or an interface?**
No. Neither can be instantiated directly. You instantiate a concrete subclass/implementing class. (You *can* use anonymous classes or lambdas that provide the implementation.)

**Q5. Can an abstract class have a constructor?**
Yes. It's called via `super()` when a subclass object is created — used to initialize shared state.

**Q6. Can an interface have a constructor?**
No. Interfaces cannot hold instance state, so no constructor.

---

## Intermediate

**Q7. Difference between abstract class and interface?**
See the comparison table above. Key: abstract class = single inheritance + can hold state/implementation; interface = multiple inheritance + primarily a contract.

**Q8. Why does Java allow multiple interface inheritance but not multiple class inheritance?**
To avoid the **Diamond Problem** (ambiguity when two parent classes have the same method with a body/state). Interfaces (originally) had no implementation/state, so no ambiguity.

**Q9. Can an abstract class have zero abstract methods?**
Yes. You can still mark a class abstract to prevent instantiation even if all methods are implemented.

**Q10. Can an abstract method be `private`, `static`, or `final`?**
No.
- `private` → can't be overridden, contradicts abstract.
- `static` → belongs to class, can't be abstract/overridden.
- `final` → can't be overridden, contradicts abstract.

**Q11. What are default methods in interfaces (Java 8)? Why were they added?**
Methods with a body inside an interface. Added to allow adding new methods to interfaces **without breaking** existing implementing classes (backward compatibility, e.g. `Collection.stream()`).

**Q12. What are static methods in interfaces?**
Utility methods belonging to the interface itself, called as `InterfaceName.method()`. Not inherited by implementing classes.

**Q13. What if a class implements two interfaces having a default method with the same signature?**
Compile error (**diamond ambiguity**). The class **must** override the method and can pick one via `InterfaceName.super.method()`.

```java
class C implements A, B {
    public void hello() {
        A.super.hello();  // explicitly resolve
    }
}
```

**Q14. Can an interface extend another interface?**
Yes, and it can extend **multiple** interfaces: `interface C extends A, B {}`.

**Q15. Can a class be both `abstract` and `final`?**
No. `abstract` needs subclassing; `final` forbids it — contradictory.

---

## Advanced

**Q16. What are marker interfaces? Give examples.**
Interfaces with **no methods**, used to "tag" a class with metadata. Examples: `Serializable`, `Cloneable`, `Remote`. (Modern alternative: annotations.)

**Q17. What is a functional interface?**
An interface with **exactly one abstract method** (SAM — Single Abstract Method). Can be used with lambdas. Annotated `@FunctionalInterface`. Examples: `Runnable`, `Comparator`, `Callable`, `Function`.

```java
@FunctionalInterface
interface Calculator {
    int operate(int a, int b);
}
Calculator add = (a, b) -> a + b;
```

**Q18. Since Java 8 interfaces can have method bodies — is there still a need for abstract classes?**
Yes. Abstract classes can hold **instance state (fields)**, have **constructors**, use **any access modifier**, and support **non-public/protected** members. Interfaces still cannot hold instance state. Use abstract class when shared **state** is needed.

**Q19. Can we declare variables in an interface? What is their nature?**
Yes, but they are implicitly `public static final` (constants). Must be initialized.

**Q20. What is the diamond problem and how does Java resolve it for default methods?**
When a class inherits the same method from multiple sources causing ambiguity. Java forces the class to **override** and explicitly choose via `Interface.super.method()`.

**Q21. Can an interface have private methods?**
Yes, from **Java 9+**. Used to share common code between `default` methods without exposing it.

**Q22. Difference between `extends` and `implements`.**
- `extends` → class inherits a class, or interface inherits interface(s).
- `implements` → class provides implementation for interface(s).

**Q23. Can an abstract class implement an interface?**
Yes, and it need **not** implement all interface methods — it can leave them abstract for its subclasses to implement.

**Q24. Explain "Program to an interface, not an implementation."**
Depend on abstractions (interface types) rather than concrete classes. Improves flexibility, testability (mocking), and loose coupling.

```java
List<String> list = new ArrayList<>();  // ✅ program to List interface
// vs ArrayList<String> list = new ArrayList<>();  // tightly coupled
```

**Q25. Can constructors be overridden? Can they be abstract?**
No to both. Constructors are not inherited, so they cannot be overridden or declared abstract.

**Q26. What happens if a subclass does not implement all abstract methods of its parent?**
The subclass must itself be declared `abstract`; otherwise it's a compile error.

---

## Quick "Gotcha" / Rapid-Fire

| Question | Answer |
|---|---|
| Interface variables are…? | `public static final` |
| Interface methods (pre-Java 8) are…? | `public abstract` |
| Multiple inheritance of classes in Java? | Not allowed |
| Multiple inheritance of interfaces? | Allowed |
| Abstract class without abstract methods? | Allowed |
| Instantiate interface with lambda? | Yes, if functional interface |
| Default method added in? | Java 8 |
| Private interface method added in? | Java 9 |
| `final` + `abstract` together? | Illegal |
| Interface can have `main` method? | Yes (static method, Java 8+) |

---

## C# Note (if asked)
- Interfaces: since **C# 8** also support default implementations.
- C# uses `:` for both inheritance and interface implementation.
- C# has no `implements`/`extends` keywords; uses `abstract`, `virtual`, `override`, `sealed` (≈ `final`).
- C# supports **properties** in interfaces; single class inheritance, multiple interface implementation (same as Java).
