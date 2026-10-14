from datetime import date

import pytest

from app.agent import Agent
from app.llm import LLMTurn, Message, ToolCall
from app.spring_client import SpringAuthError
from app.tools import TOOLS, ToolRunner
from tests.fakes import FakeLLM, FakeSpring

SUMMARY = {"trades": {"tradeCount": 31, "winRate": 0.5862}, "days": {"tradingDays": 12}}


def agent_for(llm, spring, **kwargs) -> Agent:
    return Agent(llm, ToolRunner(spring, today=lambda: date(2026, 10, 14)), **kwargs)


def ask(agent, question="how am I doing?", history=None):
    return agent.run(system="SYS", history=history or [], user_message=question, token="tok")


def call(name="get_summary", args=None, id="c1") -> LLMTurn:
    return LLMTurn(text=None, tool_calls=[ToolCall(name, args if args is not None else {"period": "this_month"}, id)],
                   raw=f"raw-{id}")


def answer(text="You're doing fine.") -> LLMTurn:
    return LLMTurn(text=text, tool_calls=[])


def test_a_tool_call_is_executed_and_its_result_fed_back_to_the_model():
    llm = FakeLLM([call(), answer("58.6% over 31 trades.")])
    spring = FakeSpring(response=SUMMARY)

    result = ask(agent_for(llm, spring), history=[Message("user", text="hi"), Message("assistant", text="hello")])

    assert result.answer == "58.6% over 31 trades."
    assert [(t.name, t.args, t.status) for t in result.tool_calls] == [("get_summary", {"period": "this_month"}, "ok")]
    assert spring.calls[0][0] == "/analytics/summary" and spring.calls[0][1] == "tok"  # JWT reached Spring

    first, second = llm.calls
    assert first["system"] == "SYS" and {s.name for s in first["tools"]} == set(TOOLS)
    # History comes first, then the new question - and the model sees its own turn
    # (raw content intact) followed by the tool result on the second request.
    assert [m.role for m in first["messages"]] == ["user", "assistant", "user"]
    assert [m.role for m in second["messages"]] == ["user", "assistant", "user", "assistant", "tool"]
    assert second["messages"][3].raw == "raw-c1"
    assert second["messages"][4].tool_results[0].payload["data"] == SUMMARY


def test_a_bad_tool_call_is_reported_to_the_model_which_then_recovers():
    llm = FakeLLM([call("get_breakdown", {"by": "weather"}), call("get_breakdown", {"by": "setup"}, "c2"), answer()])
    spring = FakeSpring(response={"groups": []})

    result = ask(agent_for(llm, spring))

    assert [t.status for t in result.tool_calls] == ["error", "ok"]
    assert len(spring.calls) == 1  # the invalid call never reached Spring
    error_msg = llm.calls[1]["messages"][-1].tool_results[0].payload["error"]
    assert "by" in error_msg  # the model is told which argument was wrong


def test_an_identical_repeated_call_is_not_executed_again():
    llm = FakeLLM([call(), call(id="c2"), answer()])
    spring = FakeSpring(response=SUMMARY)

    result = ask(agent_for(llm, spring))

    assert [t.status for t in result.tool_calls] == ["ok", "repeat"]
    assert len(spring.calls) == 1
    assert "already made" in llm.calls[2]["messages"][-1].tool_results[0].payload["error"]


def test_hitting_the_iteration_cap_forces_a_final_answer_with_no_tools_offered():
    # The model never stops asking for (different) tools; after 3 rounds it must be made to answer.
    turns = [call("get_summary", {"period": p}, f"c{i}") for i, p in enumerate(["today", "this_week", "last_week"])]
    llm = FakeLLM([*turns, answer("Partial answer.")])
    spring = FakeSpring(response=SUMMARY)

    result = ask(agent_for(llm, spring, max_iterations=3))

    assert result.answer == "Partial answer."
    assert len(spring.calls) == 3
    final = llm.calls[-1]
    assert final["tools"] == []  # no tools on the wrap-up call
    assert "used all your tool calls" in final["system"]


def test_an_answer_with_invented_figures_is_corrected_once_and_withheld_if_it_persists():
    # First attempt invents "4 trades" with no tool call; told off, it calls the tool and answers properly.
    llm = FakeLLM([answer("NQ: 4 trades, 50% win rate."), call(), answer("58.6% over 31 trades.")])
    spring = FakeSpring(response=SUMMARY)

    fixed = ask(agent_for(llm, spring))

    assert fixed.answer == "58.6% over 31 trades."
    assert [t.name for t in fixed.tool_calls] == ["get_summary"]
    correction = llm.calls[1]["messages"][-1]
    assert correction.role == "user" and "4" in correction.text and "Call the right tool" in correction.text

    # A model that keeps inventing figures is not trusted: the answer is withheld, not shown.
    stubborn = FakeLLM([answer("Exactly 4 trades."), answer("Still 4 trades, I am sure.")])
    withheld = ask(agent_for(stubborn, FakeSpring()))

    assert "couldn't verify" in withheld.answer and "4 trades" not in withheld.answer
    assert withheld.unverified == [4.0]


def test_a_dead_token_stops_the_loop_immediately():
    llm = FakeLLM([call(), answer("never reached")])

    with pytest.raises(SpringAuthError):
        ask(agent_for(llm, FakeSpring(error=SpringAuthError())))

    assert len(llm.calls) == 1  # no further model calls (or spend) after the auth failure
