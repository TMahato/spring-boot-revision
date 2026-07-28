"""
dsService — SMS in, expense event out.

    POST /v1/ds/message  {"message": "..."}   +  x-user-id header
        -> regex pre-filter (is this a bank SMS?)
        -> LLM extraction (amount / merchant / currency)
        -> publish to the expense topic
        -> expenseService consumes and persists

This is the second producer in the system. The first (authService's
UserInfoProducer) publishes user events; the mechanics and the failure modes are
the same, and notes/chapter-6 covers them in depth.
"""
import atexit
import json
import logging
import uuid
from datetime import datetime, timezone

from flask import Flask, jsonify, request
from kafka import KafkaProducer
from kafka.errors import KafkaError

from .service.messageService import MessageService

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)-5s [%(name)s] %(message)s",
)
log = logging.getLogger(__name__)

app = Flask(__name__)
app.config.from_pyfile("config.py")

messageService = MessageService()

log.info("Kafka bootstrap servers: %s", app.config["KAFKA_BOOTSTRAP_SERVERS"])
log.info("Publishing to topic: %s", app.config["KAFKA_TOPIC"])

producer = KafkaProducer(
    bootstrap_servers=app.config["KAFKA_BOOTSTRAP_SERVERS"],
    # String key, JSON value — the same shape as authService's producer, so both
    # sides of this system serialize identically (notes/chapter-6 §2).
    key_serializer=lambda k: k.encode("utf-8") if k is not None else None,
    value_serializer=lambda v: json.dumps(v).encode("utf-8"),
    # acks="all": wait for all in-sync replicas. Safest against broker failure.
    acks=app.config["KAFKA_ACKS"],
    retries=app.config["KAFKA_RETRIES"],
)

# Without this, buffered records are lost when the process exits — send() only
# queues, it does not transmit.
atexit.register(lambda: producer.flush(timeout=10))


@app.route("/v1/ds/message", methods=["POST"])
def handle_message():
    user_id = request.headers.get("x-user-id")
    if not user_id:
        return jsonify({"error": "x-user-id header is required"}), 400

    # silent=True: a malformed or missing JSON body should be a 400, not the 500
    # that request.json raises.
    body = request.get_json(silent=True) or {}
    message = body.get("message")
    if not message or not isinstance(message, str):
        return jsonify({"error": "body must be JSON with a non-empty 'message' string"}), 400

    result = messageService.process_message(message)
    if result is None:
        return jsonify({"error": "Invalid message format"}), 400

    payload = result.serialize()
    payload["user_id"] = user_id
    # event_id is what makes the consumer idempotent. Kafka is at-least-once —
    # our own retries, or a consumer rebalance between processing and offset
    # commit, can deliver the same event twice. expenseService stores this under
    # a UNIQUE constraint and skips anything it has already seen
    # (notes/chapter-6 §4.4). Without it a redelivery is a duplicate expense.
    payload["event_id"] = str(uuid.uuid4())
    # Timezone-aware ISO-8601, which is what the Java consumer's JavaTimeModule
    # expects. A naive timestamp would be read as UTC regardless of where this ran.
    payload["created_at"] = datetime.now(timezone.utc).isoformat()

    try:
        # KEY = user_id. Kafka only guarantees ordering WITHIN a partition, and
        # the partition is chosen by key — so keying on the user means one
        # user's expenses are always processed in the order they were sent.
        # (authService's producer omits this; notes/chapter-6 §7.4.)
        #
        # .get() blocks until the broker acks, converting a silent async failure
        # into an exception we can actually return to the caller.
        producer.send(app.config["KAFKA_TOPIC"], key=user_id, value=payload).get(timeout=10)
    except KafkaError:
        log.exception("Failed to publish expense event for user_id=%s", user_id)
        return jsonify({"error": "Failed to publish expense event"}), 503

    log.info("Published expense event for user_id=%s", user_id)
    return jsonify(payload)


@app.route("/", methods=["GET"])
def handle_get():
    return "Hello world"


@app.route("/health", methods=["GET"])
def health_check():
    """Liveness only — deliberately does NOT touch Kafka.

    The compose healthcheck polls this, and Kafka connectivity is already gated
    by `depends_on: condition: service_healthy` on the broker.
    """
    return "OK"


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=app.config["SERVICE_PORT"], debug=True)
