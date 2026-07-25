# Networking & Threading — Notes

## 1. The Big Picture: How the Internet is Addressed

Think of reaching a server like reaching a physical house:

| Concept | Real-world analogy | What it is |
|---------|-------------------|------------|
| ISP     | Country            | Internet Service Provider — owns/manages lots of machines |
| IP      | Street name        | Locates a machine on the network |
| Port    | House name/number  | Locates a specific process/service on that machine |

- A **server** always lives in a **region** and exposes a **public address** (IP + port).
- An **ISP** connects many machines to the internet.

### Client → Server flow
```
client ──▶ DNS ──▶ server
```
- The **client** doesn't know the server's IP/port up front.
- **DNS** (Domain Name System) resolves a human-friendly name (e.g. `example.com`) into the **IP and port** needed to reach the server.

---

## 2. Protocols = The Rules

A **protocol** defines *how* data is transferred and *what the rules are*.

### TCP vs UDP

| | TCP | UDP |
|--|-----|-----|
| Connection | Connection-oriented (handshake first) | Connectionless (fire & forget) |
| Reliability | Guaranteed, ordered delivery | No guarantee, may drop/reorder |
| Speed | Slower (more overhead) | Faster (less overhead) |
| Use when | Data integrity matters: web pages, email, file transfer | Speed matters, loss is OK: video/audio streaming, gaming, DNS |

### TCP 3-way handshake
```
client ───────────────────▶ server
        ──▶  SYN
        ◀──  SYN-ACK
        ──▶  ACK
   (connection established)
```

---

## 3. HTTP (built on TCP)

| Version | Connection behavior |
|---------|--------------------|
| HTTP/1.0 | **Non-persistent** — new TCP connection per request |
| HTTP/1.1 | **Persistent** — reuse the same connection; send `FIN` to close the socket |

**Other application-layer protocols:** SMTP (email sending), FTP (file transfer), and more — each has its own rules, most built on top of TCP.

---

## 4. The Socket API (Client ↔ Server)

Sockets are the programming interface for network communication.

```
┌─────────────────────────────────────────────────────────┐
│  Client                     Server                        │
│                                                           │
│   C1 ───── socket.listen() ─────▶  S   ip1, port1         │
│    \                                   (Socket API)       │
│     \                                                     │
│      ─── socket.accept() ──────▶   S   ip2, port2         │
│   (Socket API)                                            │
└─────────────────────────────────────────────────────────┘
```

Typical server-side socket lifecycle:
1. `socket()` — create the socket
2. `bind()` — attach it to an IP + port
3. `listen()` — mark it ready to accept incoming connections
4. `accept()` — accept a client connection (returns a **new** socket dedicated to that client, e.g. `ip2, port2`)
5. exchange data → `close()`

> Key idea: `listen()` sets up the "front door"; each `accept()` spins off a separate connected socket so the server can talk to that specific client.

---

## 5. Handling Multiple Clients → Threads

When **multiple clients** try to access a **single server**, the server needs a strategy to serve them concurrently.

### Single-threading
- One thread handles one request at a time.
- Client 2 waits until Client 1 is fully served → poor concurrency.
- **Runnable example:** [`projects/2-SingleThreaded`](../projects/2-SingleThreaded) — a `Server.java` that accepts one client, replies "Hello from the server", closes, then loops back; and a `Client.java` that connects and prints the reply. See its `README.md` for run steps.

### Multi-threading
- Spawn a new thread per request → clients served in parallel.
- **Example:** 3 clients hit the server → 3 threads run "simultaneously," each handling one client.
- **Runnable example:** [`projects/3-MultiThreaded`](../projects/3-MultiThreaded) — the server hands each accepted connection to its own `Thread`, so 3 concurrent clients finish in ~3s instead of ~9s. See its `README.md`.

### Context switching
- A CPU core runs one thread at a time; the OS rapidly **switches** between threads to create the illusion of parallelism.
- **Example:** Thread A runs → OS saves A's state → loads Thread B → runs B → switches back. Each switch has a cost (saving/restoring state).

---

## 6. Drawback of Multi-threading

Each thread carries a **Thread Control Block (TCB)**:
- `id`
- `state`
- `CPU registers`
- `priority`

**Problem:** 10,000 requests → 10,000 threads → each consumes memory → **memory usage spikes** (plus heavy context-switching overhead). This doesn't scale.

---

## 7. Thread Pool — The Fix

Instead of unlimited threads, keep a **fixed pool** + a **task queue**.

