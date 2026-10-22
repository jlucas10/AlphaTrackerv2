"""Turn raw attempts into the numbers worth reading, and a report a person can read."""

import math
import statistics
from dataclasses import dataclass


@dataclass
class Attempt:
    passed: bool
    failures: list[str]            # human-readable, e.g. "has 63.64%: required figure is missing"
    answer: str
    tool_calls: list[dict]         # [{"name", "args", "status"}]
    tool_ok: bool | None           # did the tool expectations hold? None if the question has none
    withheld: bool                 # the grounding check refused to show the answer
    model_calls: int               # >1 + number of tool rounds means a corrected answer or retries
    prompt_tokens: int
    output_tokens: int
    seconds: float
    error: str | None = None       # the run itself failed (model/Spring error), not the answer


@dataclass
class QuestionResult:
    id: str
    category: str
    text: str
    attempts: list[Attempt]

    @property
    def passes(self) -> int:
        return sum(a.passed for a in self.attempts)


def _rate(passed: int, total: int) -> float | None:
    return round(passed / total, 4) if total else None


def summarize(results: list[QuestionResult]) -> dict:
    attempts = [a for r in results for a in r.attempts]

    by_category: dict[str, dict] = {}
    for r in results:
        cat = by_category.setdefault(r.category, {"questions": 0, "attempts": 0, "passed": 0})
        cat["questions"] += 1
        cat["attempts"] += len(r.attempts)
        cat["passed"] += r.passes
    for cat in by_category.values():
        cat["pass_rate"] = _rate(cat["passed"], cat["attempts"])

    tool_attempts = [a for a in attempts if a.tool_ok is not None]
    seconds = sorted(a.seconds for a in attempts)
    n = len(attempts)

    return {
        "questions": len(results),
        "attempts": n,
        "pass_rate": _rate(sum(a.passed for a in attempts), n),
        # Right tool(s) called (and no tool where none belonged), measured only where a question expects it.
        "tool_accuracy": _rate(sum(bool(a.tool_ok) for a in tool_attempts), len(tool_attempts)),
        "by_category": by_category,
        "always_pass": [r.id for r in results if r.attempts and r.passes == len(r.attempts)],
        # Passes sometimes and fails sometimes: the model is not reliable on these, only lucky.
        "flaky": [r.id for r in results if 0 < r.passes < len(r.attempts)],
        "always_fail": [r.id for r in results if r.attempts and r.passes == 0],
        "withheld": sum(a.withheld for a in attempts),
        "run_errors": sum(a.error is not None for a in attempts),
        "seconds_median": round(statistics.median(seconds), 2) if seconds else None,
        "seconds_p95": round(seconds[max(math.ceil(0.95 * n) - 1, 0)], 2) if seconds else None,
        "model_calls_avg": round(sum(a.model_calls for a in attempts) / n, 2) if n else None,
        "prompt_tokens_avg": round(sum(a.prompt_tokens for a in attempts) / n) if n else None,
        "output_tokens_avg": round(sum(a.output_tokens for a in attempts) / n) if n else None,
        "tokens_total": sum(a.prompt_tokens + a.output_tokens for a in attempts),
    }


def _pct(x: float | None) -> str:
    return "n/a" if x is None else f"{x * 100:.1f}%"


def render_markdown(summary: dict, meta: dict, results: list[QuestionResult]) -> str:
    lines = [
        f"# Eval report: {meta['model']}",
        "",
        f"- Run at {meta['when']}, {summary['questions']} questions x {meta['repeat']} attempt(s) = {summary['attempts']} attempts",
        f"- **Pass rate: {_pct(summary['pass_rate'])}**  |  tool accuracy: {_pct(summary['tool_accuracy'])}  |  "
        f"answers withheld by the grounding check: {summary['withheld']}  |  run errors: {summary['run_errors']}",
        f"- Latency per question: median {summary['seconds_median']}s, p95 {summary['seconds_p95']}s  |  "
        f"model calls avg {summary['model_calls_avg']}  |  tokens avg {summary['prompt_tokens_avg']} in / "
        f"{summary['output_tokens_avg']} out ({summary['tokens_total']:,} total)",
        "",
        "## By category",
        "",
        "| Category | Questions | Pass rate |",
        "|---|---|---|",
    ]
    for name, cat in summary["by_category"].items():
        lines.append(f"| {name} | {cat['questions']} | {_pct(cat['pass_rate'])} ({cat['passed']}/{cat['attempts']}) |")

    if summary["flaky"]:
        lines += ["", f"**Flaky (pass only sometimes):** {', '.join(summary['flaky'])}"]
    if summary["always_fail"]:
        lines += ["", f"**Always fail:** {', '.join(summary['always_fail'])}"]

    failing = [r for r in results if r.passes < len(r.attempts)]
    if failing:
        lines += ["", "## Failures", ""]
        for r in failing:
            bad = next(a for a in r.attempts if not a.passed)
            lines.append(f"### {r.id} ({r.passes}/{len(r.attempts)} passed): {r.text}")
            if bad.error:
                lines.append(f"- run error: {bad.error}")
            lines += [f"- {f}" for f in bad.failures]
            tools = ", ".join(f"{t['name']}({t['status']})" for t in bad.tool_calls) or "none"
            lines += [f"- tools called: {tools}", f"- answer: {bad.answer.strip()[:400]!r}", ""]
    return "\n".join(lines) + "\n"
