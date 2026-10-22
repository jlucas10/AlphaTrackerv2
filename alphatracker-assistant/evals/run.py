"""Run the eval set against the real stack and write a report.

    python -m evals.run [--model gemini-3.8-flash] [--repeat 1] [--category summary] [--id summary-01]
                        [--spring-url http://localhost:8080/api/v1]

What runs for real: the seeded local Spring, the real SpringClient, the real model, and
the SAME `answer_question` the /chat endpoint uses. What is pinned: "today" (so "this
month" always means the same dates). Questions run one at a time - slower than parallel,
but kind to the model's rate limits and easy to read in a log.
"""

import argparse
import json
import re
import time
from dataclasses import asdict
from datetime import datetime
from pathlib import Path

from app.agent import AgentError
from app.chat import answer_question, to_messages
from app.config import Settings
from app.llm import LLMError, build_llm
from app.spring_client import SpringClient, SpringUnavailable
from app.tools import ToolRunner
from evals.dataset import TODAY
from evals.metered import MeteredLLM
from evals.questions import QUESTIONS, validate
from evals.report import Attempt, QuestionResult, render_markdown, summarize
from evals.scorer import score
from evals.seed import seed

RESULTS_DIR = Path(__file__).parent / "results"
MAX_RETRIES = 3


def run_attempt(question, settings, spring, runner, metered, token) -> Attempt:
    metered.reset()
    started = time.perf_counter()
    result, error = None, None

    for tries in range(1, MAX_RETRIES + 1):
        try:
            result = answer_question(
                settings=settings, spring=spring, runner=runner, get_llm=lambda: metered, token=token,
                message=question.text, history=to_messages(question.history), today=TODAY,
            )
            break
        except LLMError as exc:
            if exc.retryable and tries < MAX_RETRIES:
                time.sleep(2 ** tries)  # rate limit or a blip: back off and try again
                continue
            error = f"model error: {exc}"
            break
        except (AgentError, SpringUnavailable) as exc:
            error = f"{type(exc).__name__}: {exc}"
            break

    seconds = round(time.perf_counter() - started, 2)
    common = dict(model_calls=metered.calls, prompt_tokens=metered.prompt_tokens,
                  output_tokens=metered.output_tokens, seconds=seconds)
    if result is None:
        # The run itself broke. That counts as a failed attempt, never a skipped one.
        return Attempt(False, [error or "no result"], "", [], None, False, error=error, **common)

    verdict = score(question, result.answer, result.tool_calls, withheld=bool(result.unverified))
    return Attempt(
        passed=verdict.passed,
        failures=[f"{c.name}: {c.detail}" for c in verdict.failures],
        answer=result.answer,
        tool_calls=[{"name": t.name, "args": t.args, "status": t.status} for t in result.tool_calls],
        tool_ok=verdict.tool_ok,
        withheld=bool(result.unverified),
        **common,
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="Score the assistant against the eval set.")
    parser.add_argument("--model", help="Gemini model id (default: GEMINI_MODEL / the app default)")
    parser.add_argument("--repeat", type=int, default=1, help="attempts per question; >1 exposes flaky answers")
    parser.add_argument("--category", help="only this category")
    parser.add_argument("--id", dest="ids", action="append", help="only this question id (repeatable)")
    parser.add_argument("--spring-url", default=Settings().spring_api_base_url)
    parser.add_argument("--out", type=Path, default=RESULTS_DIR)
    args = parser.parse_args()

    validate(QUESTIONS)
    questions = [q for q in QUESTIONS
                 if (not args.category or q.category == args.category) and (not args.ids or q.id in args.ids)]
    if not questions:
        raise SystemExit("No questions match those filters.")

    settings = Settings()
    model = args.model or settings.gemini_model
    settings = settings.model_copy(update={"gemini_model": model})

    seeded = seed(args.spring_url)  # re-seeds every run so the data is always exactly the dataset
    spring = SpringClient(seeded.base_url)
    runner = ToolRunner(spring, today=lambda: TODAY)
    metered = MeteredLLM(build_llm(settings))

    print(f"Running {len(questions)} question(s) x {args.repeat} on {model} against {seeded.base_url}\n")
    results: list[QuestionResult] = []
    for index, q in enumerate(questions, 1):
        attempts = []
        for _ in range(args.repeat):
            attempt = run_attempt(q, settings, spring, runner, metered, seeded.token)
            attempts.append(attempt)
            mark = "PASS" if attempt.passed else "FAIL"
            note = "" if attempt.passed else f"  <- {attempt.failures[0][:90]}"
            print(f"[{index:>2}/{len(questions)}] {q.id:<16} {mark}  {attempt.seconds:>5.1f}s{note}", flush=True)
        results.append(QuestionResult(q.id, q.category, q.text, attempts))

    summary = summarize(results)
    meta = {"model": model, "repeat": args.repeat, "when": datetime.now().strftime("%Y-%m-%d %H:%M"),
            "today_pinned": TODAY.isoformat()}
    report = render_markdown(summary, meta, results)

    args.out.mkdir(parents=True, exist_ok=True)
    stem = f"{datetime.now():%Y%m%d-%H%M%S}-{re.sub(r'[^A-Za-z0-9._-]', '_', model)}"
    (args.out / f"{stem}.json").write_text(json.dumps(
        {"meta": meta, "summary": summary, "results": [asdict(r) for r in results]}, indent=2, default=str))
    (args.out / f"{stem}.md").write_text(report)

    print("\n" + report)
    print(f"Saved {args.out / (stem + '.md')} (+ .json)")


if __name__ == "__main__":
    main()
