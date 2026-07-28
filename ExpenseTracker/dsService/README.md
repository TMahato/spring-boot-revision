# dsService

Takes a raw SMS, decides whether it is a bank transaction alert, extracts the
structured expense from it with an LLM, and publishes that onto Kafka for
`expenseService` to persist.

```
  client ──► Kong :8000 ──► dsService :8010
                                 │
                       1. MessagesUtil.isBankSms()   regex pre-filter
                       2. LLMService.runLLM()        Mistral, structured output
                       3. producer.send()            key = user_id
                                 │
                                 ▼
                       [ expense-topic ] ──► expenseService (not built yet)
```

The regex runs before the LLM on purpose: every message past it costs an API
call, so cheap rejection first.

## API

### `POST /v1/ds/message`

| | |
|---|---|
| Header | `x-user-id: <uuid>` — **required**, 400 without it |
| Body | `{"message": "Rs 450 spent on your HDFC card at STARBUCKS"}` |

**200** — extracted, and published to Kafka:

```json
{"amount": "450", "merchant": "STARBUCKS", "currency": "INR", "user_id": "…"}
```

**400** — missing `x-user-id`, malformed body, not a bank SMS, or extraction
failed.
**503** — extraction succeeded but Kafka would not accept the record.

### `GET /health`

Liveness only; does not touch Kafka. Used by the compose healthcheck.

## The Kafka contract

**This is the part `expenseService` has to match exactly.** Chapter 6 is about
why: a JSON contract has no schema registry behind it, so a mismatch produces
nulls rather than an error.

| | |
|---|---|
| Topic | `expense-topic` (override with `KAFKA_TOPIC`) |
| Key | `user_id`, UTF-8 string |
| Value | UTF-8 JSON, snake_case |

```json
{
  "amount":   "450",
  "merchant": "STARBUCKS",
  "currency": "INR",
  "user_id":  "3f1c…"
}
```

Notes for whoever writes the consumer:

- **All four fields are nullable except `user_id`.** The LLM is instructed to
  return null for anything it can't determine, so `amount` and `merchant` can
  legitimately arrive as `null`. Don't map them to primitives.
- **`amount` is a string, not a number.** It comes out of the model as text.
  Parse and validate it on the consumer side; don't assume it's well-formed.
- The key is the `user_id`, so all of one user's expenses land in the same
  partition and are processed in order (notes/chapter-6 §7.4 — the auth service
  producer gets this wrong, this one doesn't).
- `user_id` is the same id the auth service mints and puts in `user-info-topic`,
  so `expenseService` can join against the user service's records.
- Set `FAIL_ON_UNKNOWN_PROPERTIES=false` / `@JsonIgnoreProperties(ignoreUnknown
  = true)` on the consumer DTO so this service can add fields without breaking
  it (notes/chapter-6 §4.2).

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `MISTRAL_API_KEY` | — | **Required.** Service refuses to start without it. |
| `MISTRAL_MODEL` | `mistral-large-latest` | Extraction model |
| `KAFKA_HOST` | `localhost` | `kafka` under compose |
| `KAFKA_PORT` | `9092` | |
| `KAFKA_TOPIC` | `expense-topic` | Must match the consumer |
| `KAFKA_ACKS` | `all` | |
| `KAFKA_RETRIES` | `3` | |
| `SERVICE_PORT` | `8010` | Only used when run directly |

## Running

Through compose, with the rest of the stack:

```bash
docker compose up --build dsservice
```

Directly, for development:

```bash
cd dsService
pip install -r requirements.txt
PYTHONPATH=src MISTRAL_API_KEY=... python -m app
```

Through the gateway:

```bash
curl -X POST http://localhost:8000/v1/ds/message \
     -H 'Content-Type: application/json' \
     -H 'x-user-id: 3f1c-...' \
     -d '{"message":"Rs 450 spent on your HDFC card at STARBUCKS"}'
```

## Differences from the reference implementation

Ported from `D:\POC\dsService` with these changes:

| Change | Why |
|---|---|
| `OPENAI_API_KEY` → `MISTRAL_API_KEY` | The key was passed to `ChatMistralAI`. The old name only worked if you stored a Mistral key under the OpenAI variable. |
| Missing key raises at startup | Was an opaque 401 from the provider on the first request. |
| `request.json` → `get_json(silent=True)` + validation | A body without `message` raised, returning 500 instead of 400. |
| `producer.send()` → `.get(timeout=10)` | `send()` is async; failures were silently dropped and the caller still got a 200. |
| Kafka key set to `user_id` | Was unkeyed, so one user's expenses could be processed out of order. |
| `atexit` flush | Buffered records were lost on shutdown. |
| Topic name from config | Was hardcoded `expense_service`. Renamed to `expense-topic` to match the project's existing `user-info-topic`. |
| `flask run` → gunicorn | The Flask dev server is single-threaded and not for production. |
| Install from `requirements.txt`, not a `dist/` tarball | The reference Dockerfile copied `dist/ds-service-1.0.tar.gz`, but `dist/` is gitignored — the build fails on a fresh clone unless you remember to `python setup.py sdist` first. |
| requirements trimmed 100+ → 8 | The original was a `pip freeze` including jupyter, ipython, numpy, SQLAlchemy. |
| Non-root user, `PYTHONUNBUFFERED` | Consistent with the Java services' Dockerfiles. |
| `re.compile` moved to `__init__` | Was recompiling the pattern on every message. |
| Added `debited`, `credited`, `txn` | The original three words miss most Indian bank SMS formats. |
