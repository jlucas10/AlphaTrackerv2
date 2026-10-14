"""A safety net for the project's core rule: every number in an answer comes
from the Java API, never from the model.

The system prompt tells the model this, but a prompt is a request, not a
guarantee (a live test showed Gemini inventing "4 trades, -$140" for a
follow-up question it never called a tool for). So the agent also CHECKS: it
collects every number the model is allowed to quote and rejects an answer
containing any other. Deliberately number-only and conservative; it does not
try to understand the sentence, only that no figure appeared from nowhere.
"""

import re
from typing import Any

_NUMBER = re.compile(r"\d[\d,]*(?:\.\d+)?")
# "1." / "2)" at the start of a line is list numbering, not a figure.
_LIST_MARKER = re.compile(r"^\s*\d+[.)]\s", re.MULTILINE)
# Harmless in any answer: zero, "1 trade", and the 100 in "of 100%".
_ALWAYS_OK = frozenset({0.0, 1.0, 100.0})
# Half a cent: lets $19.33 match 19.333... without letting 19 match 20.
_TOLERANCE = 0.0051


def numbers_in(text: str) -> list[float]:
    values = []
    for match in _NUMBER.finditer(_LIST_MARKER.sub("", text)):
        try:
            values.append(float(match.group().replace(",", "")))
        except ValueError:
            pass
    return values


class KnownNumbers:
    """Every figure the model may legitimately quote in one request."""

    def __init__(self) -> None:
        self._values: set[float] = set()

    def add_text(self, text: str) -> None:
        """Numbers the model was handed as words: the user's question and history,
        today's date, account names like 'Apex 50k #1'."""
        self._values.update(numbers_in(text))

    def add_payload(self, node: Any) -> None:
        """Numbers in a tool result, at any depth (including inside strings such
        as dates and journal notes)."""
        if isinstance(node, bool) or node is None:
            return
        if isinstance(node, (int, float)):
            self._add_value(abs(float(node)))
        elif isinstance(node, str):
            self.add_text(node)
        elif isinstance(node, dict):
            for value in node.values():
                self.add_payload(value)
        elif isinstance(node, list):
            for item in node:
                self.add_payload(item)

    def _add_value(self, value: float) -> None:
        self._values.add(value)
        # The model may round for display ($413 for 412.5).
        self._values.update(round(value, places) for places in (0, 1, 2))
        if value <= 1.0:
            # A rate such as 0.5862 is presented as 58.6% / 59%.
            percent = value * 100
            self._values.update(round(percent, places) for places in (0, 1, 2))

    def unknown_in(self, answer: str) -> list[float]:
        """Figures in `answer` that match nothing known (in order, no duplicates)."""
        unknown: list[float] = []
        for number in numbers_in(answer):
            if number in _ALWAYS_OK or number in unknown:
                continue
            if any(abs(number - known) <= _TOLERANCE for known in self._values):
                continue
            unknown.append(number)
        return unknown
