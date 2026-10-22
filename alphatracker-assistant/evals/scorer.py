"""Grade one answer against one question. Pure: no model, no network.

Every requirement becomes its own named Check, so a failure says exactly WHAT was
wrong ("missing 63.64%", "forbidden figure 66.67% appeared", "called no tool") rather
than just "failed". Deterministic by design: a second model grading the first would
add its own errors and make scores unrepeatable.

Matching is deliberately asymmetric, because the two mistakes cost different things:
  - A REQUIRED figure matches the number anywhere in the answer. Models format freely
    ("66.7%", "about 67 percent"), and a strict match would fail correct answers.
  - A FORBIDDEN figure matches only when written the way its kind is written (a percent
    followed by '%', money after '$'). A lenient match would flag the date "September 20"
    as the forbidden 20%, failing correct answers.
"""

import re
from dataclasses import dataclass

from app.agent import ToolCallRecord
from app.grounding import numbers_in
from evals.questions import Expect, Question

_PERCENT = re.compile(r"(\d[\d,]*(?:\.\d+)?)\s*(?:%|percent\b)", re.IGNORECASE)
_MONEY = re.compile(r"\$\s*(\d[\d,]*(?:\.\d+)?)")


@dataclass(frozen=True)
class Check:
    name: str
    passed: bool
    detail: str = ""


@dataclass(frozen=True)
class Verdict:
    checks: list[Check]

    @property
    def passed(self) -> bool:
        return all(c.passed for c in self.checks)

    @property
    def failures(self) -> list[Check]:
        return [c for c in self.checks if not c.passed]

    @property
    def tool_ok(self) -> bool | None:
        """Did the tool-use expectations hold? None when the question has none."""
        tool_checks = [c for c in self.checks if c.name.startswith(("tools", "no_tools"))]
        return all(c.passed for c in tool_checks) if tool_checks else None


def normalize(text: str) -> str:
    """Lower-case, with curly quotes made straight so "can’t" matches "can't"."""
    return text.lower().replace("’", "'").replace("‘", "'").replace("“", '"').replace("”", '"')


def label(e: Expect) -> str:
    if e.kind == "percent":
        return f"{e.value:g}%"
    if e.kind == "money":
        return f"${e.value:,.2f}"
    return f"{e.value:g}"


def _present(e: Expect, answer: str) -> bool:
    return any(abs(n - e.value) <= e.tolerance for n in numbers_in(answer))


def _forbidden_hit(e: Expect, answer: str) -> bool:
    if e.kind == "percent":
        found = [float(m.replace(",", "")) for m in _PERCENT.findall(answer)]
    elif e.kind == "money":
        found = [float(m.replace(",", "")) for m in _MONEY.findall(answer)]
    else:
        found = numbers_in(answer)
    return any(abs(n - e.value) <= e.tolerance for n in found)


def score(question: Question, answer: str, tool_calls: list[ToolCallRecord], withheld: bool = False) -> Verdict:
    checks: list[Check] = []
    text = normalize(answer)

    checks.append(Check("answered", bool(answer.strip()) and not withheld,
                        "the grounding check withheld the answer (unsupported figures)" if withheld else ""))

    # Only calls that returned data count toward a required tool; a rejected call did not answer anything.
    succeeded = {c.name for c in tool_calls if c.status == "ok"}
    if question.no_tools:
        names = [c.name for c in tool_calls]
        checks.append(Check("no_tools", not names, f"called {names}" if names else ""))
    if question.must_call:
        wanted = " or ".join("+".join(alt) for alt in question.must_call)
        passed = any(set(alt) <= succeeded for alt in question.must_call)
        checks.append(Check("tools", passed, f"needed {wanted}; got {sorted(succeeded) or 'no successful tool call'}"))

    for e in question.numbers:
        checks.append(Check(f"has {label(e)}", _present(e, answer), "required figure is missing"))
    for e in question.forbid_numbers:
        hit = _forbidden_hit(e, answer)
        checks.append(Check(f"avoids {label(e)}", not hit, "a wrong-context figure appeared" if hit else ""))

    for words in question.phrases:
        hit = any(normalize(w) in text for w in words)
        checks.append(Check(f"says one of {list(words)[:3]}", hit, "none of the expected words appeared"))
    for phrase in question.forbid_phrases:
        hit = normalize(phrase) in text
        checks.append(Check(f"avoids '{phrase}'", not hit, "a forbidden phrase appeared" if hit else ""))

    return Verdict(checks)
