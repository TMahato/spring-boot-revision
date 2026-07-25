# SQL vs NoSQL, CAP, ACID, VPS & VMs — Notes

## 1. SQL vs NoSQL

Two families of databases. SQL = **structured, relational, schema-first**;
NoSQL = **flexible, non-relational, schema-optional**.

| | **SQL (Relational)** | **NoSQL (Non-relational)** |
|--|----------------------|----------------------------|
| Data model | **Tables** (rows + columns) | Documents / key-value / wide-column / graph |
| Schema | **Fixed** — define columns upfront | **Flexible** — fields vary per record |
| Relationships | **JOINs**, foreign keys | denormalize / embed; joins are weak or manual |
| Query language | **SQL** (standardized) | per-DB APIs (Mongo query, CQL, …) |
| Scaling | **Vertical** (bigger server) mostly | **Horizontal** (add nodes / shard) natively |
| Consistency | Strong (**ACID**) | Often eventual (**BASE**), tunable |
| Best when | Structured data, transactions, complex queries | Huge scale, evolving schema, high write throughput |
| Examples | PostgreSQL, MySQL, Oracle, SQL Server | MongoDB, Cassandra, DynamoDB, Redis, Neo4j |

### The 4 NoSQL types

| Type | Shape | Good for | Examples |
|------|-------|----------|----------|
| **Document** | JSON-like docs | flexible records, content, catalogs | MongoDB, Couchbase |
| **Key-Value** | `key → value` | caching, sessions, fast lookups | Redis, DynamoDB |
| **Wide-Column** | rows with dynamic columns | huge write volume, time-series | Cassandra, HBase |
| **Graph** | nodes + edges | relationships (social, fraud, recommendations) | Neo4j, Neptune |

### How to choose

- **SQL** → data is structured & relational, you need **transactions**
  (banking, orders, inventory), complex reporting/JOINs, strong consistency.
- **NoSQL** → schema changes often, **massive scale / horizontal growth**,
  high write throughput, or a shape that fits a specific model (docs, graph,
  cache).

> Not either/or — real systems mix them (**polyglot persistence**): Postgres for
> orders + Redis for cache + Elasticsearch for search.

---

## 2. ACID (SQL transaction guarantees)

**ACID** = the four guarantees a relational transaction gives. A **transaction**
is a group of operations that must all succeed or all fail as one unit.

| Letter | Property | Meaning | Example |
|--------|----------|---------|---------|
| **A** | **Atomicity** | All steps happen, or **none** do (all-or-nothing) | Money leaves A **and** arrives at B, or neither |
| **C** | **Consistency** | DB moves from one **valid** state to another; constraints hold | balance never goes negative; FK stays valid |
| **I** | **Isolation** | Concurrent transactions don't step on each other | two transfers on the same account don't corrupt it |
| **D** | **Durability** | Once committed, it **survives crashes** (on disk) | power loss after commit → data still there |

```
BEGIN;
  UPDATE account SET balance = balance - 100 WHERE id = 1;   -- debit
  UPDATE account SET balance = balance + 100 WHERE id = 2;   -- credit
COMMIT;   -- both applied atomically; a crash mid-way rolls back (Atomicity)
```

- **Isolation levels** (weakest → strongest): `READ UNCOMMITTED` → `READ
  COMMITTED` → `REPEATABLE READ` → `SERIALIZABLE`. Higher = safer but slower.
  Anomalies they prevent: **dirty read**, **non-repeatable read**, **phantom read**.
- In Spring this is `@Transactional` — implemented as an **AOP proxy**
  (begin → commit on success → rollback on exception). See
  [`10-AOP`](./10-AOP-Aspect-Oriented-Programming.md) and
  [`9-Proxy`](./9-Proxy-In-Spring-Boot.md).

### BASE — the NoSQL counterpart

Many NoSQL DBs trade ACID for **BASE**: **B**asically **A**vailable, **S**oft
state, **E**ventually consistent. Prioritizes availability & scale over immediate
consistency — replicas converge to the same value *eventually*.

---

## 3. CAP theorem

For any **distributed** data store, during a **network partition** you can
guarantee **at most two** of these three:

| Letter | Property | Meaning |
|--------|----------|---------|
| **C** | **Consistency** | Every read sees the **latest** write (all nodes agree) |
| **A** | **Availability** | Every request gets a (non-error) response |
| **P** | **Partition tolerance** | System keeps working despite dropped/broken network between nodes |

```
                 C
                / \
               /   \
    CP  ◀─────/     \─────▶  AP
             /       \
            /         \
           A ───────── P
   (CA only exists with NO partitions — i.e. a single node / non-distributed)
```

**The real trade-off:** in a distributed system, network **partitions happen**,
so **P is non-negotiable**. The actual choice is **C vs A** *during a partition*:

| Choice | Behaviour during a partition | Pick when | Examples |
|--------|------------------------------|-----------|----------|
| **CP** | Reject/block requests to stay consistent | correctness > uptime (banking, inventory) | MongoDB, HBase, Zookeeper, most RDBMS clusters |
| **AP** | Answer anyway, reconcile later (eventual) | uptime > perfect freshness (feeds, carts, DNS) | Cassandra, DynamoDB, CouchDB |

