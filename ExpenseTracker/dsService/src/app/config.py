"""
Configuration, loaded by Flask via ``app.config.from_pyfile``.

Only UPPERCASE names are picked up by from_pyfile — lowercase helpers stay
private to this module.

Everything that differs between environments is read from the environment with a
local-development default, so one image runs everywhere (notes/chapter-7 §6).
"""
import os

# --- Kafka -------------------------------------------------------------------
# Service names under docker compose (`kafka`), localhost when run directly.
# Inside a container `localhost` is that container — see notes/chapter-7 §5.
KAFKA_HOST = os.getenv("KAFKA_HOST", "localhost")
KAFKA_PORT = os.getenv("KAFKA_PORT", "9092")
KAFKA_BOOTSTRAP_SERVERS = f"{KAFKA_HOST}:{KAFKA_PORT}"

# The topic expenseService will consume. Both sides must agree on this string;
# it is the entire contract between them (notes/chapter-6 §1).
KAFKA_TOPIC = os.getenv("KAFKA_TOPIC", "expense-topic")

# acks=all + retries mirrors authService's producer settings so both producers
# in this system have the same durability guarantees.
KAFKA_ACKS = os.getenv("KAFKA_ACKS", "all")
KAFKA_RETRIES = int(os.getenv("KAFKA_RETRIES", "3"))

# --- LLM ---------------------------------------------------------------------
# NOTE: the reference service read OPENAI_API_KEY and handed it to ChatMistralAI.
# That is a bug — this is a Mistral key. MISTRAL_API_KEY is the correct name.
MISTRAL_API_KEY = os.getenv("MISTRAL_API_KEY")
MISTRAL_MODEL = os.getenv("MISTRAL_MODEL", "mistral-large-latest")

# --- Server ------------------------------------------------------------------
SERVICE_PORT = int(os.getenv("SERVICE_PORT", "8010"))
