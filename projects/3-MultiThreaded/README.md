# Multi-Threaded Server/Client

A TCP server that spawns **one thread per client**, referenced from
`learning/7-Networking-And-Threading.md` (Multi-threading section).

Compare with [`../2-SingleThreaded`](../2-SingleThreaded): there the server
serves one client fully before accepting the next. Here the main thread only
`accept()`s and immediately hands the connection to a worker thread, so many
clients are served **concurrently**.

## Structure
```
3-MultiThreaded/
└── MultiThreaded/            <- package folder (files declare `package MultiThreaded;`)
    ├── Server.java           <- Server + ClientHandler (Runnable, one per client)
    └── Client.java
```

## How it works

- `Server.run()` loops: `accept()` a connection → wrap it in a `ClientHandler`
  (a `Runnable`) → `new Thread(handler).start()` → loop back immediately.
- Each `ClientHandler` reads the client's message, **sleeps 3 seconds** (to
  simulate work so overlapping clients are easy to see), then replies with the
  handling thread's name and closes the writer.

## How to run

Compile (from this folder, `projects/3-MultiThreaded`):
```bash
javac MultiThreaded/*.java
```

Start the server (terminal 1):
```bash
java MultiThreaded.Server
```

Run several clients at once to see concurrency. On Git Bash / Linux:
```bash
java MultiThreaded.Client &
java MultiThreaded.Client &
java MultiThreaded.Client &
wait
```

### What you'll observe

Each client is handled by a different thread, and all finish in ~3s total
(parallel) rather than ~9s (serial):

- Clients:
  ```
  Reponse from the socket is : Hello from the server (handled by Thread-0)
  Reponse from the socket is : Hello from the server (handled by Thread-1)
  Reponse from the socket is : Hello from the server (handled by Thread-2)
  ```
- Server:
  ```
  Connection accepted from client /127.0.0.1:xxxxx
  [Thread-0] received from client: Hello from the client
  Connection accepted from client /127.0.0.1:xxxxx
  [Thread-1] received from client: Hello from the client
  ...
  ```

Stop the server with `Ctrl+C`.

## The catch (see learning note §6)

One thread per client works, but each thread carries a Thread Control Block
(id, state, CPU registers, priority) and costs memory. At 10,000 concurrent
clients this spikes memory and context-switching overhead — which is why a
**thread pool** (§7) is the next step.
