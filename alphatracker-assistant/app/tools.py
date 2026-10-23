import json
from dataclasses import dataclass
from datetime import date
from typing import Any, Callable, Literal

from pydantic import BaseModel, ConfigDict, Field, ValidationError, model_validator

from app.periods import PERIOD_NAMES, PeriodName, resolve_period
from app.spring_client import SpringToolError

# Cap on what one tool result can put in the model's context. Journal notes
# are the only realistically large payload (up to a year of free text).
MAX_PAYLOAD_CHARS = 20_000
JOURNAL_MAX_DAYS = 366  # mirrors JournalEntryService.MAX_RANGE_DAYS on the Spring side

_PERIOD_HELP = (
    "Time window: one of " + ", ".join(PERIOD_NAMES) + ". 'this_week'/'this_month' run up to today. "
    "Defaults to all_time if neither period nor from_date/to_date is given."
)


# --------------------------------------------------------------------------
# Argument models. extra="forbid": a parameter the model invented is rejected
# with a message it can read and correct, instead of being silently ignored.
# --------------------------------------------------------------------------
class _Args(BaseModel):
    model_config = ConfigDict(extra="forbid")


class NoArgs(_Args):
    pass


class WindowArgs(_Args):
    account_id: int | None = Field(
        default=None,
        description="Numeric account id from list_accounts. Omit to include all of the trader's accounts.",
    )
    period: PeriodName | None = Field(default=None, description=_PERIOD_HELP)
    from_date: date | None = Field(default=None, description="Start date, YYYY-MM-DD, inclusive. Use instead of period.")
    to_date: date | None = Field(default=None, description="End date, YYYY-MM-DD, inclusive. Use instead of period.")

    @model_validator(mode="after")
    def _period_or_dates(self) -> "WindowArgs":
        if self.period and (self.from_date or self.to_date):
            raise ValueError("Use either `period` or `from_date`/`to_date`, not both.")
        if self.from_date and self.to_date and self.from_date > self.to_date:
            raise ValueError("`from_date` must not be after `to_date`.")
        return self


class BreakdownArgs(WindowArgs):
    by: Literal["instrument", "setup", "rating"] = Field(
        description="How to group trades: by instrument (ticker), setup (tag), or rating (execution rating 1-5)."
    )


# --------------------------------------------------------------------------
# Tool definitions. Descriptions are written FOR the model: they carry the
# definitions it needs (win/loss/neutral, what null means) so a number is never
# reinterpreted.
# --------------------------------------------------------------------------
_STATS_HELP = (
    "A win is a trade with P&L > 0, a loss is < 0, and neutral is exactly 0 (not a win, not a loss). "
    "winRate = wins / (wins + losses), so neutral trades are excluded; neutralRate = neutral / all trades. "
    "Rates are 0-1 fractions. Money is in dollars; avgLoss is negative. "
    "A null value means there is no data, which is NOT the same as zero."
)


@dataclass(frozen=True)
class Tool:
    name: str
    description: str
    args_model: type[_Args]
    path: str  # Spring path; must be in spring_client.ALLOWED_PATHS
    uses_window: bool = False  # takes period/from_date/to_date
    needs_bounded_window: bool = False  # Spring requires both dates (journal)

    @property
    def parameters_schema(self) -> dict[str, Any]:
        """JSON Schema for the model. The Gemini adapter (item 4) adapts it."""
        return self.args_model.model_json_schema()


TOOLS: dict[str, Tool] = {
    t.name: t
    for t in (
        Tool(
            name="list_accounts",
            description=(
                "List the trader's prop accounts with their ids, names, firms, balances and drawdown figures. "
                "Call this first when the user names an account (e.g. 'my Apex account') to find its id."
            ),
            args_model=NoArgs,
            path="/accounts",
        ),
        Tool(
            name="get_summary",
            description=(
                "Headline performance: trade counts, win rate, total P&L, average win/loss, and the same by "
                "trading day (win days / loss days / neutral days). " + _STATS_HELP
                + " A high neutralRate or neutralDayRate means the trader keeps ending flat, which is worth flagging."
            ),
            args_model=WindowArgs,
            path="/analytics/summary",
            uses_window=True,
        ),
        Tool(
            name="get_breakdown",
            description=(
                "The same stats split into groups by instrument, setup tag, or execution rating. " + _STATS_HELP
                + " For by=setup a trade with several tags counts in each of its tags' groups, so group trade "
                "counts add up to more than the total. Trades with no tag are '(untagged)'; no rating is 'unrated'."
            ),
            args_model=BreakdownArgs,
            path="/analytics/breakdown",
            uses_window=True,
        ),
        Tool(
            name="get_discipline",
            description=(
                "Trades split by whether the trader followed their plan (followedPlan flag): stats for followed vs "
                "broke-plan trades, plus planFollowedRate. 'unspecified' holds older trades with no flag; they are "
                "excluded from planFollowedRate. " + _STATS_HELP
            ),
            args_model=WindowArgs,
            path="/analytics/discipline",
            uses_window=True,
        ),
        Tool(
            name="get_journal_entries",
            description=(
                "The trader's written journal notes and higher-timeframe bias, one entry per day, for a date range "
                "of at most 366 days. Only days with notes or a bias are returned. Requires a bounded range: use a "
                "period other than all_time, or both from_date and to_date."
            ),
            args_model=WindowArgs,
            path="/journal",
            uses_window=True,
            needs_bounded_window=True,
        ),
    )
}


