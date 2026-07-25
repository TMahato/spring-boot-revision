# Single-Threaded Server/Client

A minimal TCP server and client using Java's Socket API, referenced from
`learning/7-Networking-And-Threading.md` (Single-threading section).

The server handles **one client at a time**: it accepts a connection, sends a
greeting, closes it, then loops back to accept the next one. While it is busy
with one client, others must wait — that is the single-threaded limitation.

## Structure
```
2-SingleThreaded/
└── SingleThreaded/        <- package folder (files declare `package SingleThreaded;`)
    ├── Server.java
    └── Client.java
```

## How to run

Open **two terminals** in this folder (`projects/2-SingleThreaded`).

Compile:
```bash
javac SingleThreaded/*.java
```

Terminal 1 — start the server (listens on port 8010):
```bash
java SingleThreaded.Server
```

Terminal 2 — run the client:
```bash
java SingleThreaded.Client
```

Expected output:

- Server:
  ```
  Server is listening on port 8010
  Connection accepted from client /127.0.0.1:xxxxx
  Server is listening on port 8010
  ```
- Client:
  ```
  Reponse from the socket is : Hello from the server
  ```

The server keeps listening; stop it with `Ctrl+C`.

## Note on `toClient.close()`

The course code creates `PrintWriter toClient = new PrintWriter(...)` and calls
`toClient.println(...)`, but a `PrintWriter` without auto-flush buffers its
output internally. Without flushing, the client's `readLine()` blocks forever
(nothing was ever sent).

We call **`toClient.close()`** after writing. Closing the *writer* flushes its
buffer to the socket and then closes the connection — so the client actually
receives the message. (Closing the raw socket instead would discard the
PrintWriter's buffer and the client would read `null`.)

Alternatives that also work:
- `new PrintWriter(acceptedConnection.getOutputStream(), true)` — auto-flush on
  every `println`.
- `toClient.flush()` after the `println`.
