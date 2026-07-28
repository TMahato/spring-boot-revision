# Chapter 7 — Containerization & the API Gateway (Docker, Compose, Kong)

> **Scope:** How both services get packaged into images, how the whole system —
> two Spring Boot apps, MySQL, Kafka and a Kong gateway — comes up with a single
> command, and how Kong sits in front as the one door into the system.
>
> Chapter 5 and 6 dealt with how the two services talk to each other
> *asynchronously* through Kafka. This chapter deals with the *synchronous* front
> door, and with the packaging that makes "run the whole system" a one-liner
> instead of a checklist.
>
> §12 maps every concept here onto the exact files in this repo.

**Table of contents**

1. [The problem containers solve](#1-the-problem-containers-solve)
2. [Docker fundamentals](#2-docker-fundamentals)
3. [Writing a Dockerfile for a Spring Boot service](#3-writing-a-dockerfile-for-a-spring-boot-service)
4. [`.dockerignore` — the build context](#4-dockerignore--the-build-context)
5. [Container networking — why `localhost` breaks](#5-container-networking--why-localhost-breaks)
6. [Configuration: the twelve-factor rule](#6-configuration-the-twelve-factor-rule)
7. [Docker Compose](#7-docker-compose)
8. [The stateful dependencies](#8-the-stateful-dependencies)
9. [API Gateways — what and why](#9-api-gateways--what-and-why)
10. [Kong](#10-kong)
11. [Running and troubleshooting](#11-running-and-troubleshooting)
12. [Where this lives in the codebase](#12-where-this-lives-in-the-codebase)
13. [Quick revision sheet](#13-quick-revision-sheet)

---

## 1. The problem containers solve

Right now, starting this system by hand means: install JDK 21 and Maven, install
MySQL, install and configure Kafka, create both schemas and a user with rights on
them, start the broker, start two Spring Boot apps in the right order, and hope
every version matches what the code expects.

That checklist is the problem. Three specific symptoms:

- **"Works on my machine."** The auth service's database URI was pinned to
  `192.168.29.195` — a laptop on someone's LAN. Nobody else could run it.
- **Version drift.** Chapter 6 §1 relies on producer and consumer speaking the
  same `kafka-clients`. Nothing enforced that but a comment in a `pom.xml`.
- **Order of startup.** The user service needs MySQL and Kafka *ready*, not just
  installed.

A container image freezes the answer to all of that: the JRE, the jar, the
filesystem, the entrypoint — one immutable artifact that runs identically
everywhere. Compose then declares how the containers relate.

---

## 2. Docker fundamentals

### 2.1 Image vs container

An **image** is a read-only template: a stack of filesystem layers plus metadata
(entrypoint, exposed ports, env, working directory). A **container** is a running
instance of an image with a thin writable layer on top.

```
   Dockerfile  ──build──►  Image  ──run──►  Container(s)
   (recipe)                (frozen         (running process,
                            artifact)       writable top layer)
```

One image, many containers. Anything a container writes goes to its own writable
layer and **dies with the container** — which is why databases need volumes (§7.4).

### 2.2 Layers and the build cache

Every instruction in a Dockerfile creates a layer. Docker caches them and reuses
a layer only if that instruction *and every instruction before it* are unchanged.

This single rule dictates Dockerfile structure. If you write:

```dockerfile
COPY . .
RUN mvn package          # ← re-downloads every dependency on any source change
```

then editing one line of Java invalidates the `COPY`, which invalidates the
`RUN`, and Maven re-resolves the entire dependency tree. Instead, copy the thing
that changes *rarely* first:

```dockerfile
COPY pom.xml .
RUN mvn dependency:go-offline    # ← cached until pom.xml changes
COPY src ./src
RUN mvn package                  # ← only this re-runs on a code change
```

### 2.3 Not a VM

A VM virtualizes hardware and runs a full guest OS. A container is just a Linux
process with namespaces (its own view of PIDs, network, mounts) and cgroups
(resource limits). It shares the host kernel. That's why a container starts in
milliseconds and a VM takes a minute.

On Windows, Docker Desktop runs a small Linux VM underneath — so the containers
are still real Linux containers, they just sit inside one VM instead of running
on the host kernel directly.

---

## 3. Writing a Dockerfile for a Spring Boot service

### 3.1 Multi-stage builds

Building needs a JDK and Maven (~800 MB of tooling). *Running* needs only a JRE
and one jar. A **multi-stage build** uses one image to build and a different,
smaller one to run, copying only the artifact across:

```dockerfile
# ---- stage 1: build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- stage 2: runtime ----
FROM eclipse-temurin:21-jre
COPY --from=build /build/target/*.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Everything in stage 1 — Maven, the JDK, the `.m2` cache, the source — is
discarded. Only what `COPY --from=build` names survives. Result: roughly 250 MB
instead of ~900 MB.

Both `pom.xml` files here use `spring-boot-maven-plugin`, so `mvn package`
already produces an executable fat jar. There is no wrapper (`mvnw`) in this
repo, which is exactly why stage 1 uses a Maven base image rather than
`./mvnw`.

### 3.2 `-DskipTests` is not laziness here

The user service's tests use `@EmbeddedKafka` and H2, but the auth service's
would want a live MySQL. A Docker build has no network access to your compose
services and no dependency ordering. Tests belong in CI, before the image build —
not inside it.

### 3.3 Running as a non-root user

By default a container's process runs as root. If an attacker escapes the
process, they are root inside a container that shares your kernel. Creating an
unprivileged user costs two lines:

```dockerfile
RUN useradd -r -u 1001 appuser
USER appuser
```

### 3.4 Container-aware heap sizing

The JVM reads cgroup limits, but its default of 25% of available memory for the
heap is wasteful in a container that exists to run one process. Set it
explicitly:

```dockerfile
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
```

Use `MaxRAMPercentage`, **not** `-Xmx`. A percentage adapts when you change the
container's memory limit; a hardcoded `-Xmx512m` does not, and will happily
exceed a 256 MB limit and get OOM-killed.

### 3.5 `EXPOSE` documents, it does not publish

`EXPOSE 8080` is metadata. It does not open a port to your host — only
`ports:` in Compose (or `docker run -p`) does that. Containers on the same
network can reach each other on any port regardless of `EXPOSE`.

---

## 4. `.dockerignore` — the build context

Before building, the Docker CLI tars up the build context (the directory you
point it at) and ships it to the daemon. Without a `.dockerignore`, that includes
`target/` — every compiled class and fat jar you already built locally —
and `.idea/`, and `.git/`.

Two consequences:

1. **Slow builds.** Tens of MB uploaded on every build.
2. **Broken caching.** `COPY src ./src` is fine, but any `COPY . .` would pull in
   a locally-built `target/` whose timestamps change constantly, busting the
   cache every single time — and potentially baking a *stale* jar into the image.

`.dockerignore` uses the same syntax as `.gitignore` and is read from the root of
the build context.

---

## 5. Container networking — why `localhost` breaks

**The single most common mistake.** Inside a container, `localhost` means *that
container*, not your host and not another container. `localhost:9092` inside the
auth service container means "a Kafka broker inside the auth service container" —
which does not exist.

Compose creates a user-defined bridge network and runs an embedded DNS server on
it. Every service is resolvable **by its service name**:

```
        ┌──────────────── docker network: expense-net ────────────────┐
        │                                                             │
        │   kong  ──http://authservice:8080──►  authservice           │
        │     │                                     │                 │
        │     └───http://userservice:9810──►  userservice             │
        │                                     │     │                 │
        │        mysql:3306  ◄────────────────┴─────┤                 │
        │          ├─ authservice schema            │                 │
        │          └─ userservice schema            │                 │
        │        kafka:9092  ◄──────────────────────┘                 │
        └─────────────────────────────────────────────────────────────┘
              │                    │
        ports: 8000:8000     ports: 29092:29092
              ▼                    ▼
                        HOST (your laptop)
```

So the config becomes `MYSQL_HOST=mysql`, `KAFKA_HOST=kafka`, and Kong proxies to
`http://authservice:8080`. Service name, never an IP: container IPs are assigned
by Docker and change on every recreate.

### 5.1 The Kafka listener trap, again

Chapter 5 §8.3 flagged this for a VPS; containers hit the identical problem for a
different reason.

A Kafka client's first connection is a **metadata request**. The broker replies
with the addresses of the partition leaders — its `advertised.listeners` — and
the client reconnects to *those*. So if the broker advertises `kafka:9092`, only
clients that can resolve `kafka` work. That's the app containers, but not a
client on your host.

The fix is **two listeners on two ports**:

| Listener | Advertised as | Used by |
|---|---|---|
| `PLAINTEXT` | `kafka:9092` | app containers, over the Docker network |
| `PLAINTEXT_HOST` | `localhost:29092` | IntelliJ / CLI tools on your laptop |

Both point at the same broker. They differ only in the address the broker hands
back. Get this wrong and the classic symptom is: the client connects, then hangs
and times out — because it connected once for metadata, then tried to reach an
unresolvable address.

---

## 6. Configuration: the twelve-factor rule

**Config that varies between environments belongs in the environment, not in the
image.** One image must run in dev, staging and prod with only env vars
differing. Otherwise you are not deploying the artifact you tested.

Spring Boot makes this easy because `${VAR:default}` in a properties file is
resolved from, in decreasing priority: environment variables → JVM system
properties → the properties file default. So:

```properties
spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
```

runs against `localhost:9092` from your IDE with no env set, and against
`kafka:9092` in Compose where the env var is supplied — same code, same image.

Also note Spring's **relaxed binding**: the env var `SPRING_DATASOURCE_URL`
maps onto the property `spring.datasource.url` automatically. Either style
works; explicit `${...}` placeholders are clearer about what is intended to be
overridden, which is why this project uses them.

### 6.1 Secrets

Passwords must not be in the image or in git. Compose automatically reads a
`.env` file sitting next to `docker-compose.yml` and substitutes `${VAR}` into
the compose file. `.env` is already in `.gitignore`; a `.env.example` with dummy
values is committed so others know which variables exist.

This is adequate for local development. It is *not* production secret
management — for that you want Docker secrets, Vault, or your cloud provider's
secret manager, none of which put the plaintext on disk.

---

## 7. Docker Compose

Compose declares a multi-container application in one YAML file. `docker compose
up` reconciles reality to that declaration.

### 7.1 `depends_on` alone is a lie

```yaml
depends_on:
  - mysql            # ← waits for the CONTAINER to start, not MySQL to be READY
```

MySQL's container starts in a second; MySQL itself takes ten or more to
initialize. Spring Boot's Hikari pool tries to connect immediately, fails, and
the app exits. You then blame Docker.

### 7.2 Healthchecks are the fix

A `healthcheck` runs a command inside the container on an interval; the container
is `healthy` only once it passes. Combine with the long form of `depends_on`:

```yaml
mysql:
  healthcheck:
    test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
    interval: 10s
    timeout: 5s
    retries: 10
    start_period: 30s

userservice:
  depends_on:
    mysql:
      condition: service_healthy
```

`start_period` is the grace window during which failures don't count against
`retries` — it exists precisely for slow-initializing databases.

> **Healthchecks need a tool to run.** `eclipse-temurin:21-jre` is a minimal
> image with no `curl` and no `wget`. Either install `curl` in the runtime stage
> (what this project does) or write a healthcheck that uses only bash's
> `/dev/tcp`. A healthcheck referencing a binary that isn't there reports
> `unhealthy` forever and nothing ever starts.

### 7.3 Health endpoints

Both services now expose Spring Boot Actuator at `/actuator/health`. That is
better than a hand-rolled `return true` endpoint because Actuator aggregates the
real dependency health — a DataSource validation query, Kafka connectivity — so
an app that is up but has lost its database reports `DOWN`.

The auth service is behind Spring Security, so `/actuator/health` has to be
explicitly permitted or the healthcheck gets a 401 and the container never turns
healthy.

### 7.4 Volumes

A container's writable layer is deleted with the container. `docker compose down`
would therefore wipe your database. A **named volume** is storage managed by
Docker that outlives any container:

```yaml
services:
  mysql:
    volumes:
      - mysql-data:/var/lib/mysql
volumes:
  mysql-data:
```

`docker compose down` keeps volumes. `docker compose down -v` deletes them — that
is your "factory reset", and the flag to be careful with.

> **Related:** the user service had `spring.jpa.hibernate.ddl-auto=create`, which
> drops and recreates the schema on **every startup**. With that set, a volume is
> pointless — the data is destroyed by the app itself on each restart. It is now
> `${JPA_DDL_AUTO:update}`.

---

## 8. The stateful dependencies

### 8.1 MySQL — one server, two schemas

Both services are on MySQL, and both share a single container:

```
              mysql:3306
                  │
      ┌───────────┴───────────┐
      ▼                       ▼
  authservice            userservice
  ├─ users               └─ users
  ├─ user_roles
  └─ tokens
```

This is still **database-per-service**: neither service can see the other's
tables, they share no entities, and the only thing that crosses between them is
the Kafka event from Chapter 6. Splitting them onto separate servers later is a
connection-string change and nothing else. Running one container instead of two
is purely a development convenience.

The image's env vars create exactly **one** database and **one** user:

- `MYSQL_DATABASE` → creates `userservice`
- `MYSQL_USER` / `MYSQL_PASSWORD` → creates `appuser` with full rights **on that
  database only**

So the second schema needs an init script. Any `.sql` or `.sh` file placed in
`/docker-entrypoint-initdb.d/` runs on **first initialization only** — that is,
only when the data volume is empty. Ours creates `authservice` and grants
`appuser` on it.

Two things that catch people:

1. **Order.** The entrypoint creates `MYSQL_USER` *before* running init scripts,
   so the `GRANT` in the script always has a user to target.
2. **First-init-only.** Edit the script and restart and *nothing happens* — the
   volume is no longer empty. You need `docker compose down -v`.

A plain `.sql` file cannot read `${MYSQL_USER}`, so the username is hardcoded in
the script. Change `MYSQL_USER` in `.env` and you must change it there too.

### 8.2 Grants and `'appuser'@'%'`

MySQL identifies a user by **name *and* host**. The app connects from another
container, so from MySQL's point of view it is a remote client — the grant must
be to `'appuser'@'%'`, not `'appuser'@'localhost'`. Getting this wrong produces
`Access denied for user 'appuser'@'172.18.0.5'`, where the IP is the app
container's address on the bridge network.

### 8.3 Kafka in KRaft mode

Modern Kafka has dropped ZooKeeper. **KRaft** mode has Kafka manage its own
metadata through an internal Raft quorum, so a single container is a complete
cluster. The relevant settings:

- `KAFKA_PROCESS_ROLES=broker,controller` — one node doing both jobs
- `KAFKA_CONTROLLER_QUORUM_VOTERS=1@kafka:9093` — the quorum is just itself
- `CLUSTER_ID` — a fixed UUID; the storage directory is formatted with it
- `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1` — the default is 3, which **cannot
  be satisfied by a one-broker cluster**. Leave it at 3 and consumer group
  offset commits fail with `NOT_ENOUGH_REPLICAS`. Same for the two
  `TRANSACTION_STATE_LOG_*` settings.
- the dual listeners from §5.1

---

## 9. API Gateways — what and why

Without a gateway, every client needs to know that auth lives on `:8080` and
users on `:9810`, and every service has to implement rate limiting, CORS,
logging and TLS for itself.

```
   WITHOUT                              WITH
   client ──► authservice:8080          client ──► Kong :8000 ──► authservice:8080
   client ──► userservice:9810                       │
                                                     └─────────► userservice:9810
```

An API gateway is a reverse proxy that is aware of your APIs. It gives you:

- **One entry point.** Clients know one host. Services stay unpublished on an
  internal network.
- **Routing.** Path/host/method → upstream service.
- **Cross-cutting concerns in one place.** Rate limiting, authentication, CORS,
  request/response transformation, correlation IDs, logging — configured once
  rather than reimplemented per service.
- **Decoupling.** Split, rename or move a service and only the gateway's route
  changes.

**What it is not:** a load balancer (it does that, but it is API-aware, not
generic L4), and not a service mesh (a mesh handles service-to-service traffic,
"east-west"; a gateway handles client-to-system traffic, "north-south").

---

## 10. Kong

Kong is an API gateway built on nginx + OpenResty. Its model is four objects:

| Object | Meaning |
|---|---|
| **Service** | An upstream you proxy to — here, `http://authservice:8080` |
| **Route** | A rule matching an incoming request (path, host, method) → a Service |
| **Plugin** | Behaviour attached globally, or to one Service or Route |
| **Consumer** | An identified caller, for auth and per-caller rate limits |

Request flow: `Route` matches → plugins run → forwarded to the `Service`'s
upstream → response plugins run → returned.

### 10.1 Ports

| Port | Purpose |
|---|---|
| `8000` | Proxy — **this is where your API traffic goes** |
| `8443` | Proxy over TLS |
| `8001` | Admin API — configuration and inspection |
| `8444` | Admin API over TLS |

The Admin API must never be publicly reachable. In production it is bound to
localhost or firewalled; here it is published so you can inspect the running
config.

### 10.2 DB-less (declarative) mode

Kong can run backed by Postgres, or **DB-less**: the entire configuration comes
from one YAML file loaded at boot. DB-less is the right choice here — no extra
database container, no migration step, and the gateway config is version
controlled and reviewable like any other file. Set `KONG_DATABASE=off` and point
`KONG_DECLARATIVE_CONFIG` at the mounted file.

The trade-off: the Admin API becomes read-only. You change config by editing the
file and reloading, which is what you want anyway.

### 10.3 `strip_path` — the setting that will catch you

```yaml
routes:
  - name: auth-routes
    paths:
      - /auth/v1
    strip_path: false
```

`strip_path: true` (**Kong's default**) removes the matched prefix before
forwarding: a request for `/auth/v1/signup` arrives at the service as `/signup`.

Our controllers are mapped at `@PostMapping("auth/v1/signup")` — the full path.
So we need `strip_path: false`, which forwards the path untouched. Leave it at
the default and every request 404s at the service while Kong reports success.

### 10.4 Plugins configured here

- **`correlation-id`** — attaches an `X-Request-ID` header if absent, so one
  request can be traced across Kong, the auth service and the user service. This
  is the cheapest observability win available in a distributed system.
- **`rate-limiting`** — a per-minute cap. In DB-less mode the policy must be
  `local` (counters in each node's memory); the `cluster` policy needs a
  database, and `redis` needs a Redis.

### 10.5 What is deliberately *not* here

Kong has a `jwt` plugin that could validate the auth service's tokens at the
gateway, rejecting unauthenticated traffic before it ever reaches a service. It
is not enabled, because it needs the HS256 signing secret registered against a
Kong Consumer, and this project currently has that secret commented out in
`application.properties` (see the auth service config, and Chapter 4). Wiring
that up is the natural next step: it would let the user service stop being
implicitly trusted just because it sits on an internal network.

---

## 11. Running and troubleshooting

```bash
cd ExpenseTracker
cp .env.example .env            # then edit the passwords
docker compose up --build       # add -d to detach
```

Traffic goes through Kong on **8000**:

```bash
curl -X POST http://localhost:8000/auth/v1/signup \
     -H 'Content-Type: application/json' \
     -d '{"username":"jassi","password":"secret"}'
```

Useful commands:

| Command | Purpose |
|---|---|
| `docker compose ps` | State and health of every container |
| `docker compose logs -f authservice` | Follow one service's logs |
| `docker compose up -d --build authservice` | Rebuild and restart just one |
| `curl localhost:8001/routes` | What Kong actually loaded |
| `docker compose down` | Stop; **keep** volumes |
| `docker compose down -v` | Stop and **delete** data |

Symptom → cause:

| Symptom | Cause |
|---|---|
| `Connection refused` to a dependency | Using `localhost` instead of the service name (§5) |
| Kafka client connects then times out | `advertised.listeners` (§5.1) |
| MySQL `Access denied for user 'appuser'@'172.x.x.x'` | Grant is to `@'localhost'`, not `@'%'` (§8.2) |
| MySQL `Unknown database 'authservice'` | Init script didn't run — volume wasn't empty (§8.1) |
| App exits at startup, DB "not ready" | Missing `condition: service_healthy` (§7.1) |
| Container stuck `starting` forever | Healthcheck binary missing from the image (§7.2) |
| Kong returns 404 from the service | `strip_path` (§10.3) |
| Kong `no Route matched` | Path prefix doesn't match any route |
| Init script edits have no effect | It only runs on an empty volume — `down -v` (§8.1) |

---

## 12. Where this lives in the codebase

Everything above is implemented in these files.

### 12.1 New files

| File | What it implements |
|---|---|
| `authService/Dockerfile` | Multi-stage build, non-root user, `MaxRAMPercentage`, curl for healthchecks — §3 |
| `authService/.dockerignore` | Build context trimming — §4 |
| `userService/Dockerfile` | Same, port 9810 — §3 |
| `userService/.dockerignore` | §4 |
| `docker-compose.yml` | All 5 services, network, volumes, healthchecks, `depends_on` conditions — §5, §7, §8 |
| `.env.example` | Every overridable secret/setting — §6.1 |
| `infra/kong/kong.yml` | Declarative Kong config: 2 services, 2 routes, plugins — §10 |
| `infra/mysql/init-databases.sql` | Creates the `authservice` schema and grants `appuser` on it — §8.1 |

### 12.2 Modified files

| File | Change | Why |
|---|---|---|
| `authService/src/main/resources/application.properties` | Mongo URI → MySQL `spring.datasource.*` with `${MYSQL_HOST:…}` placeholders; added `management.endpoints…` | Migrated off MongoDB (§12.5); the hardcoded LAN IP `192.168.29.195` was also unreachable from a container — §6 |
| `authService/pom.xml` | `+ spring-boot-starter-actuator` | `/actuator/health` for the Compose healthcheck — §7.3 |
| `authService/src/main/java/…/auth/SecurityConfig.java` | `permitAll()` on `/actuator/health/**` | Security returned 401 to the healthcheck, so the container never became healthy — §7.3 |
| `userService/pom.xml` | `+ spring-boot-starter-actuator` | §7.3 |
| `userService/src/main/resources/application.properties` | `ddl-auto` → `${JPA_DDL_AUTO:update}`; added `management.endpoints…` | `create` wiped the schema on every restart, making the volume pointless — §7.4 |

Plus the MongoDB → MySQL migration in §12.5.

### 12.3 Reading order

1. `authService/Dockerfile` — the §2/§3 concepts in ~20 lines.
2. `docker-compose.yml` — read `kafka` for §5.1, `mysql` for §8.1, `userservice`
   for §7.1–7.2.
3. `infra/kong/kong.yml` — §10, and note `strip_path: false` against the
   controller mappings in `AuthController.java:31` and `UserController.java:19,28`.

### 12.4 Ports after this change

| Service | In-network address | Published to host |
|---|---|---|
| Kong proxy | `kong:8000` | **`localhost:8000`** ← use this |
| Kong admin | `kong:8001` | `localhost:8001` |
| authService | `authservice:8080` | `localhost:8080` (direct, for debugging) |
| userService | `userservice:9810` | `localhost:9810` (direct, for debugging) |
| Kafka | `kafka:9092` | `localhost:29092` |
| MySQL | `mysql:3306` | `localhost:3306` |

The two app services stay published only so you can compare direct-vs-gateway
behaviour while learning. In a real deployment you would remove their `ports:`
entirely, leaving Kong as the only reachable entry point — which is the whole
point of §9.

### 12.5 The MongoDB → MySQL migration

The auth service originally stored users, roles and refresh tokens in MongoDB.
It now uses MySQL/JPA like the user service, so the stack has one database
technology instead of two. Mongo is gone from the codebase entirely.

**Dependencies** — `authService/pom.xml`: `spring-boot-starter-data-mongodb` out,
`spring-boot-starter-data-jpa` + `mysql-connector-j` in.

**Entity mapping** — the interesting part, because the two models differ in what
they can express:

| Concern | MongoDB (before) | JPA / MySQL (after) |
|---|---|---|
| `UserInfo` | `@Document(collection = "users")` | `@Entity @Table(name = "users")` |
| `userId` | `@Id`, manually assigned UUID | unchanged — `@Id`, **no** `@GeneratedValue` |
| unique `username` | `@Indexed(unique = true)` | `@Column(unique = true)` |
| `Set<UserRole>` | embedded documents in the user doc | `@ElementCollection` + `@CollectionTable(name = "user_roles")`, `UserRole` is `@Embeddable` |
| `RefreshToken` | `@Document(collection = "tokens")` | `@Entity @Table(name = "tokens")` |
| `RefreshToken.id` | `String` ObjectId from the DB | `Long` + `@GeneratedValue(IDENTITY)` — it never crosses a service boundary, so the DB can own it |
| token → user link | `@DBRef` | `@ManyToOne` + `@JoinColumn(name = "user_id")` — a real foreign key |
| Repositories | `MongoRepository<T, String>` | `JpaRepository<UserInfo, String>`, `JpaRepository<RefreshToken, Long>` |

**Two fetch decisions that are not cosmetic.** Mongo had no lazy loading, so
nothing in the old code worried about it. JPA does, and two call sites read
associations *outside* any transaction:

- `CustomUserDetails` iterates `getUserRoles()` during authentication. An
  `@ElementCollection` is LAZY by default → `LazyInitializationException` on
  every login. Hence `fetch = FetchType.EAGER`.
- `TokenController.refreshToken()` maps `RefreshToken::getUserInfo` then reads
  `getUsername()`. Same problem → the `@ManyToOne` is EAGER too.

Also note `@ToString.Exclude` / `@EqualsAndHashCode.Exclude` on
`RefreshToken.userInfo`: Lombok's `@Data` would otherwise walk into `UserInfo`
on every log line and every comparison.

**Unchanged on purpose.** `userId` stays a manually assigned UUID rather than
becoming an auto-increment column — it is the id carried in the Kafka event and
used as the user service's primary key (Chapter 6 §4.4). Letting MySQL generate
it would break that agreement. One consequence: Spring Data sees a non-null id
on a new entity, so `save()` routes through `merge()` (a SELECT then an INSERT)
rather than `persist()`. Correct, just one extra query per signup.

**Infrastructure** — `infra/mongo/init-mongo.js` deleted; the `mongo` service
and `mongo-data` volume removed from `docker-compose.yml`;
`infra/mysql/init-databases.sql` added to create the second schema (§8.1).

**Still open:** the `UserInfoDto extends UserInfo` coupling flagged in Chapter 6
§7.5 survives the migration — the DTO now extends a JPA entity instead of a Mongo
document, which is the same design problem wearing different annotations.

---

## 13. Quick revision sheet

- **Image** = frozen artifact; **container** = running instance + writable layer
  that dies with it. State needs a **volume**.
- A layer is cached only if it and everything before it is unchanged → copy
  `pom.xml` and resolve dependencies *before* copying `src`.
- **Multi-stage build**: JDK+Maven to build, JRE to run, `COPY --from` the jar.
  ~900 MB → ~250 MB.
- `.dockerignore` keeps `target/`, `.git/`, `.idea/` out of the build context.
- `EXPOSE` documents; `ports:` publishes. Containers reach each other without
  either.
- **`localhost` inside a container is that container.** Use Compose service
  names. Never IPs.
- Kafka hands clients its `advertised.listeners` — one listener for the Docker
  network (`kafka:9092`), one for the host (`localhost:29092`).
- Single-broker Kafka needs replication factors set to **1**, or offset commits
  fail.
- `${VAR:default}` in `application.properties` = one image, every environment.
- `depends_on` waits for *start*; add `healthcheck` + `condition:
  service_healthy` to wait for *ready*.
- A healthcheck needs its binary to exist in the image — JRE images have no curl.
- MySQL's env vars create **one** database and **one** user; a second schema
  needs an init script in `/docker-entrypoint-initdb.d/`. Init scripts run **only
  on an empty volume** — editing one and restarting does nothing.
- Grant to `'user'@'%'`, not `@'localhost'`: an app in another container is a
  remote client to MySQL.
- `docker compose down` keeps volumes; `-v` destroys them.
- **Gateway** = north-south (client→system); **mesh** = east-west
  (service→service).
- Kong model: **Service** (upstream) ← **Route** (match rule) + **Plugin**
  (behaviour) + **Consumer** (caller).
- Kong `8000` proxy, `8001` admin. Never expose the admin port publicly.
- **DB-less mode**: config from one YAML, no Postgres, Admin API read-only.
- **`strip_path` defaults to `true`** and will silently 404 your controllers.
  Ours are mapped at the full path, so it must be `false`.

---

## References

- Chapter 4 — Security internals (why `/actuator/health` needs permitting)
- Chapter 5 §8.3 — Kafka listener configuration, the VPS version of §5.1
- Chapter 6 — what actually travels between these two containers
- Docker docs — multi-stage builds, `.dockerignore`, Compose file reference
- Kong docs — DB-less and declarative configuration, Routes, Plugin hub
- The Twelve-Factor App — factor III, Config
