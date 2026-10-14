import json
from dataclasses import dataclass, field

from app.llm import LLM, Message, ToolResultMsg, ToolSpec
from app.tools import TOOLS, ToolRunner

MAX_ITERATIONS = 6


class AgentError(Exception):
    """The loop could not produce an answer (the model returned nothing usable)."""


@dataclass(frozen=True)
class ToolCallRecord:
    name: str
    args: dict
    # "ok", "error" (rejected by validation or by Spring - the model was told),
    # or "repeat" (an identical call it had already made, not executed again).
    status: str


@dataclass
class AgentResult:
    answer: str
    tool_calls: list[ToolCallRecord] = field(default_factory=list)


class Agent:
    """The hand-written tool loop.

        user message -> model -> (tool calls -> run them -> results back -> model)* -> answer

    Written out explicitly, rather than left to the SDK, so every step is visible:
    each tool call is validated and run through ToolRunner, the loop has a hard cap,
    and a dead JWT stops everything immediately. Failures the model can act on come
    back to it as tool results; failures it can't (SpringAuthError, SpringUnavailable,
    LLMError) propagate to the endpoint, which decides the HTTP response.
    """

    def __init__(self, llm: LLM, runner: ToolRunner, max_iterations: int = MAX_ITERATIONS):
        self._llm = llm
        self._runner = runner
        self._max_iterations = max_iterations
        self._specs = [ToolSpec(t.name, t.description, t.parameters_schema) for t in TOOLS.values()]

    def run(self, *, system: str, history: list[Message], user_message: str, token: str) -> AgentResult:
        messages = [*history, Message("user", text=user_message)]
        trace: list[ToolCallRecord] = []
        seen: set[tuple[str, str]] = set()

        for _ in range(self._max_iterations):
            turn = self._llm.generate(system, messages, self._specs)
            if not turn.tool_calls:
                return AgentResult(_require_text(turn.text), trace)

            # Keep the model's own turn (with its raw provider content) in the transcript.
            messages.append(Message("assistant", text=turn.text, tool_calls=turn.tool_calls, raw=turn.raw))

            results: list[ToolResultMsg] = []
            for call in turn.tool_calls:
                fingerprint = (call.name, json.dumps(call.args, sort_keys=True, default=str))
                if fingerprint in seen:
                    # A model stuck repeating itself would otherwise burn every iteration.
                    payload = {"error": "You already made this exact call; its result is earlier in the conversation. "
                                        "Use it, or answer now."}
                    status = "repeat"
                else:
                    seen.add(fingerprint)
                    result = self._runner.run(call.name, call.args, token)  # may raise: auth / outage
                    payload, status = result.payload, "ok" if result.ok else "error"
                trace.append(ToolCallRecord(call.name, call.args, status))
                results.append(ToolResultMsg(call.name, payload, call.id))
            messages.append(Message("tool", tool_results=results))

        # Out of iterations. One last call with NO tools available, so the model
        # must answer from what it already has (and say what is missing) rather
        # than the request failing outright.
        wrap_up = system + "\n\nYou have used all your tool calls. Answer now using only what you already have, " \
                           "and say clearly what you could not find out."
        final = self._llm.generate(wrap_up, messages, [])
        return AgentResult(_require_text(final.text), trace)


def _require_text(text: str | None) -> str:
    if not text or not text.strip():
        raise AgentError("The model returned an empty answer.")
    return text.strip()
