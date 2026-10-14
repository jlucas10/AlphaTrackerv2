from app.llm import LLMTurn, Message, ToolSpec


class FakeSpring:
    """Stands in for SpringClient: records calls, returns or raises what it's told."""

    def __init__(self, response=None, error=None):
        self.response, self.error, self.calls = response, error, []

    def get(self, path, token, params=None):
        self.calls.append((path, token, params))
        if self.error:
            raise self.error
        return self.response


class FakeLLM:
    """A scripted stand-in for the model: returns the given turns in order and
    records every request, so tests of the agent loop are deterministic, free,
    and never touch the network."""

    def __init__(self, turns: list[LLMTurn]):
        self._turns = list(turns)
        self.calls: list[dict] = []

    def generate(self, system: str, messages: list[Message], tools: list[ToolSpec]) -> LLMTurn:
        # Snapshot the messages: the loop keeps appending to the same list.
        self.calls.append({"system": system, "messages": list(messages), "tools": tools})
        if not self._turns:
            raise AssertionError("FakeLLM was called more times than it has scripted turns")
        return self._turns.pop(0)