@dataclass(frozen=True)
class ToolResult:
    ok: bool
    payload: dict[str, Any]  # exactly what goes back to the model


class ToolRunner:
    """Validates a model's tool call and executes it against Spring.

    Mistakes the MODEL can fix (bad arguments, unknown tool, someone else's
    account) come back as ToolResult(ok=False) so it can correct itself or
    explain. Failures it cannot fix - a dead token (SpringAuthError) or Spring
    being down (SpringUnavailable) - propagate, and the caller ends the request.
    """

    def __init__(self, spring: Any, today: Callable[[], date]):
        self._spring = spring
        self._today = today

    def run(self, name: str, raw_args: dict[str, Any] | None, token: str) -> ToolResult:
        tool = TOOLS.get(name)
        if tool is None:
            return _error(f"Unknown tool '{name}'. Available tools: {', '.join(TOOLS)}.")

        try:
            args = tool.args_model.model_validate(raw_args or {})
        except ValidationError as exc:
            return _error(_summarize(exc))

        params: dict[str, Any] = {}
        period_applied: dict[str, Any] | None = None
        if tool.uses_window:
            frm, to = self._window(args)  # type: ignore[arg-type]
            problem = _window_problem(tool, frm, to)
            if problem:
                return _error(problem)
            params = {"accountId": args.account_id, "from": _iso(frm), "to": _iso(to)}  # type: ignore[attr-defined]
            if isinstance(args, BreakdownArgs):
                params["by"] = args.by
            period_applied = {"period": getattr(args, "period", None), "from": _iso(frm), "to": _iso(to)}

        try:
            data = self._spring.get(tool.path, token, params)
        except SpringToolError as exc:
            return _error(exc.message)

        payload: dict[str, Any] = {"data": data}
        if tool.path == "/journal":
            payload = _fit_journal(data)
        if period_applied is not None:
            payload["period_applied"] = period_applied
        return ToolResult(True, payload)

    def _window(self, args: WindowArgs) -> tuple[date | None, date | None]:
        if args.period:
            return resolve_period(args.period, self._today())
        if args.from_date or args.to_date:
            return args.from_date, args.to_date
        return resolve_period("all_time", self._today())


def _window_problem(tool: Tool, frm: date | None, to: date | None) -> str | None:
    if not tool.needs_bounded_window:
        return None
    if frm is None or to is None:
        return (
            "get_journal_entries needs a bounded date range of at most 366 days: use a period other than "
            "all_time, or give both from_date and to_date."
        )
    if (to - frm).days + 1 > JOURNAL_MAX_DAYS:
        return f"Date range too large: at most {JOURNAL_MAX_DAYS} days. Use a shorter range."
    return None


def _fit_journal(entries: list[dict[str, Any]]) -> dict[str, Any]:
    """Keep as many entries (oldest first) as fit under MAX_PAYLOAD_CHARS, and
    say so when some were cut, so the model asks for a narrower range instead
    of presenting a partial list as complete."""
    kept: list[dict[str, Any]] = []
    size = 2
    for entry in entries:
        size += len(json.dumps(entry)) + 2
        if size > MAX_PAYLOAD_CHARS:
            break
        kept.append(entry)
    # `count` is the TOTAL number of entries in the range. Without it the model counts the list
    # itself ("you wrote 3 entries"), which the grounding check rightly refuses as unsupported;
    # handing it the figure is cheaper and more honest than letting it count.
    payload: dict[str, Any] = {"data": kept, "count": len(entries)}
    if len(kept) < len(entries):
        payload.update(
            truncated=True,
            returned=len(kept),
            total=len(entries),
            note="Too many entries to return. Ask for a shorter date range to see the rest.",
        )
    return payload


def _error(message: str) -> ToolResult:
    return ToolResult(False, {"error": message})


def _iso(d: date | None) -> str | None:
    return d.isoformat() if d else None


def _summarize(exc: ValidationError) -> str:
    # Field names and rule messages only - never the offending input value.
    parts = []
    for err in exc.errors()[:5]:
        where = ".".join(str(p) for p in err["loc"])
        parts.append(f"{where}: {err['msg']}" if where else err["msg"])
    return "Invalid arguments - " + "; ".join(parts)
