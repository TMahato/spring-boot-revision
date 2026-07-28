# =============================================================================
# dsService — SMS -> expense event. See notes/chapter-7 §3.
#
# Single-stage: unlike the Java services there is no compile step to discard,
# so a second stage would save nothing. The layer split below is what matters.
# =============================================================================
FROM python:3.11-slim

# curl for the compose healthcheck — slim images ship without it, and a
# healthcheck naming a missing binary reports unhealthy forever (§7.2).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# requirements.txt alone first: this layer stays cached until dependencies
# actually change, so editing Python doesn't re-download langchain (§2.2).
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY src ./src

# The app package lives under src/, so that is what must be importable —
# `from app.service...` resolves against it.
ENV PYTHONPATH=/app/src
# Don't buffer stdout, or logs only appear when the buffer fills and
# `docker compose logs` looks dead.
ENV PYTHONUNBUFFERED=1

# Don't run as root (§3.3).
RUN useradd -r -u 1001 -m -d /home/appuser appuser \
    && chown -R appuser:appuser /app
USER appuser

EXPOSE 8010

# gunicorn, not `flask run`: the Flask dev server is single-threaded and
# explicitly not for production use. `app:app` = the `app` object in the `app`
# package (src/app/__init__.py).
#
# Threads rather than extra workers: each worker builds its own KafkaProducer
# and LLMService, and this workload is I/O bound (waiting on Mistral, then on
# the broker), so threads are the cheaper way to get concurrency.
# --timeout 120 because LLM extraction can take a while.
CMD ["gunicorn", "--bind", "0.0.0.0:8010", \
     "--workers", "2", "--threads", "4", "--timeout", "120", \
     "--access-logfile", "-", "--error-logfile", "-", \
     "app:app"]
