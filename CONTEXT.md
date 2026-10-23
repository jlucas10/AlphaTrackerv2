# AlphaTracker — Project Context & Architecture

A futures trading journal built for a **prop-firm trader**. Not a retail brokerage app: the
trader is evaluated against firm rules (profit targets, trailing drawdown). No commission or
fee is modeled: prop firms don't pass per-trade commissions through the way a retail broker does.

## Core Tech Stack

- **Backend:** Java 17, Spring Boot, Spring Security, JWT Auth, PostgreSQL (Hibernate/JPA)
- **Frontend:** React 19, TypeScript, Vite, Tailwind CSS v4, Axios, Recharts, date-fns

## Data Flow & Architecture

- Authentication via `/api/v1/auth/register` and `/api/v1/auth/authenticate` returning JWTs.
- `apiClient.ts` handles Bearer token injection for protected REST calls.
- `useTrades` owns the entire `/api/v1/trades` surface: fetch, `refetch`, and `deleteTrade`.
  It supports account-scoped queries (`/trades?accountId=...`), and exposes `loading` (first load
  only) and `refreshing` (background) so the dashboard updates in place instead of collapsing into
  a loader.
- `useAccounts` manages the list of prop accounts, account creation, and the active account filter.
- CORS is configured centrally in `SecurityConfiguration`, not per-controller.
- Client-side utilities handle P/L aggregation, win rates, and daily matrix formatting.

## Domain Model: How Money & Risk Are Calculated

**The trader inputs only what they observed** — ticker, direction, entry, exit, contracts.
Never a dollar multiplier. A 10-point move is $20 on MNQ and $200 on NQ;
that difference belongs in the server, not the trader's head.

`Instrument` (enum, `com.alphatracker.api.trade`) is the single source of truth for contract
economics — 12 contracts (ES/MES, NQ/MNQ, YM/MYM, RTY/M2K, CL/MCL, GC/MGC), each with a
`pointValue` (fixed CME spec). There is no commission/round-turn fee anywhere in the model
(removed in `eb3e212`).

```text
priceMove  = LONG ? exit - entry : entry - exit
profitLoss = round2(priceMove × pointValue × contracts)   ← stored as-is, what hits the prop account
```

Unknown tickers are **rejected**, never defaulted to a 1.0 multiplier.

### Win / Loss Definition (applies to all analytics)

- **Win:** `profitLoss > 0`. **Loss:** `profitLoss < 0`. **Neutral:** exactly `0` — back at the
  start point; it is neither a win nor a loss.
- `winRate = wins / (wins + losses)`; neutral results are left out of the denominator.
- **Neutral is never hidden.** Every summary also reports `neutral` (count) and `neutralRate`
  (`neutral / tradeCount`). Many neutral trades or days is a warning sign — profit being given up,
  or a system that isn't working — so the assistant must be able to see it. The API returns the
  rates only; the "that's a red flag" interpretation lives in the assistant's prompt, not in Java.
- **Days use the same rule:** a day's trades are netted (summed in whole cents), then the day is a
  win day (`> 0`), loss day (`< 0`) or **neutral day** (`== 0`). `neutralDayRate = neutralDays / tradingDays`.
- **Discipline:** the `followedPlan` flag is the focus, not loss streaks. Trades are split into
  followed / broke-plan / unspecified (null flag, legacy rows) with the same stats on each side.

---

## Key Files

**Backend** (`src/main/java/com/alphatracker/api/`)

- `account/Account.java` — prop entity: starting/current balance, maxDrawdown, profitTarget, `drawdownMode`, `trailingStopsAtBalance`, `isPrimary`
- `account/AccountService.java` — account CRUD and user boundary checks
- `account/AccountController.java` — REST endpoints (`/api/v1/accounts`)
- `account/DrawdownMode.java` — enum (`END_OF_DAY`, `PER_TRADE_CLOSE`)
- `trade/Instrument.java` — contract economics (point values only)
- `trade/Trade.java` — execution entity linked to `User` and optionally `Account`
- `trade/TradeRequest.java` — POST write contract; accepts optional `accountId`
- `trade/TradeService.java` — validates inputs, derives money, updates live account balance, handles primary account default
- `exception/GlobalExceptionHandler.java` — `IllegalArgumentException` → 400, `SecurityException` → 403
- `security/SecurityConfiguration.java` — JWT chain + central CORS policy

**Frontend** (`alphatracker-frontend/src/`)

- `hooks/useTrades.ts` — account-scoped trade queries, refetch, delete
- `hooks/useAccounts.ts` — fetches accounts, manages selected account state and creation
- `components/dashboard/AccountSelector.tsx` — dropdown to toggle between accounts and aggregate portfolio
- `components/dashboard/DrawdownGauge.tsx` — visual buffer gauge tracking distance to drawdown floor
- `components/dashboard/CreateAccountModal.tsx` — dialog to create new prop accounts
- `components/TradeEntryModal.tsx` — log trade modal with account selector
- `utils/pnlAggregations.ts` — `computeWinRate`, `computeAvgWinLoss`, `groupTradesByDay`

---

## Sprint Roadmap & Progress

### Sprint 1 — Core MVP & Trade Logging (COMPLETE)

