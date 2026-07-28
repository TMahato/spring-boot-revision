"""
Orchestration: filter, then extract.
"""
from app.service.llmService import LLMService
from app.utils.messagesUtil import MessagesUtil


class MessageService:
    def __init__(self, llm_service=None, message_util=None):
        # Constructor injection (with defaults) so tests can pass a fake
        # LLMService instead of needing a real API key.
        self.messageUtil = message_util or MessagesUtil()
        self.llmService = llm_service or LLMService()

    def process_message(self, message):
        """Returns an Expense, or None when the SMS isn't a bank message
        or extraction failed."""
        if self.messageUtil.isBankSms(message):
            return self.llmService.runLLM(message)
        return None
