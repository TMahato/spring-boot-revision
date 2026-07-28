# Chapter 6 — Serialization & Deserialization across the Kafka boundary

> **Scope:** How a `UserInfoDto` in the auth service becomes bytes on
> `user-info-topic`, and how those bytes become a `UserInfoDto` again in the user
> service. Covers the Kafka `Serializer`/`Deserializer` SPI, how Spring wires them
> in, the Jackson mechanics that decide the field names and which fields travel,
> and the failure modes on each side.
>
> Chapter 5 was the theory of brokers and topics. This chapter is the wire format
> — the one part of an event-driven system that two independently deployed
> services must agree on exactly.
>
> §7 documents what this codebase **actually** puts on the wire today, which is
> not what the DTOs suggest. Read it before trusting the happy path.

**Table of contents**

1. [Why serialization exists at all](#1-why-serialization-exists-at-all)
2. [The Kafka SPI: Serializer and Deserializer](#2-the-kafka-spi-serializer-and-deserializer)
3. [The producer side (authService)](#3-the-producer-side-authservice)
4. [The consumer side (userService)](#4-the-consumer-side-userservice)
5. [The Jackson layer — what decides the JSON](#5-the-jackson-layer--what-decides-the-json)
6. [End-to-end walkthrough of one signup](#6-end-to-end-walkthrough-of-one-signup)
7. [What actually goes on the wire today](#7-what-actually-goes-on-the-wire-today)
8. [Failure modes and where they surface](#8-failure-modes-and-where-they-surface)
9. [Alternatives to hand-written serializers](#9-alternatives-to-hand-written-serializers)
10. [Quick revision sheet](#10-quick-revision-sheet)

---

## 1. Why serialization exists at all

Kafka has no idea what a `UserInfoDto` is. A Kafka record is four things:

```
┌──────────────────────────────────────────────┐
│  key      : byte[]  (nullable)               │
│  value    : byte[]  (nullable)               │
│  headers  : list of (String, byte[])         │
│  metadata : topic, partition, offset, ts     │
└──────────────────────────────────────────────┘
```

The broker stores and replicates opaque bytes. It never parses them. That is
deliberate — it is what lets the broker stay fast and lets producers and
consumers be written in different languages and deployed on different schedules.

The price is that **the schema lives in your code, not in the broker**. Two
services agreeing on a Java class is not enough; they must agree on the *bytes*.
Everything in this chapter is about maintaining that agreement.

```
authService                     Kafka                      userService
  UserInfoDto                                                UserInfoDto
      │                                                          ▲
      │  serialize()                              deserialize()  │
      ▼                                                          │
   byte[]  ──────────────► [ user-info-topic ] ──────────────► byte[]
                             (stores bytes,
                              understands nothing)
```

Two separate `UserInfoDto` classes — `com.jassi.expensetracker.model.UserInfoDto`
and `com.jassi.userservice.entities.UserInfoDto` — in two separate Maven
projects. They share no code. **JSON is the only contract between them.**

---

## 2. The Kafka SPI: Serializer and Deserializer

The `kafka-clients` library defines two interfaces. Both live in
`org.apache.kafka.common.serialization`.

```java
public interface Serializer<T> {
    byte[] serialize(String topic, T data);
}

public interface Deserializer<T> {
    T deserialize(String topic, byte[] data);
}
```

That is the whole contract (the other methods — `configure`, `close`, and the
`Headers`-aware overloads — have defaults). Note the symmetry: `T -> byte[]` on
the way out, `byte[] -> T` on the way back.

Three rules that dictate how these classes must be written:

1. **Kafka instantiates them reflectively via a no-arg constructor.** You give
   Kafka a class *name* in a config property, not an instance. Which means:
2. **They are not Spring beans.** No `@Autowired`, no constructor injection. This
   is exactly why `UserInfoDeserializer` builds its own `ObjectMapper` instead of
   using the one exposed as a bean in `UserServiceConfig` — the comment on the
   class says so explicitly.
3. **They must be thread-safe.** One instance serves the whole producer/consumer.
   Hence `private static final ObjectMapper` in both classes: a Jackson
   `ObjectMapper` is thread-safe once configured, and expensive to construct, so
   one per JVM is the right call.

There are four serializers in play, because keys and values are serialized
independently:

| Side | Key | Value |
|---|---|---|
| Producer (authService) | `StringSerializer` | `UserInfoSerializer` |
| Consumer (userService) | `StringDeserializer` | `UserInfoDeserializer` |

---

## 3. The producer side (authService)

### 3.1 Configuration

`authService/src/main/resources/application.properties:24-28`

```properties
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=com.jassi.expensetracker.serializer.UserInfoSerializer
spring.kafka.producer.acks=all
spring.kafka.producer.retries=3
```

Boot reads these, builds a `DefaultKafkaProducerFactory`, and exposes a
`KafkaTemplate` bean. The `value-serializer` is a **fully-qualified class name as
a string** — there is no compile-time check that the class exists or that its
type parameter matches what you send. A typo here is a runtime failure at first
send, not a build failure.

`acks=all` and `retries=3` are durability settings, not serialization ones, but
they matter to this chapter for one reason: **retries can produce duplicates**,
which is why the consumer must be idempotent (§4.4).

### 3.2 `UserInfoProducer`

`authService/.../eventProducer/UserInfoProducer.java`

```java
@Service
public class UserInfoProducer {

    private final KafkaTemplate<String, UserInfoDto> kafkaTemplate;

    @Value("${app.kafka.topic.name}")
    private String TOPIC_NAME;

    public void sendEventToKafka(UserInfoDto userInfoDto) {
        Message<UserInfoDto> message = MessageBuilder
                .withPayload(userInfoDto)
                .setHeader(KafkaHeaders.TOPIC, TOPIC_NAME)
                .build();
        kafkaTemplate.send(message);
    }
}
```

Points worth understanding:

- **`KafkaTemplate<String, UserInfoDto>`** — the generics are the *application's*
  view. They tell you what you may pass to `send()`. They do **not** configure
  the serializer; erasure means Spring cannot infer a serializer from them. The
  serializer comes from the properties file, and nothing checks the two agree.
  Mismatch = `ClassCastException` inside the serializer at runtime.
- **The `Message` abstraction** — `MessageBuilder` builds a
  `spring-messaging` `Message`, with the topic supplied as the
  `KafkaHeaders.TOPIC` header rather than as a `send(topic, payload)` argument.
  Functionally equivalent; the `Message` form is what you want when you later need
  to attach more headers (a trace id, an event type, a schema version).
- **The topic name is externalized** as `app.kafka.topic.name`, deliberately not
  under `spring.kafka.*` — those are Boot's namespace, this is ours. Both
  services declare the same value; that string matching is a real coupling.
- **`send()` is asynchronous.** It returns a `CompletableFuture` that this code
  ignores. Serialization, however, happens **synchronously on the calling
  thread**, before the record is buffered — so a serialization failure surfaces
  as an exception out of `send()`, on the HTTP request thread, whereas a *broker*
  failure surfaces later in the ignored future and is silently lost.

### 3.3 `UserInfoSerializer`

`authService/.../serializer/UserInfoSerializer.java`

```java
public class UserInfoSerializer implements Serializer<UserInfoDto> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public byte[] serialize(String topic, UserInfoDto data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsBytes(data);
        } catch (Exception e) {
            throw new SerializationException(
                "Error serializing UserInfoDto for topic " + topic, e);
        }
    }
}
```

- **`null` in, `null` out.** A null value is a legal Kafka record — a
  **tombstone**, which on a compacted topic means "this key is deleted". Passing
  null to Jackson would produce the four bytes `null` instead, which is a
  completely different thing. The guard is correct and necessary.
- **`writeValueAsBytes`, not `writeValueAsString`.** Skips an intermediate
  `String` and its UTF-8 round trip. Jackson writes UTF-8 by default, which is
  what every JSON consumer expects.
- **Throwing `SerializationException`** — Kafka's own unchecked exception type,
  so it propagates cleanly out of `send()` rather than being wrapped.

---

## 4. The consumer side (userService)

### 4.1 Configuration

`userService/src/main/resources/application.properties:20-21`

```properties
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=com.jassi.userservice.deserializer.UserInfoDeserializer
```

Mirror image of the producer. Same caveat: class names as strings, no
compile-time verification, and no verification that this deserializer's output
type matches what `@KafkaListener` expects.

### 4.2 `UserInfoDeserializer`

`userService/.../deserializer/UserInfoDeserializer.java`

```java
public class UserInfoDeserializer implements Deserializer<UserInfoDto> {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public UserInfoDto deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;                       // tombstone — don't parse zero bytes
        }
        try {
            return objectMapper.readValue(data, UserInfoDto.class);
        } catch (Exception e) {
            throw new SerializationException(
                "Error deserializing UserInfoDto from topic " + topic, e);
        }
    }
}
```

Three decisions, each load-bearing:

**`FAIL_ON_UNKNOWN_PROPERTIES = false`.** Without this, the moment the auth
service adds a field to its event, every consumer instance starts throwing on
every record. This is the single most important setting for independent
deployability. The DTO also carries `@JsonIgnoreProperties(ignoreUnknown = true)`
— belt and braces, and the reason the consumer survives receiving `username` and
`user_roles`, which it has no fields for.

**Throwing instead of returning null on a parse error.** The class comment calls
this out as a deliberate deviation from the reference implementation, and the
reasoning is right: returning null hands the listener a silent null, the listener
completes normally, the offset commits, and a corrupt record is now
indistinguishable from a legitimate tombstone and gone forever. Throwing at least
makes it the error handler's problem.

**The null guard.** Same tombstone reasoning as the producer, in reverse.

> **Note the asymmetry:** the producer's `ObjectMapper` is a bare
> `new ObjectMapper()`; the consumer's is configured. That is fine here because
> the two directions need different things — but it is exactly the kind of drift
> that bites later. If you ever add a serialization feature (say
> `WRITE_DATES_AS_TIMESTAMPS`) on one side only, the contract quietly breaks.

### 4.3 `AuthServiceConsumer`

`userService/.../consumer/AuthServiceConsumer.java`

```java
@KafkaListener(topics = "${app.kafka.topic.name}",
               groupId = "${spring.kafka.consumer.group-id}")
public void listen(UserInfoDto eventData) {
    try {
        log.info("Consumed user event for userId={}", ...);
        userService.createOrUpdateUser(eventData);
    } catch (Exception ex) {
        log.error("Exception while consuming kafka event", ex);
    }
}
```

**Where deserialization happens matters.** The listener container polls Kafka on
a background thread; the deserializer runs on that thread, *before* this method
is entered. So:

- A `SerializationException` is thrown **outside** this method's `try` block. The
  `catch` here cannot see it. It goes to the container's error handler.
- By default, a deserialization failure means the container cannot advance past
  that record — it re-polls, re-deserializes, fails again. This is the classic
  **poison pill**: one bad record stalls the partition permanently. The
  standard fix is `ErrorHandlingDeserializer`, which wraps your deserializer,
  catches the exception, and passes a `null` payload plus a
  `DeserializationException` header downstream so a `DefaultErrorHandler` can
  route it to a dead-letter topic.

The comment in the `catch` is honest about the other half: swallowing a
*business* exception commits the offset and loses the event. Deliberate for now,
but the real answer is `DefaultErrorHandler` + retries + DLT.

### 4.4 Why the consumer must be idempotent

`UserService.createOrUpdateUser` upserts on `userId` rather than inserting:

```java
userRepository.findByUserId(userInfoDto.getUserId())
        .map(updateUser)
        .orElseGet(createUser);
```

This is not a nicety. Kafka is **at-least-once**: the producer's `retries=3` can
publish a duplicate, and a consumer rebalance between processing and offset
commit will redeliver. Saving by the same primary key twice is harmless; a blind
`INSERT` would violate the PK on the second delivery.

Which is also why `userService`'s `UserInfo` has `@Id` with **no**
`@GeneratedValue` — the id is the auth service's UUID, carried in the event. If
this service invented its own id, the upsert could never match and every
redelivery would create a duplicate row.

---

## 5. The Jackson layer — what decides the JSON

The `Serializer`/`Deserializer` classes are thin. All the interesting behaviour
is Jackson reading annotations off the DTOs.

### 5.1 Naming strategy

Both sides declare snake_case:

| | Annotation |
|---|---|
| authService `UserInfoDto` | `@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)` |
| userService `UserInfoDto` | `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)` |

Same behaviour, but note `PropertyNamingStrategy.SnakeCaseStrategy` (auth side)
is **deprecated** — Jackson 2.12 moved these to the plural
`PropertyNamingStrategies`. Worth aligning the auth service to the plural form
before a future Jackson upgrade removes it.

The strategy applies to **inherited properties too**, which is how `userId`
becomes `user_id` on the auth side even though that field lives on the parent
`UserInfo`.

The consumer *additionally* puts explicit `@JsonProperty("user_id")` on every
field. That is redundant with the naming strategy, but it is the more robust
choice: an explicit name cannot be broken by someone changing or removing the
strategy annotation. **Explicit names beat conventions on a wire contract.**

### 5.2 Visibility — the rule that decides everything

Jackson's default visibility for **serialization** is:

- public getters — **yes**
- public fields — **yes**
- private/package/protected fields with no getter — **no**

This is not a detail. It is the single rule that determines what leaves the auth
service, and it is where this codebase goes wrong (§7.1).

### 5.3 Keeping the password out

`authService/.../entities/UserInfo.java:29-30`

```java
@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
private String password;
```

`WRITE_ONLY` means *writable from JSON, never written to JSON* — Jackson reads it
from the signup request body but excludes it from output. Since the DTO extends
`UserInfo` and `signupUser` sets the BCrypt hash on that DTO before publishing,
without this annotation **the password hash would be broadcast onto the Kafka
topic** and persisted in the log for the full retention period.

JPA uses its own mapping, not Jackson, so persistence is unaffected. This
annotation is doing real security work — do not remove it.

---

## 6. End-to-end walkthrough of one signup

```
POST /auth/v1/signup   {"username":"jassi","password":"secret", ...}
   │
   ▼  Jackson deserializes request body → UserInfoDto      [authService]
AuthController.SignUp
   │
   ▼
UserDetailsServiceImpl.signupUser
   ├─ dto.setPassword(bcrypt(dto.getPassword()))
   ├─ generate userId = UUID.randomUUID()
   ├─ userRepository.save(new UserInfo(userId, username, hash, {}))   → MySQL
   └─ userInfoProducer.sendEventToKafka(dto)
        │
        ▼  MessageBuilder → Message<UserInfoDto>, header TOPIC=user-info-topic
      KafkaTemplate.send(message)
        │
        ▼  ON THE CALLING THREAD:
      StringSerializer.serialize(topic, null)          → key   = null
      UserInfoSerializer.serialize(topic, dto)         → value = byte[]
        │
        ▼  buffered, batched, sent to broker
   ═══════════════ [ user-info-topic ] ═══════════════
        │
        ▼  ON THE LISTENER CONTAINER THREAD:            [userService]
      StringDeserializer.deserialize(...)              → key
      UserInfoDeserializer.deserialize(topic, bytes)   → UserInfoDto
        │
        ▼
AuthServiceConsumer.listen(dto)
   └─ UserService.createOrUpdateUser(dto)
        └─ dto.transformToUserInfo() → userRepository.save()  → MySQL
        │
        ▼  method returns normally → offset committed
```

Two things to fix in your mental model from this diagram:

1. **Serialization is synchronous, sending is not.** The two failure classes
   surface in completely different places.
2. **Deserialization happens before your listener method.** Your listener's
   `try/catch` is powerless against it.

---

## 7. What actually goes on the wire today

The DTOs above describe an event carrying `user_id`, `first_name`, `last_name`,
`phone_number`, `email`, `profile_pic`. That is **not** what this code publishes.
Verified against the compiled classes in `authService/target/classes`.

### 7.1 The auth DTO has no getters, so four fields never serialize

```java
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@AllArgsConstructor
@NoArgsConstructor
@Builder                      // ← no @Data, no @Getter, no @Setter
public class UserInfoDto extends UserInfo {
    private String firstName;
    private String lastName;
    private Long phoneNumber;
    private String email;
}
```

`javap` on the compiled class confirms it — the only methods are `builder()`, the
two constructors, and nothing else:

```
public class com.jassi.expensetracker.model.UserInfoDto extends ...UserInfo {
  private java.lang.String firstName;
  private java.lang.String lastName;
  private java.lang.Long phoneNumber;
  private java.lang.String email;
  public static ...UserInfoDtoBuilder builder();
  public UserInfoDto(String, String, Long, String);
  public UserInfoDto();
}
```

Lombok's `@Data` on the **parent** `UserInfo` generates getters for the parent's
four fields only — Lombok never touches a subclass's fields. So by the visibility
rule in §5.2, Jackson sees **no accessible property** for `firstName`,
`lastName`, `phoneNumber` or `email`, and silently omits all four.

The same gap breaks the *input* side: with no setters and no
`@JsonCreator`-annotated constructor, Jackson cannot populate those four fields
from the signup request body either. They are dead fields end to end — always
null, never sent.

**Fix:** add `@Getter @Setter` (or `@Data`) to the auth service's `UserInfoDto`.

### 7.2 `user_id` is always null in the event

`UserDetailsServiceImpl.signupUser` generates the UUID and passes it to the
*entity*, but never sets it on the *DTO* that gets published:

```java
String userId = UUID.randomUUID().toString();
userRepository.save(new UserInfo(userId, userInfoDto.getUsername(), ...));
userInfoProducer.sendEventToKafka(userInfoDto);   // ← dto.userId still null
```

`userId` is inherited from `UserInfo`, so `setUserId` exists — it is just never
called. The client doesn't send `user_id` at signup, so it is null at
serialization time.

Downstream this is worse than a missing field: `createOrUpdateUser` calls
`findByUserId(null)`, misses, and tries to `save()` a `UserInfo` whose `@Id` is
null — and that id is `@NonNull`.

**Fix:** `userInfoDto.setUserId(userId);` before the `sendEventToKafka` call.

### 7.3 The actual payload

Putting §7.1 and §7.2 together, a signup today publishes:

```json
{"user_id":null,"username":"jassi","user_roles":[]}
```

The consumer parses that happily — `ignoreUnknown` discards `username` and
`user_roles`, and every mapped field is null. No exception anywhere. **A schema
mismatch on a JSON contract fails silently by design**, which is precisely why
this is worth a section rather than a footnote.

### 7.4 No message key is ever set

`application.properties:24-25` says:

```properties
# Producer: String key (use the entity id, so all events for one id keep their
# order within a partition), JSON value.
```

But `sendEventToKafka` sets only `KafkaHeaders.TOPIC` — never
`KafkaHeaders.KEY`. With a null key the partitioner distributes records across
partitions, so **two events for the same user can land in different partitions
and be processed out of order**. Kafka only guarantees ordering *within* a
partition. The comment describes an intent the code does not implement.

**Fix:** `.setHeader(KafkaHeaders.KEY, userInfoDto.getUserId())`. On a
multi-partition topic this is the difference between correct and
occasionally-corrupt state.

### 7.5 The DTO extends the persistence entity

`UserInfoDto extends UserInfo` couples the event contract to the JPA entity.
Every field added to the entity is automatically published to Kafka — which is
how `username` and `user_roles` ended up in the payload without anyone deciding
they should be. It is also one missing annotation away from leaking the password
hash (§5.3).

A published event is a **public API**. It deserves a standalone class with only
the fields you intend to publish, not a subclass of your storage model.

---

## 8. Failure modes and where they surface

| Failure | Where it is thrown | What you see |
|---|---|---|
| Serializer class name typo in properties | Producer factory init | Startup / first-send `ClassNotFoundException` |
| `KafkaTemplate` generics ≠ configured serializer | Producer, calling thread | `ClassCastException` inside `serialize` |
| Payload not JSON-serializable | Producer, calling thread | `SerializationException` out of `send()` |
| Broker unreachable / not acked | Async, in the ignored future | **Nothing** — silently dropped |
| Malformed bytes on the topic | Consumer container thread | `SerializationException`, **poison pill**, partition stalls |
| Unknown field in JSON | Consumer | Ignored (correct, by config) |
| Missing field in JSON | Consumer | Field is null, **no error** |
| Renamed field on one side only | Consumer | Field is null, **no error** |
| Business exception in listener | Inside `listen()` | Logged, offset committed, **event lost** |

The pattern: **structural** problems are loud, **semantic** problems are silent.
JSON will never tell you the producer renamed `email` to `email_address`. That is
the argument for a schema registry (§9.3).

---

## 9. Alternatives to hand-written serializers

### 9.1 Spring Kafka's built-in `JsonSerializer` / `JsonDeserializer`

`spring-kafka` ships both, so the two custom classes here are optional:

```properties
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.consumer.properties.spring.json.value.default.type=com.jassi.userservice.entities.UserInfoDto
spring.kafka.consumer.properties.spring.json.trusted.packages=com.jassi.userservice.entities
```

By default `JsonSerializer` adds a `__TypeId__` **header** with the producer's
fully-qualified class name, and `JsonDeserializer` uses it to pick a target
class. Across two services with different package names that header is a
liability — disable it with `spring.json.add.type.headers=false` and pin the type
explicitly, or you couple the consumer to the producer's package structure.

Writing them by hand, as this project does, is more code but entirely explicit
about which class is produced. Reasonable for learning; either is defensible in
production.

### 9.2 `ErrorHandlingDeserializer` — the poison-pill fix

Worth adding regardless of which serializer you use:

```properties
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
spring.kafka.consumer.properties.spring.deserializer.value.delegate.class=com.jassi.userservice.deserializer.UserInfoDeserializer
```

It delegates to your deserializer, catches the exception, and hands the container
a null payload plus the exception in a header — so a `DefaultErrorHandler` with a
`DeadLetterPublishingRecoverer` can park the bad record on
`user-info-topic.DLT` and let the partition move on. This is the concrete answer
to the stall described in §4.3.

### 9.3 Avro / Protobuf + Schema Registry

JSON's weakness is §8's last rows: no schema, so incompatible changes fail
silently. Avro with a Schema Registry (Chapter 5 §6.2) registers the schema
centrally and **rejects an incompatible producer at publish time** rather than
letting the consumer quietly read nulls. It also encodes far more compactly —
field names are not repeated in every record.

Cost: a registry to run, a build step, and less human-readable topics. For two
services and a learning project JSON is the right trade. For a dozen teams
publishing to shared topics, it is not.

---

## 10. Quick revision sheet

- Kafka stores **opaque bytes**. Key and value are serialized independently.
- `Serializer<T>`: `T -> byte[]`. `Deserializer<T>`: `byte[] -> T`. Configured by
  **class name string** in properties — no compile-time checking.
- Kafka builds them by **reflection, no-arg constructor** → not Spring beans, no
  injection, must be thread-safe → hence `static final ObjectMapper`.
- **Null is legal** and means tombstone. Guard it on both sides; never hand zero
  bytes to Jackson.
- **Serialization is synchronous** on the calling thread. **Sending is async** —
  an ignored `send()` future silently swallows broker failures.
- **Deserialization runs on the container thread, before your `@KafkaListener`.**
  Your listener's `try/catch` cannot catch it.
- A deserialization failure with a plain deserializer is a **poison pill** that
  stalls the partition. Fix with `ErrorHandlingDeserializer` + `DefaultErrorHandler`
  + DLT.
- `FAIL_ON_UNKNOWN_PROPERTIES=false` and `@JsonIgnoreProperties(ignoreUnknown=true)`
  are what let the producer add fields without breaking the consumer.
- Jackson serializes **public getters and public fields only**. No getter on a
  private field = the field silently never leaves. This is bug §7.1.
- `@JsonProperty(access = WRITE_ONLY)` on `password` is what keeps the BCrypt
  hash off the topic. Load-bearing.
- Prefer **explicit `@JsonProperty` names** over a naming strategy on a wire
  contract.
- Kafka is **at-least-once** → consumers must be idempotent. Upsert on the
  producer-supplied id; never `@GeneratedValue` on an id that arrives in an event.
- Ordering is **per partition**, and partition is chosen by **key**. No key = no
  ordering guarantee.
- Open defects in this codebase: no getters on the auth DTO (§7.1), `user_id`
  never set before publish (§7.2), no message key (§7.4), DTO extends the
  persistence entity (§7.5).

---

## References

- Chapter 5 — Async Communication, Message Brokers, RabbitMQ & Kafka (topics,
  partitions, consumer groups, Schema Registry)
- `authService/.../serializer/UserInfoSerializer.java`,
  `.../eventProducer/UserInfoProducer.java`, `.../model/UserInfoDto.java`
- `userService/.../deserializer/UserInfoDeserializer.java`,
  `.../consumer/AuthServiceConsumer.java`, `.../entities/UserInfoDto.java`
- Spring for Apache Kafka reference — Serialization, Deserialization, and Message
  Conversion
- Jackson `PropertyNamingStrategies`, `@JsonProperty`, `@JsonIgnoreProperties`