```
        incoming tasks
             │
             ▼
        ┌─────────┐        ┌──────────────────┐
        │  Queue  │ ─────▶ │ Thread Pool (100)│
        └─────────┘        │  T1 T2 ... T100  │
                           └──────────────────┘
```

- **Pool size:** e.g. 100 threads.
- Tasks wait in a **queue**.
- A task is pushed to a thread **only when a thread in the pool is sitting idle**.
- Caps memory usage and context-switching regardless of how many requests arrive.
- **Runnable example:** [`projects/4-ThreadPool`](../projects/4-ThreadPool) — the server uses `Executors.newFixedThreadPool(100)` and `submit()`s each client instead of spawning a thread; threads are reused (`pool-1-thread-N`). The README shows how to shrink the pool to watch tasks queue up. See its `README.md`.

### `ExecutorService` — Java's thread pool API

In Java you rarely manage a thread pool by hand. `java.util.concurrent.ExecutorService`
is the built-in abstraction: **you hand it tasks, it manages the threads and the queue.**

```java
ExecutorService pool = Executors.newFixedThreadPool(100); // create pool of 100 threads
pool.submit(new ClientHandler(connection));               // hand off a task
pool.shutdown();                                          // stop accepting, finish queued tasks
```

**Create one with the `Executors` factory:**

| Factory method | What it gives you |
|----------------|-------------------|
| `newFixedThreadPool(n)` | Exactly `n` threads; extra tasks wait in an **unbounded queue**. (What we use.) |
| `newCachedThreadPool()` | Creates threads on demand, **reuses** idle ones, kills them after 60s idle. Good for many short tasks. |
| `newSingleThreadExecutor()` | Just 1 thread; tasks run one at a time in order. |
| `newScheduledThreadPool(n)` | Runs tasks after a delay or on a repeating schedule. |
| `newVirtualThreadPerTaskExecutor()` | (Java 21+) a lightweight **virtual thread** per task — cheap enough to skip pooling. |

**Submitting work:**
- `execute(Runnable)` — fire-and-forget; returns nothing.
- `submit(Runnable / Callable)` — returns a **`Future`**, a handle to the result/status.
  - `Callable<T>` is like `Runnable` but **returns a value** and can throw checked exceptions.
  - `Future.get()` **blocks** until the task finishes and returns its result.

```java
Future<Integer> f = pool.submit(() -> 2 + 2); // Callable returning Integer
Integer result = f.get();                      // blocks, then result == 4
```

