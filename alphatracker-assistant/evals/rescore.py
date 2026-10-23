"""Re-grade a SAVED eval run against the CURRENT questions and scorer. No model calls, no cost.

Use it when a question or the scorer was wrong, not the assistant: fix the rubric, then
re-grade the answers you already paid for instead of running the whole set again.

    python -m evals.rescore evals/results/20261023-113000-gemini-3.8-flash.json

Every verdict it CHANGES is listed, with the reason. That is the safeguard: a rubric change
that quietly turned failures into passes would be visible here, line by line.
"""

import argparse
import json
from pathlib import Path

from app.agent import ToolCallRecord
from evals.questions import QUESTIONS
from evals.report import Attempt, QuestionResult, render_markdown, summarize
from evals.scorer import score


def rescore(data: dict) -> tuple[list[QuestionResult], list[str]]:
    questions = {q.id: q for q in QUESTIONS}
    results: list[QuestionResult] = []
    flips: list[str] = []

    for stored in data["results"]:
        question = questions.get(stored["id"])
        if question is None:  # the question was removed since this run; nothing to grade it against
            continue
        attempts = []
        for number, old in enumerate(stored["attempts"], 1):
            if old.get("error"):  # the run itself broke; there is no answer to re-grade
                attempts.append(Attempt(**old))
                continue
            calls = [ToolCallRecord(t["name"], t["args"], t["status"]) for t in old["tool_calls"]]
            verdict = score(question, old["answer"], calls, withheld=old["withheld"])
            attempts.append(Attempt(
                passed=verdict.passed, failures=[f"{c.name}: {c.detail}" for c in verdict.failures],
                answer=old["answer"], tool_calls=old["tool_calls"], tool_ok=verdict.tool_ok, withheld=old["withheld"],
                model_calls=old["model_calls"], prompt_tokens=old["prompt_tokens"],
                output_tokens=old["output_tokens"], seconds=old["seconds"], error=old.get("error"),
            ))
            if verdict.passed != old["passed"]:
                now = "PASS" if verdict.passed else "FAIL"
                flips.append(f"{question.id} attempt {number}: {'PASS' if old['passed'] else 'FAIL'} -> {now}"
                             + ("" if verdict.passed else f"  ({verdict.failures[0].name})"))
        results.append(QuestionResult(question.id, question.category, question.text, attempts))
    return results, flips


def main() -> None:
    parser = argparse.ArgumentParser(description="Re-grade a saved eval run against the current rubric.")
    parser.add_argument("results_json", type=Path)
    args = parser.parse_args()

    data = json.loads(args.results_json.read_text())
    results, flips = rescore(data)
    meta = {**data["meta"], "when": data["meta"]["when"] + " (re-graded)"}

    print(render_markdown(summarize(results), meta, results))
    print(f"{len(flips)} verdict(s) changed by the current rubric:")
    for line in flips:
        print("  -", line)


if __name__ == "__main__":
    main()
