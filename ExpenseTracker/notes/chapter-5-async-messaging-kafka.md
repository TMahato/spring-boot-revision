# Chapter 5 — Async Communication, Message Brokers, RabbitMQ & Kafka

> **Scope:** Sync vs async communication in distributed systems, why async
> matters in microservices, the two core messaging patterns (Pub-Sub vs
> Producer-Consumer), then RabbitMQ and Kafka in depth, plus how to actually run
> Kafka on a VPS with Prometheus/Grafana monitoring.
>
> This chapter steps back from the ExpenseTracker code — it is the background you
> need before the service talks to anything other than its own database.
> §10 maps it back onto this project.

**Table of contents**

1. [Sync vs Async communication](#1-sync-vs-async-communication)
2. [Why async communication](#2-why-async-communication)
3. [The two patterns: Pub-Sub vs Producer-Consumer](#3-the-two-patterns-pub-sub-vs-producer-consumer)
4. [RabbitMQ](#4-rabbitmq)
5. [Kafka](#5-kafka)
6. [Kafka ecosystem components](#6-kafka-ecosystem-components)
7. [RabbitMQ vs Kafka — how to choose](#7-rabbitmq-vs-kafka--how-to-choose)
8. [Running Kafka on a VPS + monitoring](#8-running-kafka-on-a-vps--monitoring)
9. [Quick revision sheet](#9-quick-revision-sheet)
10. [Where this fits in ExpenseTracker](#10-where-this-fits-in-expensetracker)

---

## 1. Sync vs Async communication

### Synchronous

Two services talk over an API (REST or GraphQL). The caller **blocks and waits**
for the response — success or error — before moving on. The wait exists to keep
things **in sequence**.

```
Service A  ──── HTTP request ────►  Service B
           ◄─── response ─────────
   (A is blocked this whole time)
```

- **Pro:** simple, immediate result, easy to reason about, errors surface at the
  call site.
- **Con:** A is coupled to B's availability and latency. If B is slow, A is slow.
  If B is down, A fails. Chains of sync calls multiply latency and failure risk.

### Asynchronous

A service **publishes an event** to a broker and immediately continues its own
work. It does **not** care whether anyone consumes the event, or who, or when.

```
Service A ──publish event──► [ Broker / shared data structure ] ──consume──► Service B
   │                                                                          │
   └─ continues immediately                                    processes whenever ready
```

- **Pro:** decoupled in time (B can be down and catch up later), decoupled in
  identity (A doesn't know B exists), absorbs traffic spikes, enables fan-out.
- **Con:** no immediate result, eventual consistency, harder to debug/trace,
  need to handle duplicates and ordering.

### The whiteboard example — driver location

> *"For real-time communication, like syncing a driver's location from server to
> client, we just need to pass the live location through — it isn't necessary to
> save the data immediately. For storing data like driver location we can use
> async."*

This is the classic split in one feature:

| Path | Needs to be | Why |
|---|---|---|
| Server → client live location push | **fast / real-time** (WebSocket, SSE) | the rider is watching the map right now |
| Persisting that location history to the DB | **async** (event → broker → consumer) | nobody is waiting on it; batching it is fine, and a slow DB must not slow the map |

The lesson: **not every piece of work in a request needs to happen inside the
request.** Anything the caller doesn't need an answer to → push it onto a broker.

**Good async candidates:** emails/SMS/push notifications, analytics & audit
logs, image/video processing, search index updates, report generation, cache
warming, location history, webhooks to third parties.

**Keep sync:** anything whose result the caller needs to render or decide on —
auth checks, payment authorization, reading data for the current screen.

---

## 2. Why async communication

Microservices are adopted for specific reasons, and async messaging is what makes
those reasons actually hold up:

1. **Decoupling.** Responsibilities are split across services. Async messaging
   keeps them decoupled at *runtime* too — services don't hold references to each
   other, only to topics/queues. (If every service calls every other service over
   REST, you've built a distributed monolith.)
2. **Independent scaling.** A monolith is a single point of failure and scales as
   one lump. With microservices, the service taking high RPS scales on its own.
   A queue in front of it lets it consume at its own pace instead of being
   overwhelmed — this is **backpressure / load leveling**.
3. **Fault isolation.** If one service goes down, the whole app doesn't. Events
   pile up in the broker and get processed when the service returns — the
   broker acts as a **buffer**, so downtime becomes lag instead of data loss.

Additional benefits that fall out of it:

- **Fan-out** — one event, many independent consumers, no change to the producer.
- **Replay** — with Kafka, a new consumer can re-read history from offset 0.
- **Traffic spikes absorbed** — 10k events/sec in, consumers drain at 1k/sec.

---

## 3. The two patterns: Pub-Sub vs Producer-Consumer

Both put a **middleware / message broker** between services. The difference is
**how many consumers get each message.**

### 3.1 Pub-Sub (Publisher–Subscriber)

A publisher publishes an event to a shared resource organized by **topic /
channel**. Subscribers subscribe to a topic and consume from it. The broker keeps
a **registry**: which producers publish to which topic, and which subscribers are
subscribed to which topic.

```
                    ┌──────────► Subscriber A   (gets a copy)
Publisher ──► Topic ├──────────► Subscriber B   (gets a copy)
                    └──────────► Subscriber C   (gets a copy)
```

- Publisher publishes **without caring who subscribed**.
- Subscriber consumes **without caring who published**.
- **Every subscriber gets every message** — this is broadcast / fan-out.
- Makes the architecture **scalable, decoupled, flexible, fault tolerant**.
- Suited to: broadcasting events to multiple listeners, fan-out messaging.
- **ex: Kafka**

### 3.2 Producer–Consumer

Producers **enqueue** messages onto a shared queue; multiple consumers **compete**
to dequeue from it.

```
                    ┌──► Consumer 1  ─┐
Producer ──► Queue ─┼──► Consumer 2   │  each message goes to exactly ONE of them
                    └──► Consumer 3  ─┘
```

- Each message is processed by **exactly one consumer** — no duplication.
- Work is **divided** among consumers → task distribution and **load balancing**.
- Add consumers → throughput goes up. This is a *competing consumers* pattern.
- **ex: RabbitMQ**

### 3.3 The conclusion (from the whiteboard)

> **Use publisher-subscriber** when you want to **broadcast data to many
> consumers** without the publisher knowing the identity or number of consumers,
> and when the data can be processed synchronously.
>
> **Use producer-consumer** when you want to **distribute data to one or a few
> consumers**. *ex: RabbitMQ*

| | **Pub-Sub** | **Producer-Consumer** |
|---|---|---|
| Message delivered to | **all** subscribers | **one** consumer |
| Intent | broadcast / notify | distribute work |
| Consumers | independent, each has own view | competing for the same pool |
| Adding consumers | more copies delivered | more throughput, same total work |
| Publisher knows consumers? | no | no |
| Typical use | event notification, fan-out | task queues, job processing |
| Classic example | **Kafka** | **RabbitMQ** |

> **Note:** the line is about *patterns*, not products. Kafka does
> producer-consumer *within* a consumer group (one partition → one consumer in
> the group), and RabbitMQ does pub-sub via **fanout exchanges**. The examples
> show what each tool is *shaped for*, not what it's limited to.

---

## 4. RabbitMQ

A traditional **message broker** implementing **AMQP** (Advanced Message Queuing
Protocol). Its defining feature is the **exchange** — a routing layer between the
producer and the queues. Producers never publish to a queue directly; they
publish to an exchange, and the exchange decides which queue(s) it lands in.

```
Producer ──► Exchange ──(binding + routing key)──► Queue ──► Consumer
```

### 4.1 The four exchange types

| Exchange | Routing rule | Use it for |
|---|---|---|
| **Direct** | routing key must **exactly match** the queue's binding key | point-to-point, targeted routing |
| **Fanout** | **broadcast to every queue** bound to the exchange; routing/binding keys ignored | pub-sub / notify everyone |
| **Topic** | routing key matched against a **pattern** (regex-like, wildcards `*` and `#`) | category-based routing |
| **Headers** | routes using the message's **headers** instead of the routing key | routing on structured metadata |

- **Direct** — a message published with routing key `"abc"` goes to the queue
  whose binding key is `"abc"`.
- **Topic** — pattern matching, where `*` matches exactly one word and `#`
  matches zero or more. e.g. `order.*.created` or `logs.#`.

### 4.2 RabbitMQ components

| Component | What it is |
|---|---|
| **Producer** | the service/code that publishes a message |
| **Consumer** | the service that consumes and responds to the message |
| **Message** | a piece of information sent from producer to consumer |
| **TCP Connection** | the connection established between services and the broker so they can communicate |
| **Exchange** | takes the message from the producer and publishes it to a specific queue based on the configured rules/protocol |
| **Queue** | where messages sit until a consumer takes them |
| **Binding** | the pathway established between an exchange and a queue |
| **Routing key** | the key used to determine the destination queue for a message |
| **AMQP** | the advanced message queuing protocol RabbitMQ speaks |
| **Access** | user-level permissions (read/write/configure), typically via username + password |

Ref: <https://www.linkedin.com/pulse/rabbitmq-features-architecture-huzaifa-asif/>

---

## 5. Kafka

Ref: <https://developer.confluent.io/what-is-apache-kafka/>

Kafka is an **event/data streaming platform**, not just a message queue. It can
be used for many purposes: messaging, activity tracking, log aggregation, stream
processing, event sourcing, and as an integration backbone.

### 5.1 What is an "event"?

An event is a **trigger** — a button click, completion of some code, anything —
and it usually carries a small amount of information with it, most often as
**JSON**.

### 5.2 The two layers

| Layer | Responsibility | Contains |
|---|---|---|
| **Compute layer** | producing, consuming and processing events | **Producer**, **Consumer**, **Streams**, **Connector APIs** |
| **Storage layer** | storing data effectively so it scales with demand | topics, partitions, logs on disk, replication |

Separating the two is what lets Kafka scale storage and processing independently.

### 5.3 Topics

Topics are **abstracted containers holding a log data structure, which is
append-only**.

- Writing a message to a topic **appends it at the end of the log**.
- Reading requires maintaining an **offset** — the position of the reader in the
  log. Consumers track their own offset, which is why replay is possible.
- The append-only log is what delivers **very high throughput in and out**:
  sequential disk writes are fast, and there is no random access.
- **Topics are stored on disk as files.** There is **nothing temporary in Kafka**
  — it doesn't initialize a temp buffer between source and destination the way
  most message brokers do. Messages persist for a configured retention period
  even after being consumed.

> This is the biggest mental shift coming from RabbitMQ: **consuming does not
> delete.** The message stays on disk; the consumer just moves its offset. Ten
> different consumer groups can read the same message independently.

### 5.4 Partitioning

A topic's log can't live on a single node — that would cap scalability. So a
**topic is split into multiple partitions**, and those partitions live on
different nodes. Each partition is its own append-only log.

**How Kafka decides which partition a message goes to:**

| Message has a key? | Routing |
|---|---|
| **Yes** | hash of the key → always the **same partition**. Guarantees all messages for that key stay **in order**. |
| **No** | **round-robin** across partitions → even distribution of data |

> **Ordering guarantee:** Kafka only guarantees order **within a partition**, not
> across a topic. If you need all events for `driver_42` in order, use
> `driver_42` as the message key so they all land in the same partition.

### 5.5 Brokers and the cluster

Each **broker is a mini server** holding some topics and partitions. Brokers are
discovered by their **ID** — and once you can reach one broker, the entire Kafka
cluster becomes accessible (it advertises the rest via metadata; this is why the
first broker you connect to is called a *bootstrap server*).

```
┌──────────────── Kafka Cluster ────────────────┐
│  ┌───────────┐  ┌───────────┐                 │
│  │  Broker 1 │  │  Broker 2 │                 │
│  │ ┌───┐┌───┐│  │ ┌───┐┌───┐│  topic-A p0,p1  │
│  │ │p0 ││p1 ││  │ │p2 ││p0'││  topic-B p0…    │
│  │ └───┘└───┘│  │ └───┘└───┘│                 │
│  └───────────┘  └───────────┘                 │
│  ┌───────────┐  ┌───────────┐                 │
│  │  Broker 3 │  │  Broker 4 │   ← replication │
│  └───────────┘  └───────────┘     across      │
└───────────────────────────────────  brokers  ─┘
```

**Scaling Kafka (e.g. running in Docker containers):** scaling the service adds
**new broker instances**, and load is redistributed among the existing and new
brokers. Result: **higher throughput and better fault tolerance.**

### 5.6 Replication — leader & follower

Broker instances are prone to failure, so every partition is replicated across
brokers using a **leader/follower** mechanism:

- One replica is the **leader**; the others are **followers**.
- **Writes and reads happen on the leader**, and the leader always keeps its
  followers in sync (the *in-sync replicas*, ISR).
- When the leader dies, **a follower is promoted to leader** — no data loss, and
  clients transparently reconnect to the new leader.
- `replication.factor = 3` is the common production setting: survives 2 broker
  failures.

### 5.7 Producers and Consumers (the API shape)

| | Class | Purpose |
|---|---|---|
| Producer | `KafkaProducer` | takes the config needed to connect to a Kafka cluster |
| | `ProducerRecord` | holds the **key-value pair** you want to send |
| Consumer | `KafkaConsumer` | takes a config map to connect to the cluster |
| | `ConsumerRecords` | the batch returned when messages are available from the topic |
| | `ConsumerRecord` | an individual message inside that batch |

### 5.8 Consumer Groups

Kafka can have many consumers, and it organizes them at the **consumer group**
level. Each consumer group has a **different purpose** (e.g. `CG1` = billing,
`CG2` = analytics) and each maintains **its own offsets**.

```
     Broker                    CG1  (e.g. notifications)
  ┌──────────┐            ┌──────────┐
  │  ┌────┐  │───────────►│    c1    │
  │  │ p1 │  │            │    c2    │
  │  └────┘  │            │    c3    │
  │  ┌────┐  │───────────►└──────────┘
  │  │ p2 │  │
  │  └────┘  │            CG2  (e.g. analytics)
  │  ┌────┐  │            ┌──────────┐
  │  │ p3 │  │───────────►│    c1    │
  │  └────┘  │            │    c2    │
  └──────────┘            │    c3    │
                          └──────────┘
   each consumer group has a different purpose —
   BOTH groups get ALL the messages, independently
```

**1. Can multiple partitions be consumed by multiple consumer groups? → Yes.**
Multiple consumer groups can consume the same set of partitions
**simultaneously**. Each group can have multiple consumers, and each consumer
within a group is assigned one or more partitions. Kafka ensures **each
partition is consumed by exactly one consumer within each consumer group**.
Different groups consume the same partitions concurrently → parallel processing
and scalability.

**2. Can a single partition be consumed by multiple consumers *within the same*
group? → No.** Kafka follows the **"one-consumer-per-partition"** model: within a
group, each partition is exclusively assigned to one consumer. Extra consumers in
that group sit **idle**. This design ensures each message in a partition is
processed by only one consumer in the group — preserving **message ordering** and
**preventing duplication**.

**The practical rule that falls out of this:**

> **Max useful parallelism per consumer group = number of partitions.**
> 3 partitions + 5 consumers in one group → 2 consumers idle. Want more
> parallelism? Add partitions.

This single mechanism is how Kafka gives you *both* patterns at once:

- **Across groups** → pub-sub (everyone gets a copy)
- **Within a group** → producer-consumer (work split, no duplication)

---

## 6. Kafka ecosystem components

### 6.1 Kafka Connect

Kafka can be used with any data source or sink. e.g. to send data from Kafka to
Elasticsearch, no code is required — just a JSON config:

```json
{
  "connector.class": "io.confluent.connect.elasticsearch.ElasticsearchSinkConnector",
  "topics": "my_topic",
  "connection.url": "http://elasticsearch:9200",
  "type.name": "_doc",
  "key.ignore": "true",
  "schema.ignore": "true"
}
```

**Source connectors** pull data *into* Kafka (e.g. DB change data capture);
**sink connectors** push data *out* (Elasticsearch, S3, JDBC, …).

### 6.2 Schema Registry

A kind of **database that stores schemas** — what is allowed to be stored inside
a topic. It is **exposed as an API** that producers/consumers call to validate
whether they can produce or consume a certain type of message.

Why it matters: without it, a producer changing its JSON shape silently breaks
every consumer. With it, incompatible changes are rejected at publish time, and
schema **evolution** rules (backward/forward compatible) are enforced.

### 6.3 Kafka Streams

A **client library** for building applications and microservices where the input
and output data both live in Kafka clusters. It abstracts data streams as
immutable, ordered sequences of events:

| Abstraction | Meaning |
|---|---|
| **KStream** | a stream of records — every event is an independent fact |
| **KTable** | a changelog stream — latest value per key (a materialized table view) |

**Aggregations and joins are supported out of the box** — so windowed counts,
enrichment joins, and running totals don't require an external processing engine.
It's just a library: you run it inside your own JVM app, no separate cluster.

---

## 7. RabbitMQ vs Kafka — how to choose

| | **RabbitMQ** | **Kafka** |
|---|---|---|
| Model | message broker / task queue | event streaming platform / distributed log |
| Core pattern | producer-consumer (+ fanout for pub-sub) | pub-sub (+ producer-consumer inside a group) |
| Routing | smart broker: exchanges, bindings, routing keys | dumb broker: topic + partition key only |
| Storage | messages **deleted after ack** | **retained on disk** for the retention period |
| Replay | no | **yes** — reset the offset |
| Ordering | per queue, weak under competing consumers | strict **per partition** |
| Throughput | high (tens of thousands/sec) | **very high** (millions/sec) |
| Consumer tracking | broker tracks acks | **consumer tracks its own offset** |
| Protocol | AMQP | custom binary over TCP |
| Best for | task distribution, RPC, complex routing, per-message workflows | event streaming, log aggregation, analytics pipelines, event sourcing, high-volume fan-out |

**Rule of thumb:** if the message is a **job** for someone to do → RabbitMQ. If
the message is a **fact** that many parties may care about now or later → Kafka.

---

## 8. Running Kafka on a VPS + monitoring

### 8.1 AWS Lightsail (VPS) setup

Guide: <https://www.youtube.com/watch?v=ENR-J6c9Xoo>

Set the ubuntu user's password first:

```bash
sudo passwd ubuntu
```

### 8.2 Installing Kafka on Ubuntu 22.04 LTS

Guide: <https://vegastack.com/tutorials/how-to-install-apache-kafka-on-ubuntu-22-04/>

```bash
sudo apt install openjdk-17-jre-headless
```

Give the kafka user sudo permission — add this line via `sudo visudo`:

```
<username> ALL=(ALL) ALL
```

> Download the **binary** Kafka tarball, **not** the `src` tgz.

Start ZooKeeper first, then the broker:

```bash
bin/zookeeper-server-start.sh config/zookeeper.properties
bin/kafka-server-start.sh config/server.properties
```

> ZooKeeper stores cluster metadata (broker registry, controller election).
> Modern Kafka (3.x+) can run **KRaft** mode without ZooKeeper — but the tutorial
> flow above uses the ZooKeeper path.

### 8.3 Listener config — the part that always breaks

In `/path/to/kafka/config/server.properties`:

```properties
# Set the host name or IP address for the broker to listen on
listeners=PLAINTEXT://0.0.0.0:9092

# Set the advertised host name or IP address to be published to clients
advertised.listeners=PLAINTEXT://your_public_ip:9092
```

**Why both.** `listeners` is the socket the broker **binds** to — `0.0.0.0` means
"all interfaces". `advertised.listeners` is the address the broker **hands back
to clients** in its metadata response. A client bootstraps against one broker,
receives the advertised address, then **reconnects to that address** to actually
produce/consume. If `advertised.listeners` is left as `localhost`, remote clients
connect once, get told "go to localhost:9092", and then fail — the classic
"connects but times out on produce" symptom. Also note the **TCP connection**:
clients keep long-lived TCP connections to every broker holding a partition they
use, so all broker ports must be reachable, not just the bootstrap one.

### 8.4 Prometheus + Grafana monitoring

Guide: <https://www.fosstechnix.com/install-prometheus-and-grafana-on-ubuntu-22-04/>

After installing, allow connections to Prometheus:

```bash
sudo nano /etc/prometheus/prometheus.yml
sudo iptables -A INPUT -p tcp --dport 9090 -j ACCEPT
```

For a Spring Boot app: add the Prometheus dependency in `build.gradle` (Micrometer
registry), expose the metrics endpoint in `application.properties`, then add the
scrape config:

```yaml
scrape_configs:
  - job_name: 'spring-boot'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['your_spring_boot_host:your_spring_boot_port']
```

Grafana:

```bash
sudo nano /etc/grafana/grafana.ini
sudo iptables -A INPUT -p tcp --dport 3000 -j ACCEPT
```

Then disable the firewall / add your public IP to the IPv4 rules in AWS Lightsail
so you can connect.

> **Metrics worth watching:** **consumer lag** (how far behind a group is — the
> single most important Kafka metric), messages in/out per second, under-replicated
> partitions, request latency, and disk usage per broker.

---

## 9. Quick revision sheet

**Sync vs async**
- Sync = wait for response, keeps order, caller coupled to callee.
- Async = publish and move on, publisher doesn't care who consumes.

**Why async** → decoupling · independent scaling · fault isolation (no single
point of failure) · buffering spikes · fan-out · replay.

**Pub-Sub** → broadcast to many, publisher doesn't know consumers. *ex: Kafka*
**Producer-Consumer** → each message to exactly one consumer, work distribution.
*ex: RabbitMQ*

**RabbitMQ** → Producer · Exchange (Direct / Fanout / Topic / Headers) · Binding ·
Routing key · Queue · Consumer · AMQP.

**Kafka** →
- Two layers: **compute** (producer, consumer, streams, connectors) + **storage**.
- **Topic** = append-only log on disk, read via **offset**, nothing temporary.
- **Partition** = topic split across nodes; keyed messages hash to a fixed
  partition (ordering), unkeyed go round-robin (even spread).
- **Broker** = mini server with partitions; find one → find the whole cluster.
- **Replication** = leader handles reads/writes, followers stay in sync and take
  over on failure.
- **Consumer group** = one partition → exactly one consumer *inside* a group;
  many groups read the same partitions independently, each with its own purpose
  and offsets.
- **Ecosystem**: Connect (JSON-config integrations) · Schema Registry (validate
  message shape) · Streams (KStream/KTable, joins & aggregations built in).

**Choosing** → job to be done = RabbitMQ · fact to be broadcast/replayed = Kafka.

---

## 10. Where this fits in ExpenseTracker

Right now the service is fully **synchronous**: a request comes in, the filter
chain authenticates it ([Chapter 4](./chapter-4-security-internals.md)), the
controller calls a service, the service hits Postgres, and the response goes
back. Nothing in the codebase publishes an event yet. That is the right starting
point — but there are already places where the sync path is doing work the caller
doesn't need an answer to.

### 10.1 The `// pushEventToQueue` comment

`services\UserDetailsServiceImpl.java:62` already marks the spot:

```java
public Boolean signupUser(UserInfoDto userInfoDto) {
    userInfoDto.setPassword(passwordEncoder.encode(userInfoDto.getPassword()));
    if (Objects.nonNull(checkIfUserAlreadyExist(userInfoDto))) {
        return false;
    }
    String userId = UUID.randomUUID().toString();
    userRepository.save(new UserInfo(userId, userInfoDto.getUsername(), userInfoDto.getPassword(), new HashSet<>()));
    // pushEventToQueue          ← this is the async boundary
    return true;
}
```

This is exactly the §1 split. The caller needs one thing synchronously: *"did the
signup succeed?"* Everything downstream of that — welcome email, analytics,
provisioning a default expense category, a CRM record — is a **fact to broadcast**,
not a job the HTTP request should wait on. Publish a `user.created` event and
return; each consumer handles its own concern, and adding a fourth consumer later
requires **no change to this method** (§3.1, fan-out).

Key choice, per §5.4: use **`userId` as the message key** so all events for one
user land in the same partition and stay ordered.

### 10.2 Other candidates in this codebase

| Where | Event | Why async |
|---|---|---|
| `UserDetailsServiceImpl.signupUser` | `user.created` | welcome email, analytics, default categories — nobody is waiting |
| `TokenController.AuthenticateAndGetToken` | `user.login` | audit trail, "new device" detection, security analytics |
| Expense create/update (when built) | `expense.recorded` | monthly aggregates, budget-threshold alerts, report generation |
| `RefreshTokenService` cleanup | — | expired-token purge is a **scheduled job**, not an event; don't reach for a broker here |

### 10.3 Which broker for which case

Per §7's rule of thumb — *job to be done = RabbitMQ, fact to broadcast = Kafka*:

- **`user.created` / `expense.recorded`** → **Kafka**. Multiple independent
  consumers, replayable, and a future analytics service can read history from
  offset 0.
- **"send this one email"** → **RabbitMQ**. It's a single job, needs retries and a
  dead-letter queue, and exactly one worker should do it.

### 10.4 The trap to avoid

Async **is not free**. Once signup publishes an event:

- The response says "success" before the welcome email exists → **eventual
  consistency**, and the UI must not promise otherwise.
- The DB write and the publish are two separate operations. Crash between them
  and the user exists with no event. The standard fix is the **transactional
  outbox pattern**: write the event to an `outbox` table in the *same* DB
  transaction as the user row, and have a separate relay publish it to Kafka.
- Consumers must be **idempotent** — Kafka gives at-least-once delivery by
  default, so the same `user.created` can arrive twice. Dedupe on the event ID.

Do not add a broker to this project until there is a second service to talk to.
A message broker between a controller and a service in the same JVM is pure
overhead — for that, Spring's `@Async` or `ApplicationEventPublisher` is the
right tool.

---

## References

- RabbitMQ features & architecture — <https://www.linkedin.com/pulse/rabbitmq-features-architecture-huzaifa-asif/>
- What is Apache Kafka (Confluent) — <https://developer.confluent.io/what-is-apache-kafka/>
- AWS Lightsail VPS setup — <https://www.youtube.com/watch?v=ENR-J6c9Xoo>
- Install Kafka on Ubuntu 22.04 — <https://vegastack.com/tutorials/how-to-install-apache-kafka-on-ubuntu-22-04/>
- Install Prometheus & Grafana on Ubuntu 22.04 — <https://www.fosstechnix.com/install-prometheus-and-grafana-on-ubuntu-22-04/>

**Related notes:**
[Chapter 2 — Auth Service design](./chapter-2-auth-service.md) ·
[Chapter 4 — Spring Security internals](./chapter-4-security-internals.md) ·
[`learning\7-Networking-And-Threading`](../../learning/7-Networking-And-Threading.md) ·
[`learning\12-SQL-vs-NoSQL-CAP-ACID-VPS-VMs`](../../learning/12-SQL-vs-NoSQL-CAP-ACID-VPS-VMs.md)
