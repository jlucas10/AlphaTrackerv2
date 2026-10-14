import json
import logging
from dataclasses import dataclass, field

from app.grounding import KnownNumbers
from app.llm import LLM, Message, ToolResultMsg, ToolSpec
from app.tools import TOOLS, ToolRunner

logger = logging.getLogger("assistant.agent")

MAX_ITERATIONS = 6
# How many times an answer with unsupported figures is sent back to the model
# to be fixed before we stop trusting it.
MAX_CORRECTIONS = 1

UNVERIFIED_ANSWER = (
    "I couldn't verify some of the figures in my answer against your data, so I'm not going to show them. "
    "Try asking for a specific metric, period or account."
)


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
    # Figures the model quoted that no tool returned, when the answer was withheld.
    unverified: list[float] = field(default_factory=list)


class Agent:
    """The hand-written tool loop.

        user message -> model -> (tool calls -> run them -> results back -> model)* -> answer

    Written out explicitly, rather than left to the SDK, so every step is visible:
    each tool call is validated and run through ToolRunner, the loop has a hard cap,
    and a dead JWT stops everything immediately. Failures the model can act on come
    back to it as tool results; failures it can't (SpringAuthError, SpringUnavailable,
    LLMError) propagate to the endpoint, which decides the HTTP response.

    The final answer is also checked (grounding.py): a figure that appeared in no
    tool result is sent back once for correction, and withheld if it persists.
    """

    def __init__(self, llm: LLM, runner: ToolRunner, max_iterations: int = MAX_ITERATIONS):
        self._llm = llm
        self._runner = runner
        self._max_iterations = max_iterations
        self._specs = [ToolSpec(t.name, t.description, t.parameters_schema) for t in TOOLS.values()]

    def run(
        self,
        *,
        system: str,
        history: list[Message],
        user_message: str,
        token: str,
        context_text: str = "",
    ) -> AgentResult:
        messages = [*history, Message("user", text=user_message)]
        trace: list[ToolCallRecord] = []
        seen: set[tuple[str, str]] = set()
        corrections = 0

        # Everything the model may legitimately quote: the conversation so far,
        # today's date / account names (context_text), and - as they arrive - tool results.
        known = KnownNumbers()
        known.add_text(context_text)
        known.add_text(user_message)
        for earlier in history:
            known.add_text(earlier.text or "")

        for _ in range(self._max_iterations):
            turn = self._llm.generate(system, messages, self._specs)

            if not turn.tool_calls:
                text = _require_text(turn.text)
                unknown = known.unknown_in(text)
                if not unknown:
                    return AgentResult(text, trace)
                if corrections < MAX_CORRECTIONS:
                    corrections += 1
                    logger.warning("answer had figures no tool returned; asking the model to fix it")
                    messages.append(Message("assistant", text=turn.text, raw=turn.raw))
                    messages.append(Message("user", text=_correction(unknown)))
                    continue
                return AgentResult(UNVERIFIED_ANSWER, trace, unknown)

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
                    known.add_payload(payload)
                trace.append(ToolCallRecord(call.name, call.args, status))
                results.append(ToolResultMsg(call.name, payload, call.id))
            messages.append(Message("tool", tool_results=results))

        # Out of iterations. One last call with NO tools available, so the model
        # must answer from what it already has (and say what is missing) rather
        # than the request failing outright.
        wrap_up = system + "\n\nYou have used all your tool calls. Answer now using only what you already have, " \
                           "and say clearly what you could not find out."
        final = self._llm.generate(wrap_up, messages, [])
        text = _require_text(final.text)
        unknown = known.unknown_in(text)
        return AgentResult(text, trace) if not unknown else AgentResult(UNVERIFIED_ANSWER, trace, unknown)


def _correction(unknown: list[float]) -> str:
    figures = ", ".join(f"{n:g}" for n in unknown)
    return (
        f"Your answer contains figures that no tool returned: {figures}. Do not calculate, estimate or "
        "remember figures. Call the right tool now (a follow-up still needs its own tool call), or leave "
        "those figures out, then answer again using only figures from tool results."
    )


def _require_text(text: str | None) -> str:
    if not text or not text.strip():
        raise AgentError("The model returned an empty answer.")
    return text.strip()
