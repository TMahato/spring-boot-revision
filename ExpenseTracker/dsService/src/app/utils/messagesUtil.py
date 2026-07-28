import re


class MessagesUtil:
    """
    Cheap pre-filter: is this SMS plausibly a bank transaction alert?

    This runs BEFORE the LLM on purpose. Every message that gets past it costs an
    API call and a second or two of latency, so rejecting the obvious non-matches
    with a regex is what keeps the service from burning tokens on "happy birthday".
    """

    WORDS_TO_SEARCH = ["spent", "bank", "card", "debited", "credited", "txn"]

    def __init__(self):
        # Compiled once at construction rather than on every call.
        self._pattern = re.compile(
            r"\b(?:" + "|".join(re.escape(w) for w in self.WORDS_TO_SEARCH) + r")\b",
            flags=re.IGNORECASE,
        )

    def isBankSms(self, message):
        if not message:
            return False
        return bool(self._pattern.search(message))
