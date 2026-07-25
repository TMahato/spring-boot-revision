# Thread-Pool Server/Client

A TCP server that serves clients using a **fixed pool of reusable threads**
(`ExecutorService`), referenced from `learning/7-Networking-And-Threading.md`
(Thread Pool section, §7).

## Why this exists (the progression)

| Project | Model | Problem it solves / has |
|---------|-------|-------------------------|
| [`../2-SingleThreaded`](../2-SingleThreaded) | 1 thread total | Simple, but clients wait in line |
| [`../3-MultiThreaded`](../3-MultiThreaded) | 1 new thread **per client** | Parallel, but **unbounded** → 10k clients = 10k threads = memory spike |
| **`4-ThreadPool`** (this) | **N reusable threads + a queue** | Parallel **and bounded** → scales safely |

## How it works

- The server creates the pool **once**, before the accept loop:
  ```java
  ExecutorService threadPool = Executors.newFixedThreadPool(100);
  ```
  This makes 100 worker threads plus an internal task **queue**.
- For each accepted connection the main thread does **not** create a thread — it
  just **submits** the work:
  ```java
  threadPool.submit(new ClientHandler(acceptedConnection));
  ```
- The pool schedules the task onto a thread:
  - an **idle** pool thread runs it immediately;
  - if all 100 threads are **busy**, the task **waits in the queue** until one
    frees up.
- Threads are **reused** across clients (never destroyed per request), so their
  names like `pool-1-thread-7` recur for many different clients.

Result: at most `POOL_SIZE` clients are processed at once — memory and
context-switching stay **bounded** no matter how many clients arrive.

## Structure
```
4-ThreadPool/
└── ThreadPool/               <- package folder (files declare `package ThreadPool;`)
    ├── Server.java           <- Server + ClientHandler, backed by ExecutorService
    └── Client.java
```

## How to run

Compile (from this folder, `projects/4-ThreadPool`):
```bash
javac ThreadPool/*.java
```

Start the server (terminal 1):
```bash
java ThreadPool.Server
```

Run several clients at once (Git Bash / Linux):
```bash
java ThreadPool.Client &
java ThreadPool.Client &
java ThreadPool.Client &
wait
```

Each client is handled by a **pool** thread, all in parallel (~3s total):
```
Reponse from the socket is : Hello from the server (handled by pool-1-thread-1)
Reponse from the socket is : Hello from the server (handled by pool-1-thread-2)
Reponse from the socket is : Hello from the server (handled by pool-1-thread-3)
```

Stop the server with `Ctrl+C`.

## Try it: see the queue in action

Change `POOL_SIZE` in `Server.java` to `2`, recompile, then fire **4** clients
at once. Only 2 are served in the first ~3s; the other 2 wait in the queue and
finish in the next ~3s (so ~6s total) — and you'll see only two distinct thread
names (`pool-1-thread-1`, `pool-1-thread-2`) reused for all four clients.
