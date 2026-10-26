# AlphaTracker

**Live:** [alpha-tracker-journal.vercel.app](https://alpha-tracker-journal.vercel.app) — register a free account, no seed data needed.

A trading journal built specifically for **prop-firm futures traders** — not a generic
brokerage tracker. AlphaTracker understands firm rules (profit targets, trailing
drawdown), derives contract economics server-side so a trader never has to do the
math themselves, and gives a trader a real place to reflect on a session: notes,
chart screenshots, execution ratings, and setup tags, organized by trading day.

> Built end-to-end (schema → API → UI → deploy) as a full-stack portfolio project.
> See [`CONTEXT.md`](./CONTEXT.md) for the full engineering log — every sprint, every
> design decision, and every bug found along the way, including three caught live
> during the production deploy itself.

---

## Why this exists

Most trading journals are built for retail stock/options traders. A prop-firm futures
trader has a different problem entirely: they're not just tracking P/L, they're being
evaluated against a firm's rules — a maximum drawdown that trails their equity curve,
a profit target to hit, and a dollar-per-point value that varies wildly by
instrument (a 10-point move is $20 on MNQ and $200 on NQ). Get the math wrong and you
misjudge how close you are to blowing an account.

AlphaTracker's core design principle: **the trader inputs only what they observed** —
ticker, direction, entry, exit, size. Every dollar figure (P/L, drawdown floor) is derived server-side from a single source of truth, so there's nowhere for a mistake to hide and nothing for the trader to mistype.

## Features

- **Server-derived trade economics** — log a ticker, direction, entry/exit, and
  contract size; the backend looks up the instrument's point value
  and computes P/L. Unknown tickers are rejected outright, never silently
  defaulted.
- **Prop account management** — track multiple prop firm accounts side by side
  (Evaluation vs. Funded), each with its own starting balance, max drawdown, and
  drawdown mode.
- **Trailing drawdown engine** — replays an account's trade history to compute its
  high-water mark and live drawdown floor, supporting both `END_OF_DAY` and
  `PER_TRADE_CLOSE` trailing modes, plus an optional balance where the floor locks in
  and stops trailing.
- **Calendar P/L matrix & equity curve** — a month-at-a-glance calendar colored by
  daily P/L, with a day drill-down showing that day's executions, win rate, and
  discipline stats.
- **Day-scoped trading journal** — a dedicated `/journal` view: click any day to add
  session notes, a higher-timeframe bias, and chart screenshots (drag-and-drop or
  paste with `Cmd+V`) — all attached to the _day_, not a single trade, since a
  session's reflection doesn't belong to just one execution.
- **Per-trade reflection** — a 1–5 execution rating and freeform, reusable setup tags
  (chip-based multi-select) on each trade, independent of the day-level notes.
- **Screenshot storage behind a swappable interface** — a `StorageService`
  abstraction with local-filesystem and AWS S3 implementations, chosen by one
  environment variable (production runs on S3), with zero changes to any calling code.
- **Analytics API** — win rate, P/L, averages and breakdowns by instrument, setup tag,
  or execution rating, plus a plan-following (discipline) split, all filterable by
  account and date range. A trade is a win above $0, a loss below, and _neutral_ at
  exactly $0; neutral trades and days are reported openly rather than folded into wins
  or losses, because a trader who keeps ending flat is giving up profit.
- **AI trade review assistant** — ask plain-English questions about your own trading
  ("what's my win rate on MNQ this month?", "do I do better when I follow my plan?")
  from a chat panel in the app. Gemini decides which read-only API calls to make; every
  number in an answer comes from the Java API, never from the model
  ([how it's kept honest](#the-ai-assistant)).
- **JWT auth**, with every ownership boundary (a trade, an account, an attachment)
  enforced server-side — never trusted from the client.

## Tech stack

| Layer     | Technology                                                                                                                               |
| --------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| Backend   | Java 21, Spring Boot, Spring Security (JWT), Spring Data JPA, PostgreSQL                                                                 |
| Frontend  | React 19, TypeScript, Vite, Tailwind CSS v4, React Router, Axios, Recharts, date-fns, react-markdown                                     |
| Assistant | Python 3.13, FastAPI, Gemini (tool calling via `google-genai`), httpx, Pydantic                                                          |
| Testing   | JUnit 5, Mockito, `@DataJpaTest` / `@WebMvcTest` slice tests; pytest for the assistant                                                   |
| Deploy    | Docker (multi-stage build), Railway (backend + assistant, two services), Vercel (frontend), Neon (Postgres), AWS S3 (screenshot storage) |

## Architecture at a glance

```
alphatracker-frontend/          React SPA (Vite)
  src/pages/                    Route-level views (Dashboard, Journal, Auth)
  src/components/               Feature-organized UI (dashboard/, journal/, attachments/, assistant/, layout/)
  src/hooks/                    Data-fetching hooks (useTrades, useAccounts, useJournalDay)
  src/api/                      Thin request wrappers around apiClient (axios)

src/main/java/com/alphatracker/api/
  trade/                        Trade entity, Instrument (contract economics), TradeService
  account/                      Prop account entity, drawdown engine (AccountService)
  journal/                      Day-scoped JournalEntry + JournalAttachment (screenshots)
  analytics/                    Read-only stats: win/loss/neutral, breakdowns, discipline
  storage/                      StorageService interface + local filesystem and S3 adapters
  security/                     JWT filter chain, CORS policy
  exception/                    Centralized exception → HTTP status mapping

alphatracker-assistant/         Python/FastAPI AI assistant (its own service and Docker image)
  app/                          Chat endpoint, tool loop, read-only Spring client, rate limiter
  evals/                        50-question eval set, independent answer key, runner
  tests/                        pytest suite
```

**Request flow:** the frontend never computes money. A trade entry form posts raw
observations to `POST /api/v1/trades`; `TradeService` resolves the `Instrument`,
derives P/L from the instrument's point value, and returns the persisted trade. The frontend just
displays what the server already calculated — this is deliberate, and it's the same
reason attachments and journal entries are ownership-checked on _every_ request
server-side rather than trusted from a client-supplied ID.

See [`CONTEXT.md`](./CONTEXT.md) for the full data model, the drawdown math, and a
sprint-by-sprint log of what was built and why (including a mid-project pivot from
trade-scoped to day-scoped journal attachments, and the reasoning behind it).

## The AI assistant

Ask a question in the chat panel and the browser sends it, with your login token, to a
separate Python service. That service lets Gemini choose from a few **read-only** tools
(summary, breakdown, discipline, journal notes, accounts); each tool is a `GET` to the
Java API carrying _your_ token, so the existing ownership checks decide what you can see.

**The one rule: the model never does arithmetic.** Gemini decides what to ask; every
number in an answer comes from the Java API. Keeping that true took more than a prompt:

- **A code-level check, not just an instruction.** Any figure in an answer that no tool
  returned is sent back to be fixed, and withheld if it still isn't. In early testing it
  caught 6 of 50 first-draft answers, including a follow-up question the model had
  answered from memory with invented numbers; after fixes it catches under 3%.
- **Read-only by construction.** The client has no write method, only five allowed paths,
  and never stores or logs the token.
- **Per-user and global rate limits**, keyed on the login (not the token, so signing in
  again doesn't reset it), and a plain "you can send again in N seconds" countdown.
- **Safe rendering.** Answers are Markdown with raw HTML disabled, and images are never
  rendered, since a remote image would load automatically and could carry private figures
  out of the page.

**How it's tested.** A 50-question eval set runs against the real stack, graded by code
(figures that must appear, wrong-context figures that must not, tools that must be
called, refusals that must be refusals) against an answer key computed independently of
the Java code. The Java API and that key agree on 111 queries. Result: 148 of 150
graded answers passed on the set it was tuned against, and 18 of 18 on questions written
before they were run and never tuned to. That is a small sample and not proof the
assistant is always right, which is why the checks above exist.

## Getting started

### Prerequisites

- **Java 21+**
- **Node 18+** and npm
- **PostgreSQL** running locally

### 1. Database

Create a local Postgres database:

```sql
CREATE DATABASE alphatracker;
```

Update `src/main/resources/application.yml` with your own local credentials:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/alphatracker
    username: <your-postgres-username>
    password: <your-postgres-password>
```

> ⚠️ The `application.security.jwt.secret-key` in that same file is a placeholder
> checked in for local development only. Replace it with a securely generated secret
> (and move it out of version control, e.g. an environment variable) before deploying
> anywhere real.

Schema is managed via `spring.jpa.hibernate.ddl-auto: update` — tables are created
automatically on first run; no manual migration step needed for local development.

### 2. Backend

```bash
./mvnw spring-boot:run
```

Runs on `http://localhost:8080`. The Maven wrapper (`mvnw`/`mvnw.cmd`) is checked in,
so no local Maven install is required.

### 3. Frontend

```bash
cd alphatracker-frontend
npm install
npm run dev
```

Runs on `http://localhost:5173`. Register a new account from the login screen to get
started — there's no seed data.

### 4. AI assistant (optional)

The chat panel needs the Python service running and a Gemini API key from
[Google AI Studio](https://aistudio.google.com/apikey):

```bash
cd alphatracker-assistant
python3 -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
# create alphatracker-assistant/.env containing:  GEMINI_API_KEY=<your key>
# (the file is git-ignored; never commit a key)
uvicorn app.main:app --port 8000
```

It talks to the backend at `http://localhost:8080/api/v1` by default. See
[`alphatracker-assistant/README.md`](./alphatracker-assistant/README.md) for every setting
(rate limits, timezone, allowed origins). In production the frontend finds the service
through `VITE_ASSISTANT_API_URL` (include the `https://`).

## Running the tests

```bash
# Backend — 65 tests across repository (@DataJpaTest), service (Mockito), and
# controller (@WebMvcTest) layers
./mvnw test

# Frontend — type-checks as part of the build (tsc -b && vite build)
cd alphatracker-frontend
npm run build
npm run lint

# Assistant — 45 pytest tests: the tool loop, the number check, the rate limiter, CORS,
# the Gemini adapter, and the eval harness's own scorer. No model or network needed.
cd alphatracker-assistant
pytest
```

Backend tests run against a real local Postgres instance (there's no H2/in-memory
database in the stack) — `@DataJpaTest` wraps each test in a transaction it rolls
back afterward, so nothing persists to your dev database.

**Evals** call the real model, so they need a running local backend and a Gemini key,
and cost a few cents. They seed a dedicated demo trader into your _local_ database
(they refuse to run against anything that isn't localhost):

```bash
cd alphatracker-assistant
python -m evals.verify_oracle      # does the Java API agree with the independent answer key?
python -m evals.run                # the 50-question set
python -m evals.run --set blind    # 18 questions that were never tuned against
python -m evals.rescore <results.json>   # re-grade a saved run, free
```

## Roadmap

Full sprint-by-sprint history — including completed work, an in-flight backlog item,
and a couple of mid-project design pivots — lives in [`CONTEXT.md`](./CONTEXT.md).
At a glance:

- ✅ Core trade logging, calendar visualization, JWT auth
- ✅ Multi-account prop firm management + trailing drawdown engine
- ✅ Day-scoped journal: notes, HTF bias, chart screenshots, per-trade ratings/tags
- ✅ Deployed to production: Vercel + Railway + Neon + AWS S3 (see the note below)
- ✅ Analytics API and an AI trade review assistant: chat panel, eval set with an
  independent answer key, rate limiting, deployed as its own service
- 🔜 Stripe billing (subscription tiers)
- 🔜 Discipline streaks, session analytics

## A note on how this was built

This project was built collaboratively with Claude Code, working through design
decisions rather than accepting first-draft output: sequencing features deliberately
(storage before the entities that use it), writing tests alongside every backend
layer, and catching real bugs before and during production deploy rather than after —
an inclusive date-range query that would have double-counted a trade logged at exactly
midnight, a password hash leaking in every trade API response, and — live, while
standing up the Railway deployment — an exception handler that silently discarded the
real error on every failure, found and fixed specifically because it was blocking
debugging of an actual production issue. All of them are documented in `CONTEXT.md`
alongside their fixes.

The assistant followed the same approach, with the added lesson that a model's score is
only worth as much as the test behind it. Reading the real answers, not just the
pass/fail numbers, is what found a model inventing figures for a follow-up question,
LaTeX that a chat panel can't render, and a Markdown image that could have carried
private data out of the page without a click. Several early "failures" turned out to be
wrong questions rather than wrong answers, and the first production deploy failed
because of a single missing `https://` in one environment variable. Each is written up
in `CONTEXT.md`.