**Shutting down (important — the JVM won't exit while pool threads are alive):**
- `shutdown()` — stop accepting new tasks; let already-queued ones finish.
- `shutdownNow()` — try to stop running tasks immediately and skip the queue.
- `awaitTermination(timeout)` — block until the pool is fully done (or timeout).

**Why prefer `ExecutorService` over `new Thread()` per request:**
- Bounds the number of live threads → **no memory spike** at 10k requests (§6).
- **Reuses** threads instead of create/destroy per task → less overhead.
- Gives you a queue, `Future` results, scheduling, and clean shutdown for free.

### `ExecutorService` vs `CompletableFuture`

Both run tasks on a thread pool, but they answer different questions.
`ExecutorService` is about **where tasks run** (the pool); `CompletableFuture`
is about **how tasks are wired together** (the async pipeline).

| | `ExecutorService` (+ `Future`) | `CompletableFuture` |
|--|-------------------------------|---------------------|
| Role | The **thread pool** that executes tasks | An **async result** you can chain and compose |
| Getting the result | `future.get()` — **blocks** the calling thread until done | Attach callbacks (`thenApply`, `thenAccept`) that fire **when ready**, no blocking |
| Chaining | None — you `get()` result 1, then submit task 2 manually | `.thenApply().thenCompose().thenCombine()` — build a non-blocking pipeline |
| Combining many | Manage a `List<Future>` and `get()` each | `allOf(...)` / `anyOf(...)` wait for all / first |
| Error handling | `get()` throws `ExecutionException`; handle with try/catch | `.exceptionally()` / `.handle()` in the chain |
| Manual completion | No | Yes — `complete(value)` from anywhere (e.g. a callback) |

```java
// Future: blocks to get each result, then does the next step by hand
Future<Integer> f = pool.submit(() -> fetchPrice());
int price = f.get();               // <-- thread parked here until done
int withTax = price + tax(price);

// CompletableFuture: describe the whole flow, nothing blocks
CompletableFuture
    .supplyAsync(() -> fetchPrice(), pool)   // run on our pool
    .thenApply(price -> price + tax(price))  // runs automatically when price is ready
    .thenAccept(total -> System.out.println("Total: " + total))
    .exceptionally(ex -> { ex.printStackTrace(); return null; });
```

**Rule of thumb:**
- Just need to run tasks on a pool → `ExecutorService`.
- Need to **combine / chain / react to** several async results without blocking
  threads → `CompletableFuture` (pass it your `ExecutorService` so you still
  control the pool). It is Java's closest answer to JS `Promise` + `async/await`.

---

## 8. JavaScript — Single-Threaded + Event Loop

- JavaScript runs on a **single thread**.
- It achieves concurrency via the **event loop** (not multiple threads).
- Long/async operations (I/O, timers, network) are offloaded; their callbacks are queued and executed when the call stack is free.
- Result: non-blocking behavior without the memory cost of many threads.

---

## 9. Thread Pool vs Event Loop

Two different strategies for handling many concurrent clients.

| | **Thread pool** (Java, Spring, Tomcat) | **Event loop** (Node.js, Nginx, Netty) |
|--|----------------------------------------|-----------------------------------------|
| Threads | Many (e.g. 100–200) | Usually **one** main thread |
| Model | 1 request "owns" a thread until it finishes (blocking) | 1 thread juggles all requests via callbacks (non-blocking) |
| On a slow I/O call | That thread **blocks/waits** doing nothing | Registers a callback and **moves on** to the next request |
| Cost per connection | A thread + its stack (~1 MB) → memory grows with clients | Tiny (just a callback/entry) → handles many idle connections cheaply |
| Bottleneck | Runs out of **threads** under high concurrency | Runs out of **CPU** if any callback does heavy work |
| CPU-heavy work | Fine — spread across many threads/cores | **Dangerous** — one long task blocks the whole loop (must offload to workers) |
| Mental model | "Give each request its own worker" | "One fast worker that never waits, only reacts" |

**Key intuition:**
- Threads block, so you need **many** of them to stay busy → memory-bound.
- The event loop **never blocks** (it hands off I/O and takes a callback), so
  **one** thread stays busy → CPU-bound, cheap memory.
- Neither is "better" — thread pools suit CPU-bound / mixed work and blocking
  libraries; event loops suit I/O-bound, high-connection workloads (chat, APIs,
  streaming).

> Note: an event loop still uses a **small** background thread pool under the
> hood (e.g. libuv in Node) for things that can't be done non-blocking, like
> file system and DNS. The *application* code still runs on the single loop.

---

## 10. JMeter — Load Testing

**Apache JMeter** is a tool to **simulate many concurrent clients** hitting your
server, so you can measure how the concurrency models above actually behave
under load.

- **What it does:** fires lots of requests (HTTP, TCP, DB, etc.) in parallel and
  reports throughput, latency, and errors.
- **Thread Group = virtual users.** You configure:
  - **Number of threads (users):** how many simulated clients (e.g. 1,000).
  - **Ramp-up period:** over how many seconds to start them all (e.g. 1,000
    users over 10s = 100 new users/sec).
  - **Loop count:** how many times each user repeats the request.
- **Samplers:** the actual request to send (e.g. an **HTTP Request** to your
  endpoint). Our socket servers could be hit with a **TCP Sampler**.
- **Listeners:** view results — *Summary Report*, *Aggregate Report*, response
  times, error %, throughput (requests/sec).

**Why it matters here:** JMeter is how you'd *prove* the differences between the
projects — e.g. point 1,000 threads at the single-threaded server (requests
queue up, latency climbs) vs the thread-pool server (bounded, steady) — and find
the point where a server's thread pool is exhausted.

- **Tip:** run big load tests in **non-GUI / CLI mode** (`jmeter -n -t plan.jmx
  -l results.jtl`) — the GUI itself consumes resources and skews results. Use the
  GUI only to build/debug the test plan.

---

### Quick recap
- **Addressing:** ISP → IP → port, resolved by DNS.
- **Transport:** TCP (reliable) vs UDP (fast); TCP uses SYN/SYN-ACK/ACK.
- **HTTP:** 1.0 non-persistent, 1.1 persistent.
- **Sockets:** `listen()` / `accept()` model.
- **Concurrency:** single → multi-thread → thread pool (to bound memory).
- **Java async:** `ExecutorService` = the pool; `CompletableFuture` = chain/compose async results without blocking.
- **JS:** single-threaded, event-loop driven.
- **Thread pool vs event loop:** many blocking threads (memory-bound) vs one non-blocking thread + callbacks (CPU-bound).
- **JMeter:** load-test tool — simulate N concurrent users to measure throughput/latency and find limits.
