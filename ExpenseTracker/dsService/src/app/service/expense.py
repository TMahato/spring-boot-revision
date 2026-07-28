"""
The extraction schema AND the Kafka event contract.

This class does double duty: LangChain uses the field descriptions to tell the
LLM what to pull out of the SMS, and ``serialize()`` produces the exact JSON that
lands on the topic.

Field names here are what expenseService will deserialize. snake_case, matching
the convention the auth/user services already use (notes/chapter-6 §5.1). Change
a name here and the Java consumer reads null for that field WITHOUT any error —
a JSON contract fails silently (notes/chapter-6 §8).
"""
from typing import Optional

from langchain_core.pydantic_v1 import BaseModel, Field


class Expense(BaseModel):
    """Information about a transaction made on any Card"""

    amount: Optional[str] = Field(
        title="expense",
        description="Expense made on the transaction",
    )
    merchant: Optional[str] = Field(
        title="merchant",
        description="Merchant name whom the transaction has been made with",
    )
    currency: Optional[str] = Field(
        title="currency",
        description="currency of the transaction",
    )

    def serialize(self):
        """The event payload. user_id is added by the caller, not here."""
        return {
            "amount": self.amount,
            "merchant": self.merchant,
            "currency": self.currency,
        }
