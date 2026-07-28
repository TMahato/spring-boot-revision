"""
The extraction step: unstructured SMS text -> a populated Expense.
"""
import logging
import os

from langchain_core.prompts import ChatPromptTemplate
from langchain_mistralai import ChatMistralAI

from app.service.expense import Expense

log = logging.getLogger(__name__)


class LLMService:
    def __init__(self, api_key=None, model=None):
        # NOTE: the reference read OPENAI_API_KEY here and passed it to
        # ChatMistralAI. Corrected to MISTRAL_API_KEY — the old name meant the
        # service only worked if you happened to store a Mistral key under the
        # OpenAI variable.
        self.apiKey = api_key or os.getenv("MISTRAL_API_KEY")
        self.model = model or os.getenv("MISTRAL_MODEL", "mistral-large-latest")

        if not self.apiKey:
            # Fail at construction with a clear message rather than at the first
            # request with an opaque 401 from the provider.
            raise RuntimeError(
                "MISTRAL_API_KEY is not set. Put it in ExpenseTracker/.env "
                "(see .env.example) or export it before running directly."
            )

        self.prompt = ChatPromptTemplate.from_messages(
            [
                (
                    "system",
                    "You are an expert extraction algorithm. "
                    "Only extract relevant information from the text. "
                    "If you do not know the value of an attribute asked to extract, "
                    "return null for the attribute's value.",
                ),
                ("human", "{text}"),
            ]
        )

        # temperature=0: extraction must be deterministic. The same SMS has to
        # produce the same amount every time.
        self.llm = ChatMistralAI(
            api_key=self.apiKey,
            model=self.model,
            temperature=0,
        )
        # with_structured_output binds Expense as a tool schema, so the model
        # returns a populated Expense rather than prose we'd have to parse.
        self.runnable = self.prompt | self.llm.with_structured_output(schema=Expense)

    def runLLM(self, message):
        """Returns an Expense, or None if the call failed.

        Returning None rather than raising keeps a provider outage from becoming
        a 500 — the caller turns it into a 4xx/503 with a useful body.
        """
        try:
            return self.runnable.invoke({"text": message})
        except Exception:
            # Never log `message`: bank SMS text contains account fragments.
            log.exception("LLM extraction failed")
            return None
