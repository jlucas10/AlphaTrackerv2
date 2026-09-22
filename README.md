# AlphaTracker

A trading journal built specifically for **prop-firm futures traders** — not a generic
brokerage tracker. AlphaTracker understands firm rules (profit targets, trailing
drawdown), derives contract economics server-side so a trader never has to do the
math themselves, and gives a trader a real place to reflect on a session: notes,
chart screenshots, execution ratings, and setup tags, organized by trading day.

> Built end-to-end (schema → API → UI) as a full-stack portfolio project. See
> [`CONTEXT.md`](./CONTEXT.md) for the full engineering log — every sprint, every
> design decision, and every bug found along the way.

---

## Why this exists

Most trading journals are built for retail stock/options traders. A prop-firm futures
trader has a different problem entirely: they're not just tracking P/L, they're being
evaluated against a firm's rules — a maximum drawdown that trails their equity curve,
a profit target to hit, a fixed commission per contract that varies wildly by
instrument (a 10-point move is $20 on MNQ and $200 on NQ). Get the math wrong and you
misjudge how close you are to blowing an account.

AlphaTracker's core design principle: **the trader inputs only what they observed** —
ticker, direction, entry, exit, size. Every dollar figure (commission, gross P/L, net
P/L, drawdown floor) is derived server-side from a single source of truth, so there's
nowhere for a mistake to hide and nothing for the trader to mistype.

## Features

- **Server-derived trade economics** — log a ticker, direction, entry/exit, and
  contract size; the backend looks up the instrument's point value and round-turn fee
  and computes net P/L. Unknown tickers are rejected outright, never silently
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
  paste with `Cmd+V`) — all attached to the *day*, not a single trade, since a
  session's reflection doesn't belong to just one execution.
- **Per-trade reflection** — a 1–5 execution rating and freeform, reusable setup tags
  (chip-based multi-select) on each trade, independent of the day-level notes.
- **Screenshot storage behind a swappable interface** — a `StorageService`
  abstraction with a local-filesystem implementation today; an S3 adapter is a
  drop-in swap later with zero changes to any calling code.
- **JWT auth**, with every ownership boundary (a trade, an account, an attachment)
  enforced server-side — never trusted from the client.

## Tech stack

| Layer      | Technology                                                             |
|------------|-------------------------------------------------------------------------|
| Backend    | Java 21, Spring Boot, Spring Security (JWT), Spring Data JPA, PostgreSQL |
| Frontend   | React 19, TypeScript, Vite, Tailwind CSS v4, React Router, Axios, Recharts, date-fns |
| Testing    | JUnit 5, Mockito, `@DataJpaTest` / `@WebMvcTest` slice tests             |

## Architecture at a glance

```
alphatracker-frontend/          React SPA (Vite)
  src/pages/                    Route-level views (Dashboard, Journal, Auth)
  src/components/               Feature-organized UI (dashboard/, journal/, attachments/, layout/)
  src/hooks/                    Data-fetching hooks (useTrades, useAccounts, useJournalDay)
  src/api/                      Thin request wrappers around apiClient (axios)

src/main/java/com/alphatracker/api/
  trade/                        Trade entity, Instrument (contract economics), TradeService
  account/                      Prop account entity, drawdown engine (AccountService)
  journal/                      Day-scoped JournalEntry + JournalAttachment (screenshots)
  storage/                      StorageService interface + local filesystem adapter
  security/                     JWT filter chain, CORS policy
  exception/                    Centralized exception → HTTP status mapping
```

**Request flow:** the frontend never computes money. A trade entry form posts raw
observations to `POST /api/v1/trades`; `TradeService` resolves the `Instrument`,
derives commission and net P/L, and returns the persisted trade. The frontend just
displays what the server already calculated — this is deliberate, and it's the same
reason attachments and journal entries are ownership-checked on *every* request
server-side rather than trusted from a client-supplied ID.

See [`CONTEXT.md`](./CONTEXT.md) for the full data model, the drawdown math, and a
sprint-by-sprint log of what was built and why (including a mid-project pivot from
trade-scoped to day-scoped journal attachments, and the reasoning behind it).

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

## Running the tests

```bash
# Backend — 65 tests across repository (@DataJpaTest), service (Mockito), and
# controller (@WebMvcTest) layers
./mvnw test

# Frontend — type-checks as part of the build (tsc -b && vite build)
cd alphatracker-frontend
npm run build
npm run lint
```

Backend tests run against a real local Postgres instance (there's no H2/in-memory
database in the stack) — `@DataJpaTest` wraps each test in a transaction it rolls
back afterward, so nothing persists to your dev database.

## Roadmap

Full sprint-by-sprint history — including completed work, an in-flight backlog item,
and a couple of mid-project design pivots — lives in [`CONTEXT.md`](./CONTEXT.md).
At a glance:

- ✅ Core trade logging, calendar visualization, JWT auth
- ✅ Multi-account prop firm management + trailing drawdown engine
- ✅ Day-scoped journal: notes, HTF bias, chart screenshots, per-trade ratings/tags
- 🔜 Stripe billing (subscription tiers)
- 🔜 Discipline streaks, session analytics, Docker + AWS deployment

## A note on how this was built

This project was built collaboratively with Claude Code, working through design
decisions rather than accepting first-draft output: sequencing features deliberately
(storage before the entities that use it), writing tests alongside every backend
layer, and — notably — catching and fixing two real bugs during a self-review pass
before they shipped: an inclusive date-range query that would have double-counted a
trade logged at exactly midnight, and a password hash that was leaking in every trade
API response. Both are documented in `CONTEXT.md` alongside the fix.
