import pytest
from google.genai import errors, types

from app.llm import GeminiLLM, LLMError, Message, ToolCall, ToolResultMsg, ToolSpec, Usage, simplify_schema
from app.tools import TOOLS


def _all_keys(node):
    if isinstance(node, dict):
        for key, value in node.items():
            yield key
            yield from _all_keys(value)
    elif isinstance(node, list):
        for item in node:
            yield from _all_keys(item)


def test_tool_schemas_are_simplified_for_gemini_without_losing_constraints():
    for tool in TOOLS.values():
        keys = set(_all_keys(simplify_schema(tool.parameters_schema)))
        assert not keys & {"anyOf", "title", "default", "additionalProperties"}, tool.name

    breakdown = simplify_schema(TOOLS["get_breakdown"].parameters_schema)
    assert breakdown["required"] == ["by"]                                     # still required
    assert breakdown["properties"]["by"]["enum"] == ["instrument", "setup", "rating"]
    assert "last_month" in breakdown["properties"]["period"]["enum"]           # optional, but enum survives
    assert breakdown["properties"]["from_date"]["type"] == "string"            # null branch collapsed away


class StubModels:
    def __init__(self, outcome):
        self.outcome, self.kwargs = outcome, None

    def generate_content(self, **kwargs):
        self.kwargs = kwargs
        if isinstance(self.outcome, Exception):
            raise self.outcome
        return self.outcome


class StubClient:
    def __init__(self, outcome):
        self.models = StubModels(outcome)


SPEC = ToolSpec("get_summary", "Headline stats.", TOOLS["get_summary"].parameters_schema)


def test_gemini_adapter_converts_messages_parses_replies_and_maps_errors():
    reply = types.Content(
        role="model",
        parts=[
            types.Part(text="(reasoning summary)", thought=True),
            types.Part(text="Checking."),
            types.Part(function_call=types.FunctionCall(name="get_summary", args={"period": "this_month"}, id="c1")),
        ],
    )
    client = StubClient(types.GenerateContentResponse(
        candidates=[types.Candidate(content=reply)],
        usage_metadata=types.GenerateContentResponseUsageMetadata(prompt_token_count=120, total_token_count=200),
    ))
    earlier_model_turn = types.Content(role="model", parts=[types.Part(text="signed turn")])
    messages = [
        Message("user", text="win rate?"),
        Message("assistant", raw=earlier_model_turn),
        Message("tool", tool_results=[ToolResultMsg("get_summary", {"data": {"x": 1}}, call_id="c0")]),
    ]

    turn = GeminiLLM("test-model", client).generate("be precise", messages, [SPEC])

    # Reply: the thought part is dropped from the text, the tool call is extracted,
    # and the provider-native content is kept so signatures can be replayed.
    assert turn.text == "Checking."
    assert turn.tool_calls == [ToolCall("get_summary", {"period": "this_month"}, "c1")]
    assert turn.raw is reply
    # Billing counts everything beyond the prompt (answer + hidden thinking) as output.
    assert turn.usage == Usage(prompt_tokens=120, output_tokens=80)

    # Request: manual loop (SDK auto-calling off), model + system prompt passed through,
    # an assistant turn with raw content is replayed untouched, and tool results ride in a user turn.
    sent = client.models.kwargs
    assert sent["model"] == "test-model"
    assert sent["config"].system_instruction == "be precise"
    assert sent["config"].automatic_function_calling.disable is True
    assert sent["config"].tools[0].function_declarations[0].name == "get_summary"
    contents = sent["contents"]
    assert contents[0].role == "user" and contents[0].parts[0].text == "win rate?"
    assert contents[1] is earlier_model_turn
    assert contents[2].role == "user"
    assert contents[2].parts[0].function_response.response == {"data": {"x": 1}}

    # Errors: 429/5xx are retryable, a 400 is not, and an empty answer is an error, not silence.
    for code, retryable in ((429, True), (503, True), (400, False)):
        failing = StubClient(errors.APIError(code, {"error": {"message": "nope"}}))
        with pytest.raises(LLMError) as raised:
            GeminiLLM("m", failing).generate("s", [Message("user", text="hi")], [SPEC])
        assert raised.value.retryable is retryable, code

    empty = StubClient(types.GenerateContentResponse(candidates=[]))
    with pytest.raises(LLMError):
        GeminiLLM("m", empty).generate("s", [Message("user", text="hi")], [SPEC])