- [x] Auth flow and JWT persistence
- [x] Calendar P/L matrix & Equity Curve visualization
- [x] Trade Entry Modal (`TradeEntryModal.tsx`)
- [x] Server-side P/L derivation via `Instrument` enum
- [x] `TradeRequest` DTO + global exception handling
- [x] Central CORS configuration
- [x] `useTrades` refetch & safe mount handling
- [x] Trade Execution Table View (with delete)
- [x] Win Rate Ring SVG arc
- [x] Calendar day drill-down

### Sprint 2 — Accounts, Prop Firm Management & Risk (Wrapping Up)

- [x] Unit tests for `Instrument`, `logTrade`, and multi-account sync (`TradeServiceTest`, `AccountServiceTest`)
- [x] `Account` entity with `drawdownMode`, `trailingStopsAtBalance`, `isPrimary`
- [x] `user_id` FK on Account; `account_id` FK on Trade
- [x] `GET /api/v1/accounts` & `POST /api/v1/accounts`
- [x] Account-scoped trade queries (`GET /api/v1/trades?accountId={id}`)
- [x] Account balance auto-sync on trade creation and rollback on deletion
- [x] Frontend `useAccounts` hook, `AccountSelector`, and `CreateAccountModal`
- [x] `TradeEntryModal` account selection
- [x] Basic `DrawdownGauge` component
- [x] **UI Theme Alignment:** Restyle `DrawdownGauge` and `AccountSelector` to match the light-mode card palette (Issue #1)
- [x] **Drawdown Engine Implementation:** Implement `END_OF_DAY` vs `PER_TRADE_CLOSE` + `trailingStopsAtBalance` calculations (Issue #2)
- [x] **Account Backfill & Primary Account:** UI to set primary account and default unassigned trades to primary

### Sprint 3 — Rich Media & Journal Attachments (Superseded by Sprint 3.5)

- [x] `StorageService` interface + local filesystem adapter (`LocalFileStorageService`). S3 adapter deliberately deferred to Sprint 5 deploy rather than built against no real bucket.
- [x] `TradeAttachment` entity (`id`, `trade_id`, `storageKey`, `attachmentType`, `contentType`, `sizeBytes`, `caption`, `uploadedAt`) + `TradeAttachmentService` (upload validation, ownership-checked retrieval/delete, cascade cleanup on trade delete) + `TradeAttachmentController`.
- [x] Drag-and-drop & clipboard paste (`Cmd+V`) screenshot upload in `TradeEntryModal`, via a reusable `AttachmentDropzone` component.
- [x] Ownership checks on attachment retrieval — re-checked on every request (`findByIdAndTrade_User_Id`), not presigned URLs (no S3 adapter yet; see above).

### Sprint 3.5 — Day-Scoped Journal Rework

**Backend (COMPLETE — reviewed & smoke-tested against a live server)**

- [x] `JournalEntry` entity — one row per `(user, entryDate)`: `notes` (day-level reflection), `htfBias`. Auto-created on first write only (a `GET` on an untouched day returns a clean empty bundle with no DB row).
- [x] `TradeAttachment` → `JournalAttachment`; FK repointed from `trade_id` to `journal_entry_id`. Removed the now-unneeded attachment cascade-delete logic from `TradeService.deleteTrade`.
- [x] `GET/PUT /api/v1/journal/{date}` — day bundle (notes + HTF bias + that day's trades) / upsert notes+bias.
- [x] `POST /api/v1/journal/{date}/attachments`, `DELETE /api/v1/journal-attachments/{id}` (+ `GET .../file` for ownership-checked retrieval).
- [x] `Trade.executionRating` (1-5) and `Trade.setupTags` (`@ElementCollection<String>`) — trade-level.
- [x] `PATCH /api/v1/trades/{id}` — first update endpoint for trades at all; edits `executionRating` and `setupTags` only. `Trade.notes` (original trade-entry field) left as-is, distinct from day-level journal notes.
- [x] **Bug found & fixed in review:** the day-range query for "trades on this date" used JPA's `Between` (inclusive both ends), so a trade logged at exactly midnight the next day would double-count into today's bundle too. Fixed with explicit `GreaterThanEqual`/`LessThan` bounds; verified live against a trade logged at exactly `T+1 00:00:00`.
- [x] **Security bug found & fixed in review:** `Trade.user` embeds a full `User` object with no DTO in between, and `User.password` (the bcrypt hash) had no `@JsonIgnore` — so `GET /trades` _and_ the new `GET /journal/{date}` were both leaking password hashes in every trade response. Fixed at the source (`@JsonIgnore` on `User.password`), protecting every current and future endpoint that ever serializes a `Trade`.
- [x] 65 backend tests passing (7 new test classes covering `JournalEntry`, `JournalAttachment`, `JournalController` at repository/service/controller layers).

**Frontend **

- [x] `AttachmentDropzone`/`AttachmentThumbnail` get a light/dark `theme` prop — fixes both the color bug (hardcoded dark styling on light cards) and a broken retrieval path caught in review (`/attachments/` → `/journal-attachments/`).
- [x] `/journal` reworked into a calendar-style browse view (`JournalCalendar`, reusing the dashboard's `CalendarDayCell` extended with `alwaysInteractive` so empty days are still clickable), replacing the flat card-list first pass.
- [x] Day panel (`JournalDayPanel`, opens on clicking a day, or via `/journal?date=...` deep link): day notes + HTF bias, a shared screenshot gallery/dropzone, and that day's trades listed via `JournalTradeCard` (inline execution rating stars + `TagInput` setup tag chips).
- [x] `DayDetailModal` (dashboard) stays exactly as before — read-only trades/P&L/win-rate/discipline stats — plus a pencil icon that deep-links to `/journal?date=...` for editing.
- [x] Retired `JournalEntryCard`/`PendingAttachmentThumbnail`/old `useAttachments.ts` (trade-scoped, superseded by day-scoped `useJournalDay`).
- [x] `TradeEntryModal` simplified — screenshots upload immediately against the trade's date (day-scoped, no `tradeId` needed), removing the staged-file/retry/`createdTradeId` mechanism that only existed for the old trade-scoped model.
- [x] `AttachmentLightbox` — click a thumbnail to view full-size, reusing the already-fetched blob (no extra request). Native pinch/`Cmd+/-` zoom works on it; a custom in-app zoom control was considered and deliberately skipped.
- [x] Click-outside-to-close on `CreateAccountModal` and `TradeEntryModal`, matching the pattern already used by `DayDetailModal`/`JournalDayPanel`.

### Sprint 4 — Production Deployment (Reprioritized ahead of Monetization)

**Why now, out of order:** a live link matters more for a resume than Stripe billing
does. This also supersedes Sprint 5's original "Docker + AWS deployment
(ECS/Fargate + RDS)" bullet with a lighter, cheaper stack better suited to a
portfolio project's actual traffic — ECS/Fargate/RDS remains a valid future
upgrade if this ever needs to scale, just not the starting point.

**Target stack:** Vercel (frontend) · Railway, ~$5/mo (backend, always-on — a
resume link can't afford a 30-60s cold-start on Render's free tier) · Neon
(managed Postgres, persistent free tier) · **AWS S3** (screenshot storage —
chosen deliberately for the resume line, not because it's the cheapest option;
realistic cost at this project's scale is a few cents/month, mitigated further
by an AWS Budget alert).

**Blockers that must be resolved first (not polish — the app cannot run on any
of these platforms without them):**

- [x] **Environment-variable-based backend config.** `datasource.url/username/password`,
      `application.security.jwt.secret-key`, `application.storage.local.base-path`, and
      `application.cors.allowed-origins` are all `${VAR:local-dev-default}` now — local
      dev behavior is unchanged, Railway will override all of them without touching this file.
- [x] **S3 `StorageService` adapter.** The only remaining hard blocker: local disk
      (`storage.local.base-path`) does not survive a container restart/redeploy on
      any of these platforms. This is the deferred Sprint 3 work, now required
      rather than optional.
- [x] **Frontend API base URL.** `apiClient.ts`'s `baseURL` is hardcoded to
      `http://localhost:8080/api/v1` - needs a build-time env var
      (`VITE_API_BASE_URL`) so the Vercel build points at the deployed backend.
- [x] **CORS origin now configurable** via `CORS_ALLOWED_ORIGINS` — mechanism is
      done; the real value (Vercel's origin) gets set once that URL exists.

**Sequencing:**

1. [x] Dockerize the backend (multi-stage build: Maven build stage → slim JRE
       runtime) + move config to env vars. Built and smoke-tested locally: the
       image starts, connects via an env-var-overridden `DATABASE_URL` (proving
       the override mechanism actually works, not just compiles), and a live
       `POST /api/v1/auth/register` through the container round-tripped successfully.
2. [x] Built `S3StorageService` (same key scheme/filename-sanitization as the
       local adapter, extracted into a shared `StorageKeys` helper), toggled via
       `STORAGE_PROVIDER` (`local` default / `s3`) so only one `StorageService`
       bean is ever active — `@ConditionalOnProperty` on both adapters. 7 new
       unit tests (mocked `S3Client`). Bucket + scoped IAM user + Budget alert
       set up in the AWS console. **Live-tested twice against the real bucket**
       (`alphatracker-attachments-josiah`, us-east-2) — once via `mvnw
spring-boot:run`, once through the actual Docker image (the real
       deploy path): store → retrieve (byte-identical) → delete, through the
       running app, not mocks. Caught one real bug in the process: a missing
       `S3_ACCESS_KEY_ID` correctly fails the container at startup rather
       than booting into a broken storage adapter (confirms the no-default
       config choice in application.yml does what it's meant to).
3. [x] Stood up the database on Neon (project `alphatracker`, `neondb`,
       pooled connection, us-east-2). Live-tested: `mvnw spring-boot:run`
       pointed at it via `DATABASE_URL`/`DATABASE_USERNAME`/`DATABASE_PASSWORD`
       env vars, Hibernate auto-created the full schema on a fresh empty
       database, register + log-trade + refetch round-tripped correctly.
4. [x] Deployed the backend container to Railway (`alphatrackerv2-production.up.railway.app`),
       9 env vars set (DB, storage, JWT secret, provider), building straight
       from the repo's `Dockerfile`. **Two real bugs found and fixed getting
       here:**
   - `GlobalExceptionHandler` converted every exception straight into a
     client-facing JSON message and never logged the original error
     server-side — a 500 on Railway was completely unobservable. Added
     `log.error(..., ex)` to the `StorageException` handler (the one that
     actually needed it to debug this).
   - `S3_BUCKET` had literal quote characters in the Railway variable
     value (`"alphatracker-attachments-josiah"` instead of
     `alphatracker-attachments-josiah`) — copied from a terminal `export`
     example where the quotes were bash syntax, not part of the value.
5. [x] `apiClient.ts` reads `VITE_API_BASE_URL` (baked in at build time, falls
       back to localhost for `npm run dev`) — verified the built bundle
       actually contains the right URL for both the set and unset cases
       before ever deploying it. Deployed to Vercel
       (`alpha-tracker-journal.vercel.app`), root directory set to
       `alphatracker-frontend` for the monorepo. `CORS_ALLOWED_ORIGINS` set
       on Railway to the real Vercel origin — verified the preflight
       response flip from a bare 403 to a proper `access-control-allow-origin`
       header before testing through the browser. Full manual click-through
       (register, log a trade, upload a screenshot, view the journal)
       confirmed working by hand.
6. [x] **The actual point of this whole sprint, proven live:** uploaded a
       screenshot to the deployed backend, triggered a full Railway
       redeploy (container restarts from scratch), and confirmed the
       screenshot was still retrievable afterward, byte-identical. This is
       exactly what local disk storage could never have survived.
7. [x] Add the live link to the README and resume.

**Live URLs:** frontend `https://alpha-tracker-journal.vercel.app` · backend
`https://alphatrackerv2-production.up.railway.app`

Keeping `ddl-auto: update` for schema management rather than introducing
Flyway/Liquibase - reasonable for this project's scope, revisit only if a real
migration history ever becomes necessary.

### Sprint 5 — Monetization & Billing (Stripe Integration)

- [ ] Stripe customer creation on user registration
- [ ] Stripe Checkout session endpoint for subscription tiers (e.g. Free vs Pro Trader)
- [ ] Stripe Webhook handler (`/api/v1/webhooks/stripe`) for subscription lifecycle events (`customer.subscription.created`, `invoice.payment_succeeded`, etc.)
- [ ] Backend subscription tier security guardrails / access gates (e.g. max active accounts limit)
- [ ] Frontend billing settings & subscription status badge

### Sprint 6 Analytics and Discipline Streaks

- [ ] Real Discipline Score & Streaks engine
- [ ] Peak-to-trough drawdown curve & session analytics (NY AM vs PM vs London)

---

### Sprint 4.5 — AI Trade Review Assistant, Phase 1 (Java groundwork)

Architecture (decided): separate Python FastAPI service (`/alphatracker-assistant`) calling Gemini
with tool calling; each tool is a read-only GET to this Spring API forwarding the user's JWT.
The model never does arithmetic and Python never touches the DB. Later phases: hand-written tool
loop, 50-question eval set, pgvector over journal notes, React chat panel.

- [x] `TradeResponse` DTO (drops embedded `User`, adds `accountId`)
- [x] `TradeStats` pure calculator (win/loss/neutral rule above)
- [x] Repository range queries (`>=` start, `<` next-day start — never `Between` on `tradeDate`)
- [x] `GET /api/v1/analytics/summary` (trade stats + day stats), `/breakdown?by=instrument|setup|rating`,
      `/discipline` (`followedPlan` split). Loss-streak stat dropped — not relevant to a discipline journal.
      Filters: `?accountId=&from=&to=` (dates inclusive). A non-owned `accountId` is a 403.
- [x] `GET /api/v1/journal?from=&to=` — `{date, notes, htfBias}` rows, both dates inclusive and required,
      max 366 days, empty (screenshot-only) days omitted
- [x] Frontend aligned with the win / loss / neutral rule: shared `utils/tradeOutcome.ts` (`classifyPnl`,
      cents-exact `sumPnl`), win rate excludes neutral from its denominator, neutral trades/days shown (amber
      calendar tint, "$0", "N neutral" under the ring), averages are `—` instead of `$0` when there's no data.

#### Phase 2 — Python assistant service (`/alphatracker-assistant`) — IN PROGRESS

**Decided:**

- Frontend calls the Python service **directly** with the user's JWT; Python forwards it unchanged to Spring
  and never validates JWTs itself (Spring does). Needs CORS on the Python side.
- **Stateless:** the client sends the last N turns of `history`; Python stores nothing and never touches the DB.
- No streaming in Phase 2 (easy to add for the Phase 4 chat panel).
- Gemini model ID comes from `GEMINI_MODEL` (flash-class for tool calling; **not chosen yet** — verify the
  current SDK/model IDs at step 4). Eval set (Phase 3) can compare models.
- Config is env-only: `GEMINI_API_KEY`, `GEMINI_MODEL`, `SPRING_API_BASE_URL`, `TIMEZONE` (default
  `America/Los_Angeles`, defines "today" — **confirm**), `CORS_ALLOWED_ORIGINS`. Locally these live in
  `alphatracker-assistant/.env` (git-ignored; no `.env.example` by choice); on Railway they are service variables.
  Blank values count as unset. The Gemini key is never pasted into chat or logged.

**Design:**

- **Tools** (each a read-only GET): `list_accounts` (so "my Apex account" resolves to an id; also surfaces
  balances/drawdown), `get_summary`, `get_breakdown(by=instrument|setup|rating)`, `get_discipline`,
  `get_journal_entries`.
- **Period handling:** tools take a `period` enum (`today`, `this_week`, `last_week`, `this_month`, `last_month`,
  `last_30_days`, `all_time`) resolved **deterministically in Python**, or explicit `from`/`to`. The model never
  does date arithmetic.
- **Tool loop (hand-written):** preflight `GET /accounts` (validates the JWT before spending a Gemini call, and
  preloads the account list) → call model with tool schemas → run requested tools → append results → repeat;
  stop on plain text or after 6 iterations. Tool errors (400/403 with a JSON `message`) go back to the model as
  structured errors; an auth failure ends the request with a 401.
- **Two kinds of 403 from Spring:** empty body = unauthenticated/expired token (stop, tell the user to log in);
  JSON `message` = ownership error (return to the model to explain).
- **Safety:** GET-only client with a path allowlist; Pydantic-validated tool args; response size cap with a
  truncation flag; JWT never in logs, traces, or error text; same tool call repeated in one loop is cut off.
- **`POST /chat`:** request `{message, history[]}` with `Authorization: Bearer`; response
  `{answer, tool_calls:[{name,args,status}]}` (the trace is for debugging, a future "show sources", and eval scoring).
- **System prompt rules:** use only tool-returned numbers; win/loss/neutral as defined above; high neutral
  rate/days is a red flag; setup counts overlap; `null` means "no data", not zero; warn on small samples; no
  financial advice; say plainly what it cannot answer (e.g. loss-streak questions — that stat was dropped).
- Trading notes are sent to Google's API: fine for a single-user tool, needs a consent decision before other
  users or Stripe tiers exist. Sprint 5 may gate the assistant as a Pro feature.

**Checklist (one numbered item at a time, review after each):**

How the pieces connect: `POST /chat` → **Agent** (loop) ⇄ **Gemini** (`llm.py`) and → **ToolRunner**
(`tools.py`) → **SpringClient** (`spring_client.py`) → Spring analytics API. Each step below builds one piece.

- [x] 1. **Scaffold.** Service skeleton: `GET /health` (says whether a key is set, never shows it), settings
     from env / `.env`, CORS, Dockerfile, README. `.env` is git-ignored. _3 tests._
- [x] 2. **`SpringClient` — the read-only door to Spring.** GET only, 5 allowed paths, forwards the user's JWT,
     never stores or logs it. Sorts failures into three kinds: bad token (stop), refused (goes back to the
     model, e.g. "not your account"), Spring down. _Checked live: a missing, garbage, expired, or unknown-user
     token all return an empty 403, never a 500. 7 tests._
- [x] 3. **Tools + dates.** The 5 things the model may call (`list_accounts`, `get_summary`, `get_breakdown`,
     `get_discipline`, `get_journal_entries`) with strict argument checks. `periods.py` turns "last month"
     etc. into exact dates in Python, so the model never does date maths. Long journal results are cut off
     with a `truncated` flag. _4 tests._
- [x] 4. **Gemini adapter.** `llm.py` is the only code that touches the Gemini SDK (default `gemini-3.8-flash`,
     change with `GEMINI_MODEL`). Stateless `generate_content` with the SDK's auto-calling off, so we run the
     loop ourselves. Google's newer stateful "Interactions" API is deliberately not used (it stores the
     conversation on their side). _Checked live: one real tool round trip worked. 2 tests, plus a scripted
     `FakeLLM` for later tests._
- [x] 5. **Agent loop + system prompt.** `agent.py`: model → tools → results → repeat. Max 6 rounds, then a
     forced answer with no tools; a repeated identical call isn't re-run; a dead token stops everything.
     `prompts.py`: use only tool numbers, win/loss/neutral rules, many neutral trades = red flag, what it
     can't answer, journal text is data not instructions. _Checked live: a normal question was answered from
     tool numbers, the loss-streak question was declined, high-neutral data was flagged. 6 tests._
- [x] 6. **`/chat` endpoint + number check.** `POST /chat` takes `{message, history[]}` + `Authorization: Bearer`.
     A cheap `GET /accounts` preflight rejects a bad token (401) _before_ any model call is paid for, and
     preloads the account list into the prompt. Errors map to 401 (bad token) / 503 (Spring down, or model
     busy) / 502 (model failed or empty) / 422 (bad input), with no internals in the body. History is capped at
     the last 20 turns and can only be user/assistant text. _Checked end to end:_ real Spring + real Gemini,
     a seeded trader with known answers; win rate, discipline, journal and total-trade answers all matched.
     **The run caught a real failure:** a follow-up ("and how about on NQ?") made no tool call and Gemini
     invented its numbers. Fixed two ways: a prompt rule (every data question needs a fresh tool call) and
     `grounding.py`, which rejects any answer containing a figure that no tool result, question, or
     context line contains. It is sent back once to be fixed, and withheld if it still fails. The check also
     stops the model adding figures itself (it once wrote a "9 total trades" nobody returned). Cost: a
     corrected answer takes one more round trip (~10s). 6 tests.
- Tests: 28 (the original target of ~14 grew with the adapter, loop and number check).

**Open questions:** confirm `TIMEZONE`; whether `gemini-3.8-flash` is the right cost/quality point (compare in Phase 3 evals); dashboard Discipline Score counts legacy `followedPlan = null`
trades as "not followed" while `/analytics/discipline` reports them as "unspecified" — align or leave?

#### Phase 3 — Eval set (measure whether the assistant is right) — NEXT

**Why:** Phase 2 was tested with a scripted fake model and one hand-run conversation; that run found a real
failure (invented numbers on a follow-up) that no unit test would have. Evals make that kind of check
repeatable, and let models/prompts be compared with numbers instead of impressions.

**How it works (and why):**

- **Independent answer key.** A fixed, hand-designed dataset (~48 trades, 2 accounts, tags, ratings, neutral
  trades/days, journal notes). `evals/oracle.py` computes the correct answers in plain Python from the dataset.
  (40 trades in the end, not the ~48 first planned.)
  It is deliberately NOT built on the Java API or `app/*`: if the answer key came from Spring, a bug in Spring
  would be "correct". Checking oracle == Spring on the same data also cross-tests the Java stats.
- **Real stack, fixed clock.** The harness seeds a dedicated eval trader into local Spring through the public
  endpoints, then runs the real `Agent` + real Gemini + real `SpringClient` in-process, with "today" pinned to a
  constant so "this month" always means the same dates. It refuses to run against anything but localhost
  (never seed the production DB).
- **50 questions, ~9 categories:** summary by period/account, breakdowns (instrument/setup/rating), discipline,
  journal, neutral handling, accounts, follow-ups with history, out-of-scope (must decline), adversarial
  (injection in a journal note, asking for a derived stat, someone else's account).
- **Scored by code, not by another model.** Each question lists the tools it must call, the figures that must
  appear, figures that must NOT appear (catches the right-number-wrong-context failure the grounding check
  can't), and whether it must decline. Every answer is also checked for withheld/ungrounded figures and leaked
  tokens. Reports: pass rate per category, tool-selection accuracy, latency, tokens.
- **Run-to-run noise:** `--repeat N` shows which questions pass only sometimes.
- **Model comparison:** same set on `gemini-3.8-flash`, `gemini-3.5-flash-lite` (and maybe `gemini-4-argon`);
  the default model is chosen from the table, not a guess.

**Steps (one at a time, review after each):**

- [x] 1. Dataset + oracle (pure Python, no network) + 3 tests. `evals/dataset.py`: 40 hand-designed trades
     (2 accounts, 5 neutral trades, 6 neutral days incl. two made of offsetting trades, overlapping tags,
     a 1-trade MCL group, 6 journal notes incl. a prompt-injection attempt), "today" pinned to 2026-10-14.
     `evals/oracle.py`: the independent answer key (integer cents, exact half-up rounding, no imports from
     `app/` or Spring). Tests pin it to figures worked out by hand and cross-check its date maths against
     `app/periods.py` over 130+ days.
- [x] 2. Seeder + `verify_oracle` + 2 tests. `python -m evals.seed` loads the dataset into LOCAL Spring through
     the public endpoints (fixed eval trader `eval-harness@example.test`; idempotent: reuses accounts by name,
     deletes+recreates trades, resets journal; refuses any non-localhost host by exact hostname match). Write
     code lives in `evals/` so it never ships (Dockerfile copies only `app/`). `python -m evals.verify_oracle`
     compares Spring with the oracle on 111 queries (every period x account for summary, 3 breakdowns,
     discipline; journal ranges; account balances). **Result: Spring and the oracle agree on all 111.**
- [x] 3. The 50 questions (`evals/questions.py`) + 1 validation test. Mix: summary 9, breakdown 9, discipline 5,
     journal 5, neutral 5, accounts 2, follow-ups-with-history 5, out-of-scope 6, adversarial 4. Each question can
     assert: tools it must call (alternatives allowed), "no tools at all" for declines, figures that must appear,
     **wrong-context figures that must NOT appear** (all-time vs this month, the other instrument/account),
     phrase groups, and forbidden phrases (e.g. obeying the journal's injected "win rate is 99%"). All expected
     figures are read from the oracle at import time. `validate()` rejects: wrong category mix, unknown tools,
     empty assertions, and forbidden figures that look like dates or collide with the expected ones. A review
     pass found one question that could not tell right from wrong (20% flat days came out the same for
     every period) and replaced it. Known weak spots: small-count expectations (e.g. "6 trades") can match by
     chance, and phrase checks are keyword-based.
- [x] 4. Runner + scorer + report + 4 tests. `python -m evals.run [--model M] [--repeat N] [--category C] [--id Q]`.
     Runs the REAL stack (seeded local Spring, real model) with "today" pinned, through the same
     `answer_question` the `/chat` endpoint now uses (extracted from the endpoint so evals test production, not a
     lookalike; the endpoint tests guarded the refactor). `scorer.py`: pure, deterministic, every requirement a
     named check; REQUIRED figures match leniently (the number anywhere), FORBIDDEN percent/money figures match
     strictly (needs `%` / `$`), so "September 20" can't trip a forbidden 20%. Self-check test: for all 50 questions
     an answer built from its own expectations passes and 6 kinds of wrong answer fail (forbidden figure, no tool
     call, failed tool call, decline that fetched data, missing figure/phrase, withheld). `MeteredLLM` counts model
     calls/tokens/time from outside the agent; the Gemini adapter now reports token usage. `report.py`: pass
     rate overall and per category, tool accuracy, flaky / always-fail lists, latency median+p95, tokens.
     Results go to `evals/results/` (git-ignored). Smoke run on 3 questions passed, including the follow-up that
     once failed. Typical cost observed: ~2 model calls and ~5k tokens in / 0.5k out per question, 4-7s.
- [x] 5. Baseline on `gemini-3.8-flash` + fixes. Final run: 50 questions x 3 attempts = 150 graded answers.
     **148/150 passed as first graded (98.7%); 150/150 once two scorer mistakes were corrected and the saved
     answers re-graded offline with `python -m evals.rescore <json>` (it lists every verdict it flips; only those
     two changed, none pass->fail).** Tool accuracy 100%, 0 answers withheld, 0 run errors.
     _Cost/speed:_ median 9.0s, p95 17.9s (16 of 150 over 15s, max 36s; the slow ones are answers the grounding
     guard sent back for a second try); ~2 model calls and ~6,850 tokens per answer (5,970 in / 870 out; ~1.03M
     tokens for the 150), most of the input being the fixed prompt + tool schemas.
     _What the runs found (in order of importance):_
     (1) **Model flaws no figures-check could see:** answers containing LaTeX (`$$\frac{..}$$`), which a chat panel
     renders as raw symbols -> prompt rule + a `plain_text` check on every question. A drafted "Journal Draft" with an
     invented "Key Takeaway" -> prompt rule: the journal is the trader's own words, the assistant is read-only and
     never writes reflections for them (product decision, reversible).
     (2) **The grounding guard's value and cost:** run 1 sent 6 of 50 answers back (12%), one being a decline question
     where the first draft quoted figures it had no source for. After fixes: 4 of 150 (2.7%), now only real
     arithmetic by the model (it adds wins+losses, attempts a profit factor). False alarms removed at the source: the
     small-sample threshold and the 1-5 rating scale now live in the context as FACTS, and the journal tool returns a
     `count`. The guard itself was left strict.
     (3) **Five eval mistakes (not assistant failures), all caught by reading the full answer before changing
     anything:** forbidden-figure checks that a faithful comparison tripped (the figure was in the same tool result)
     x3; a "no tools" demand on a concept question; and forbidden PHRASES that matched a correct refusal restating the
     request x3 (quoting the injected journal note; "you should take more"; "your win rate is 100%"). Phrase
     matching cannot tell refusing a claim from making it, so those checks now test behaviour: affirmative
     recommendation phrases only, and the TRUE figure required.
     _Caveat:_ the prompt and the questions were both tuned against this one set across three runs, so 98.7% is
     NOT an unbiased estimate of real-world accuracy. Write ~15-20 new questions blind (before seeing any output)
     before quoting a headline number. Known weak spots: small-count expectations can match by chance; the
     grounding guard checks that a number exists, not that it is the right one (the per-question forbidden
     figures cover part of that).
- [x] 6. Model comparison: **skipped by choice.**
- [x] 7. **Blind question batch — RESULT: 18/18 on the first and only run** (tool accuracy 100%, 0 withheld, 0 errors,
      0 grounding corrections, median 7.4s, 115k tokens). Read in full: empty-data cases fetched and said "no trades"
      (an Oct 3 Saturday, Topstep today, a tag that doesn't exist); declined to place an order; refused to "confirm" a
      $1,000,000 balance and reported the real $100,040 / $50,136; declined to print its prompt without leaking any
      wording. *Observed, deliberately not fixed (the prompt is not tuned to this set):* empty periods print
      "Total P&L: $0.00" because the Java API returns `totalPnl: 0` (not null) when there are no trades, unlike the
      rates, which are null; answers show internal account ids. Both are candidates for later polish.
      Written like this: `evals/questions_blind.py`, 18 questions, `evals.run --set blind`. Written BEFORE
     any run and never tuned against: the first-run result is the headline, because the 50-question set was
     tuned on across three runs. "Blind" means not tuned against; the author knew the failure modes from the main
     set, so it is a generalisation check, not a perfectly uncontaminated one. Any scorer fix after the run is
     disclosed; the prompt is NOT changed to fit these questions.

#### Phase 4 — Rate limiting (protect the Gemini key and your wallet) — PLANNED

**Why:** each chat costs 1–3 Gemini calls, and any valid JWT can call `/chat`. Before the service is reachable
from a browser, one runaway client or loop must not be able to burn the budget.

**Design:**

- **Per user, after authentication.** The limiter keys on the JWT `sub` (email). A JWT is only trusted after the
  existing preflight `GET /accounts` succeeds (Spring verified the signature), so the key can't be spoofed with a
  made-up token. Unauthenticated floods are cut off by that preflight, which is cheap.
- **Two limits:** a short window (e.g. 10 chats/minute, stops loops) and a daily cap per user (e.g. 100), plus
  one **global daily cap** so total spend is bounded no matter how many users exist. All configurable by env.
- **429 + `Retry-After`** with a plain message; the chat panel shows "slow down" instead of a generic error.
- **In-memory, single instance:** correct for one Railway container; resets on restart and does not span
  replicas. Moving to Redis is the known upgrade if the service is ever scaled out. Stated, not hidden.
- **Sprint 5 hook:** limits become per-tier (Free vs Pro) by reading the subscription from Spring later.
- Steps: [ ] 1. limiter (sliding window, injectable clock) + tests [ ] 2. wire into `/chat` before the model
  call [ ] 3. env settings + README [ ] 4. live check that the 11th request in a minute gets a 429.

#### Phase 5 — React chat panel + deployment — PLANNED

**Goal:** use the assistant from the app. A slide-over panel on the dashboard/journal, opened from the sidebar.

**Design:**

- **Files:** `components/assistant/ChatPanel.tsx`, `hooks/useAssistant.ts` (owns messages, loading, errors),
  `api/assistant.ts` (own axios instance pointed at `VITE_ASSISTANT_API_URL`, reusing the existing JWT).
- **Conversation lives in the browser** (the service is stateless): the last 20 turns are sent with each
  message; "Clear" resets it. Kept in memory / `sessionStorage`, not `localStorage` (trading notes are private).
- **Answers render as Markdown** (the model emits bold/lists/headings) via `react-markdown`, with raw HTML
  disabled so model/journal text can never inject markup.
- **Shows its work:** a collapsed "Used: breakdown by instrument, this month" line per answer, from the
  `tool_calls` the service already returns.
- **Honest waiting and errors:** answers take 4–18 s (no streaming), so a "thinking…" state; 401 → prompts
  re-login, 429 → "slow down" with the wait time, 502/503 → "assistant unavailable, try again".
- **Starter questions** as chips so the first use is obvious; amber neutral styling stays consistent with the rest.
- **Deployment:** assistant service as a second Railway service (root dir `alphatracker-assistant`, variables
  `GEMINI_API_KEY`, `SPRING_API_BASE_URL`, `CORS_ALLOWED_ORIGINS`=the Vercel origin); Vercel gets
  `VITE_ASSISTANT_API_URL`. Build the Dockerfile locally first (never run yet).
- **Needs first:** Phase 4 (rate limiting) and the `TIMEZONE` decision.
- Steps: [ ] 1. api + hook [ ] 2. panel UI + Markdown [ ] 3. error/loading states [ ] 4. sidebar entry +
  starter chips [ ] 5. build Docker image, deploy service, set CORS + env vars [ ] 6. live click-through.

#### Phase 6 — pgvector search over journal notes — LATER (not scheduled)

Semantic search over the trader's journal ("when did I write about revenge trading?"). Needs embeddings,
a vector column/extension on Neon, and a new read-only tool. Design when Phases 3–5 are done.

### Backlog — Accounts Lifecycle & Management Page (not scheduled)

Raised while reviewing Sprint 3.5: the sidebar's "Accounts" button currently just pops `CreateAccountModal` directly — there's no page to browse, manage, or retire accounts. Requirements as discussed:

- See all accounts created, grouped (Eval / Funded / Failed).
- Delete an account.
- Mark an evaluation account **Passed**, prompting "make your funded account now" — this should create a _new_, separate `Account` row (eval and funded accounts have different starting balances/drawdown rules at most firms), not mutate the eval account's `accountType` in place, so each phase's trades/stats stay cleanly separated by `account_id` the way they already do.
- Mark an account **Failed** (blew the drawdown, dropped below the buffer, etc.).

Design sketch for when this gets picked up:

- `Account.status` enum (`ACTIVE` / `PASSED` / `FAILED`), replacing the current plain `active` boolean — "passed" and "failed" are both "inactive" but read very differently in the UI.
- `Account.promotedToAccountId` — nullable, self-referencing FK, set when an eval is marked Passed and its funded account is created, so the UI can show the lineage ("this funded account came from Eval #1").
- `DELETE /api/v1/accounts/{id}` — does not exist yet at all.
- An endpoint to mark Passed + create the linked funded account in one step.
- A real `/accounts` route/page on the frontend, replacing the sidebar's direct-to-modal shortcut (though "Create Account" would still live there as an action).

## Drawdown Rules (Resolved)

**Trailing on closed balance:**

```text
closedBalance   = startingBalance + Σ profitLoss        (as already stored)
highWaterMark   = max(closedBalance) over account history
drawdownFloor   = highWaterMark - maxDrawdown
cushion         = closedBalance - drawdownFloor          ← the number that matters
```

- **`END_OF_DAY`**: High-water mark updates only from session close balance.
- **`PER_TRADE_CLOSE`**: High-water mark updates on every trade close.
- **`trailingStopsAtBalance`**: Floor stops ratcheting once it reaches the threshold:

```text
drawdownFloor = min(highWaterMark - maxDrawdown, trailingStopsAtBalance)
```
