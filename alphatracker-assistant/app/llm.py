"""The only module that knows Gemini exists.

The agent loop talks to the small `LLM` interface below with plain dataclasses,
so it (and every test of it) never imports the SDK. Swapping providers, or
faking the model in tests, means implementing one method.
"""

from dataclasses import dataclass, field
from typing import Any, Literal, Protocol

from app.config import Settings


class LLMError(Exception):
    """The model call failed (rate limit, outage, blocked or empty answer)."""

    def __init__(self, message: str, *, retryable: bool = False):
        super().__init__(message)
        self.retryable = retryable


@dataclass(frozen=True)
class ToolSpec:
    name: str
    description: str
    parameters: dict[str, Any]  # JSON Schema


@dataclass(frozen=True)
class ToolCall:
    name: str
    args: dict[str, Any]
    id: str | None = None


@dataclass(frozen=True)
class ToolResultMsg:
    name: str
    payload: dict[str, Any]
    call_id: str | None = None


@dataclass
class Message:
    role: Literal["user", "assistant", "tool"]
    text: str | None = None
    tool_calls: list[ToolCall] = field(default_factory=list)  # assistant turns
    tool_results: list[ToolResultMsg] = field(default_factory=list)  # tool turns
    # Provider-native content for an assistant turn, replayed verbatim on the
    # next request. Gemini 3 attaches "thought signatures" to function calls and
    # expects them back; rebuilding the turn from text/tool_calls would drop them.
    raw: Any = None


@dataclass(frozen=True)
class LLMTurn:
    text: str | None
    tool_calls: list[ToolCall]
    raw: Any = None


class LLM(Protocol):
    def generate(self, system: str, messages: list[Message], tools: list[ToolSpec]) -> LLMTurn: ...


# Keys Gemini's function-calling schema does not use. `title` and `default` are
# noise Pydantic adds; `additionalProperties` is enforced by OUR validation
# (extra="forbid") rather than by the model's decoder.
_DROPPED_KEYS = {"title", "default", "additionalProperties"}


def simplify_schema(schema: Any) -> Any:
    """Pydantic -> a plain schema Gemini reads cleanly.

    Optional fields come out as anyOf:[X, {type: null}]. Collapse that to X (the
    field is simply not in `required`), so the model sees the enum/format
    directly instead of a union it may mishandle. Drops title/default/
    additionalProperties.
    """
    if isinstance(schema, list):
        return [simplify_schema(item) for item in schema]
    if not isinstance(schema, dict):
        return schema

    any_of = schema.get("anyOf")
    if any_of:
        branches = [b for b in any_of if b.get("type") != "null"]
        if len(branches) == 1:
            merged = {k: v for k, v in schema.items() if k != "anyOf"}
            merged.update(branches[0])
            return simplify_schema(merged)

    return {k: simplify_schema(v) for k, v in schema.items() if k not in _DROPPED_KEYS}


class GeminiLLM:
    def __init__(self, model: str, client: Any):
        self._model = model
        self._client = client

    def generate(self, system: str, messages: list[Message], tools: list[ToolSpec]) -> LLMTurn:
        from google.genai import errors, types

        config = types.GenerateContentConfig(
            system_instruction=system,
            tools=[
                types.Tool(
                    function_declarations=[
                        types.FunctionDeclaration(
                            name=t.name,
                            description=t.description,
                            parameters_json_schema=simplify_schema(t.parameters),
                        )
                        for t in tools
                    ]
                )
            ],
            # The SDK would otherwise execute tools itself and loop silently.
            # We run the loop by hand, so every call is validated, capped and logged.
            automatic_function_calling=types.AutomaticFunctionCallingConfig(disable=True),
        )

        try:
            response = self._client.models.generate_content(
                model=self._model,
                contents=[self._to_content(m, types) for m in messages],
                config=config,
            )
        except errors.APIError as exc:
            # 429 and 5xx are worth retrying later; other 4xx (bad request, bad key) are not.
            retryable = exc.code == 429 or (isinstance(exc.code, int) and exc.code >= 500)
            raise LLMError(f"Gemini request failed (HTTP {exc.code}).", retryable=retryable) from None

        return self._from_response(response)

    @staticmethod
    def _to_content(message: Message, types: Any) -> Any:
        if message.role == "assistant":
            if message.raw is not None:
                return message.raw
            parts = [types.Part(text=message.text)] if message.text else []
            parts += [
                types.Part(function_call=types.FunctionCall(name=c.name, args=c.args, id=c.id))
                for c in message.tool_calls
            ]
            return types.Content(role="model", parts=parts)
        if message.role == "tool":
            return types.Content(
                role="user",  # the generateContent API carries tool results in a user turn
                parts=[
                    types.Part(
                        function_response=types.FunctionResponse(id=r.call_id, name=r.name, response=r.payload)
                    )
                    for r in message.tool_results
                ],
            )
        return types.Content(role="user", parts=[types.Part(text=message.text or "")])

    @staticmethod
    def _from_response(response: Any) -> LLMTurn:
        candidates = response.candidates or []
        if not candidates or candidates[0].content is None or not candidates[0].content.parts:
            reason = candidates[0].finish_reason if candidates else "no candidates"
            raise LLMError(f"Gemini returned no usable answer ({reason}).")

        content = candidates[0].content
        # `thought` parts are the model's reasoning summary, never shown to the user.
        text = "".join(p.text for p in content.parts if p.text and not p.thought) or None
        calls = [
            ToolCall(name=p.function_call.name, args=dict(p.function_call.args or {}), id=p.function_call.id)
            for p in content.parts
            if p.function_call
        ]
        return LLMTurn(text=text, tool_calls=calls, raw=content)


def build_llm(settings: Settings) -> GeminiLLM:
    if not settings.gemini_api_key:
        raise RuntimeError("GEMINI_API_KEY is not set. Add it to alphatracker-assistant/.env.")
    from google import genai

    return GeminiLLM(settings.gemini_model, genai.Client(api_key=settings.gemini_api_key))