- **CA** is only meaningful for a **non-distributed / single node** — no
  partition to tolerate, so it's not a practical choice for clustered systems.
- **PACELC** extends CAP: *if Partition → choose C or A; **Else** (normal
  operation) → choose **L**atency or **C**onsistency.* Captures the everyday
  latency-vs-consistency trade even when there's no partition.

> One line: **CAP is about what breaks when the network splits.** You keep
> Partition tolerance and pick **Consistency (CP)** or **Availability (AP)**.

---

## 4. Where your app/DB runs: local server, VM, VPS

When you build a project you need somewhere to run the app + database. Options,
from your own machine to the cloud:

| Option | What it is | You manage | Best for |
|--------|-----------|------------|----------|
| **Local machine** | Your laptop/desktop | everything | development, learning |
| **VM (Virtual Machine)** | A full OS running **on top of a hypervisor** on some host | the guest OS + app | isolation on your own hardware, testing |
| **VPS (Virtual Private Server)** | A VM **rented from a cloud provider**, public IP, always-on | OS + app (provider owns hardware) | hosting a real project cheaply 24/7 |
| **Dedicated server** | A whole physical machine rented | OS + app | high, steady load |
| **Managed / PaaS** | Provider runs the runtime (Heroku, Render, RDS) | just your code/data | fastest to ship, least ops |

### VM (Virtual Machine)

A **software emulation of a full computer** — its own OS, CPU, RAM, disk —
running on a **hypervisor** (VirtualBox, VMware, KVM, Hyper-V) that slices one
physical host into several isolated guests.

```
┌─────────────────────────────────────────────┐
│  Physical host (your PC / a server)           │
│  ┌───────────────── Hypervisor ─────────────┐ │
│  │  ┌── VM1 ──┐  ┌── VM2 ──┐  ┌── VM3 ──┐    │ │
│  │  │ Guest OS│  │ Guest OS│  │ Guest OS│    │ │  each VM = full OS
│  │  │  app    │  │  app    │  │  app    │    │ │
│  │  └─────────┘  └─────────┘  └─────────┘    │ │
│  └───────────────────────────────────────────┘ │
└─────────────────────────────────────────────────┘
```

- **Pros:** strong isolation, run a different OS (Linux VM on Windows), snapshot
  & reset, mirror production locally.
- **Cons:** heavy — each VM ships a full OS → slow boot, lots of RAM/disk.
- **As a local server:** run your Spring Boot app + Postgres inside a Linux VM to
  reproduce the production environment before deploying.

> **VM vs Container (Docker):** a VM virtualizes the **hardware** (full guest OS
> each); a container virtualizes the **OS** (shares the host kernel) → containers
> are far **lighter/faster** but less isolated. For most project setups today,
> **Docker/containers** replace VMs for packaging the app + DB.

### VPS (Virtual Private Server)

A **VM that a cloud provider rents you** — you get root, a public IP, and it runs
**24/7**, unlike your laptop. Providers: DigitalOcean, Linode, AWS Lightsail,
Hetzner, Contabo.

```
Your laptop (dev) ──deploy──▶ VPS (always-on VM in a datacenter, public IP)
                                 ├─ Nginx / reverse proxy
                                 ├─ your Spring Boot jar (systemd service)
                                 └─ PostgreSQL
                              accessible at  https://your-domain.com
```

- **Why for a project:** cheap ($4–10/mo), always online (your laptop isn't),
  real public URL, full control (install anything). Ideal first "real server" to
  host a side project.
- **You manage:** OS updates, security (firewall, SSH keys), the DB, backups,
  the web server — more responsibility than PaaS, more control than PaaS.
- **Typical deploy:** SSH in → install Java/DB → run the jar as a `systemd`
  service (or Docker) → put **Nginx** in front for TLS/domain.

### Quick decision

- **Learning / dev** → run locally (or a local VM/Docker to match prod).
- **Ship a side project cheaply, full control** → **VPS**.
- **Don't want to manage servers** → **PaaS / managed DB** (Render, Railway, RDS).
- **Need to reproduce prod OS exactly on your machine** → **VM** (or containers).

---

### Quick recap
- **SQL** = tables, fixed schema, JOINs, **ACID**, scales **vertically** — use for
  structured, transactional data. **NoSQL** = flexible schema, scales
  **horizontally**, 4 types (document / key-value / wide-column / graph), often
  **BASE/eventual** — use for scale & evolving data. Real apps mix both.
- **ACID** = **A**tomicity (all-or-nothing), **C**onsistency (valid states),
  **I**solation (concurrent safety), **D**urability (survives crashes); Spring's
  `@Transactional` is an **AOP proxy** around it. NoSQL counterpart = **BASE**.
- **CAP** = in a distributed DB during a **network partition** you get 2 of 3;
  **P is mandatory**, so choose **CP** (consistent, may reject) or **AP**
  (available, eventual). **CA** ≈ single node only. **PACELC** adds the
  latency-vs-consistency trade during normal operation.
- **VM** = full emulated computer (own OS) on a **hypervisor** — heavy, strong
  isolation; **container/Docker** is the lighter modern alternative.
- **VPS** = a VM **rented from a provider**, always-on with a public IP — the
  cheap, full-control way to **host a project 24/7**; you manage the OS, DB,
  security. **PaaS** trades control for zero server management.
