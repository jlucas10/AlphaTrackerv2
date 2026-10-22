import time

from app.llm import LLM, LLMTurn, Message, ToolSpec


class MeteredLLM:
    """Wraps the real model to count calls, tokens and time - from OUTSIDE the agent.

    The agent knows nothing about this. Measuring by wrapping the model keeps eval-only
    counters out of production code, and it measures what actually crossed the wire
    (including the extra round trip a corrected answer costs) rather than what the
    agent believes it did.
    """

    def __init__(self, inner: LLM):
        self._inner = inner
        self.reset()

    def reset(self) -> None:
        self.calls = 0
        self.prompt_tokens = 0
        self.output_tokens = 0
        self.model_seconds = 0.0

    def generate(self, system: str, messages: list[Message], tools: list[ToolSpec]) -> LLMTurn:
        started = time.perf_counter()
        try:
            turn = self._inner.generate(system, messages, tools)
        finally:
            self.calls += 1
            self.model_seconds += time.perf_counter() - started
        if turn.usage:
            self.prompt_tokens += turn.usage.prompt_tokens
            self.output_tokens += turn.usage.output_tokens
        return turn
