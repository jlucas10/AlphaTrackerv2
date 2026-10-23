from app.agent import ToolCallRecord
from app.llm import LLMTurn, Usage
from evals.metered import MeteredLLM
from evals.questions import QUESTIONS, Expect
from evals.report import Attempt, QuestionResult, summarize
from evals.scorer import label, score
from tests.fakes import FakeLLM

BY_ID = {q.id: q for q in QUESTIONS}


def perfect(q):
    """An answer and tool-call list built purely from the question's own expectations."""
    parts = [label(e) for e in q.numbers] + [words[0] for words in q.phrases]
    calls = [ToolCallRecord(name, {}, "ok") for name in (q.must_call[0] if q.must_call else ())]
    return " ".join(parts) or "Here is your answer.", calls


def test_the_scorer_accepts_a_perfect_answer_and_rejects_each_kind_of_wrong_one_for_all_50_questions():
    for q in QUESTIONS:
        answer, calls = perfect(q)
        assert score(q, answer, calls).passed, (q.id, [c.name for c in score(q, answer, calls).failures])

        # Withheld by the grounding check: never a pass, however good the text looks.
        assert not score(q, answer, calls, withheld=True).passed, q.id

        # Each wrong-context figure must fail the answer when it shows up.
        for banned in q.forbid_numbers:
            assert not score(q, f"{answer} {label(banned)}", calls).passed, (q.id, banned)

        # Right-looking answer, but the data was never fetched (the follow-up failure mode).
        if q.must_call:
            assert not score(q, answer, []).passed, q.id
            failed_tool_call = [ToolCallRecord(name, {}, "error") for name in q.must_call[0]]
            assert not score(q, answer, failed_tool_call).passed, q.id    # a rejected call returned nothing

        # A decline that still went and fetched data.
        if q.no_tools:
            assert not score(q, answer, [ToolCallRecord("get_summary", {}, "ok")]).passed, q.id

        # Required content missing.
        if q.numbers:
            assert not score(q, "I have no figures for you.", calls).passed, q.id
        if q.phrases:
            assert not score(q, " ".join(label(e) for e in q.numbers) or "Nothing relevant.", calls).passed, q.id

        for phrase in q.forbid_phrases:
            assert not score(q, f"{answer} {phrase}", calls).passed, (q.id, phrase)


def test_a_forbidden_percentage_is_not_tripped_by_a_date_but_is_caught_when_it_is_a_percentage():
    q = BY_ID["neutral-02"]            # needs 14.29%, forbids the overall 20%
    answer, calls = perfect(q)

    assert score(q, f"{answer} The Topstep account since September 20, 2026.", calls).passed
    assert not score(q, f"{answer} (20% overall)", calls).passed
    assert not score(q, f"{answer} (20 percent overall)", calls).passed

    # Money works the same way: a forbidden $112 needs the $ sign, a bare 112 is not it.
    sample = BY_ID["summary-05"]
    answer, calls = perfect(sample)
    assert score(sample, f"{answer} Reference number 112.", calls).passed
    assert not score(sample, f"{answer} versus $112.00 across all accounts", calls).passed

    # LaTeX in ANY answer fails it, however right the figures are (the chat panel can't render it).
    for markup in (r"$$\text{winRate} = \frac{wins}{wins + losses}$$", r"\times 100"):
        assert not score(q, f"{perfect(q)[0]} {markup}", perfect(q)[1]).passed
    assert score(q, f"{perfect(q)[0]} Costs $5 to $10 per contract.", perfect(q)[1]).passed   # ordinary dollars are fine

    # Curly apostrophes must not defeat a decline: "can’t" should match "can't".
    decline = BY_ID["scope-01"]
    assert score(decline, "I can’t analyse that sequence.", []).passed


def test_report_aggregates_pass_rates_flakiness_tool_accuracy_and_latency():
    def attempt(passed, seconds, tool_ok=True, withheld=False, error=None):
        return Attempt(passed, [] if passed else ["has 5%: required figure is missing"], "answer", [], tool_ok,
                       withheld, model_calls=2, prompt_tokens=100, output_tokens=50, seconds=seconds, error=error)

    results = [
        QuestionResult("a-1", "summary", "q", [attempt(True, 4.0), attempt(True, 6.0)]),                 # solid
        QuestionResult("a-2", "summary", "q", [attempt(True, 5.0), attempt(False, 7.0, tool_ok=False)]),  # flaky
        QuestionResult("b-1", "journal", "q", [attempt(False, 9.0, withheld=True), attempt(False, 3.0, error="boom")]),
    ]

    s = summarize(results)

    assert (s["questions"], s["attempts"]) == (3, 6)
    assert s["pass_rate"] == round(3 / 6, 4)
    assert s["by_category"]["summary"]["pass_rate"] == 0.75 and s["by_category"]["journal"]["pass_rate"] == 0.0
    assert (s["always_pass"], s["flaky"], s["always_fail"]) == (["a-1"], ["a-2"], ["b-1"])
    assert s["tool_accuracy"] == round(5 / 6, 4)         # one attempt expected a tool and did not use it
    assert (s["withheld"], s["run_errors"]) == (1, 1)
    assert s["seconds_median"] == 5.5 and s["seconds_p95"] == 9.0
    assert (s["prompt_tokens_avg"], s["output_tokens_avg"], s["tokens_total"]) == (100, 50, 900)


def test_metered_llm_counts_calls_and_tokens_without_changing_what_the_model_returns():
    turns = [LLMTurn("a", [], usage=Usage(100, 40)), LLMTurn("b", [], usage=Usage(30, 10)), LLMTurn("c", [])]  # last: no usage
    metered = MeteredLLM(FakeLLM(turns))

    returned = [metered.generate("sys", [], []).text for _ in range(3)]

    assert returned == ["a", "b", "c"]
    assert (metered.calls, metered.prompt_tokens, metered.output_tokens) == (3, 130, 50)
    assert metered.model_seconds >= 0
    metered.reset()
    assert (metered.calls, metered.prompt_tokens, metered.output_tokens) == (0, 0, 0)
