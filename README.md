# Java & Spring Boot Revision

Notes and hands-on projects from a Java / Spring Boot deep-dive — written while
learning, kept as a revision reference.

## Layout

| Path | What's in it |
|---|---|
| [`learning/`](./learning) | Concept notes — Java language, collections, generics, threading, Spring beans & DI, proxies, AOP, JWT auth, SQL vs NoSQL |
| [`projects/`](./projects) | Small runnable projects, one per concept (see [`projects/STEPS.md`](./projects/STEPS.md)) |
| [`ExpenseTracker/`](./ExpenseTracker) | The main project — a production-shaped Spring Boot expense tracker, built from scratch |
| [`1-Java-FullStack-DeepDive.md`](./1-Java-FullStack-DeepDive.md) | Overall roadmap |
| [`2-Java-FullStack-Course-Study-Guide.md`](./2-Java-FullStack-Course-Study-Guide.md) | Course study guide |

## Concept notes

1. Classes, abstract classes & interfaces
2. Collections — Map, Set, Optional
3. Polymorphism
4. Reference type vs. object type
5. Optionals, streams, wrappers — and generics / type erasure
6. Learnings from the ticket-booking project
7. Networking & threading
8. Spring beans & dependency injection
9. Proxies in Spring Boot
10. AOP — aspect-oriented programming
11. Auth & JWT tokens
12. SQL vs NoSQL, CAP, ACID, VPS & VMs

## Projects

| # | Project | Focus |
|---|---|---|
| 1 | `1-TicketBooking` | Gradle, OOP modelling, JSON persistence |
| 2 | `2-SingleThreaded` | Sockets — single-threaded server |
| 3 | `3-MultiThreaded` | Thread-per-connection server |
| 4 | `4-ThreadPool` | `ExecutorService` / thread pools |
| 5 | `5-Proxy` | JDK dynamic proxies, custom `@Cacheable` |
| 6 | `6-SpringBootAOP` | Spring AOP aspects, pointcuts, advice |

## ExpenseTracker

The main build. Spring Boot 3.3 + Maven + Java 21, Spring Security with JWT
access/refresh tokens, JPA/Postgres.

Design and implementation notes live in [`ExpenseTracker/notes/`](./ExpenseTracker/notes):

- **Chapter 1** — about the app
- **Chapter 2** — auth service design (tokens, Spring Security flow, DB choice, ER model)
- **Chapter 3** — step-by-step implementation guide + concept reference

## Tech

Java 21 · Spring Boot 3.3 · Spring Security · Spring Data JPA · Maven · Gradle · JWT (jjwt) · PostgreSQL
