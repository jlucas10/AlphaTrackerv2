# AlphaTracker — Project Context & Architecture

A futures trading journal built for a **prop-firm trader**. Not a retail brokerage app: the
trader is evaluated against firm rules (profit targets, trailing drawdown), and commissions
are a fixed function of the contract traded.

## Core Tech Stack

- **Backend:** Java 17, Spring Boot, Spring Security, JWT Auth, PostgreSQL (Hibernate/JPA)
- **Frontend:** React 19, TypeScript, Vite, Tailwind CSS v4, Axios, Recharts, date-fns

## Data Flow & Architecture

- Authentication via `/api/v1/auth/register` and `/api/v1/auth/authenticate` returning JWTs.
- `apiClient.ts` handles Bearer token injection for protected REST calls.
- `useTrades` owns the entire `/api/v1/trades` surface: fetch, `refetch`, and `deleteTrade`.
  It exposes `loading` (first load only) and `refreshing` (background) so the dashboard
  updates in place instead of collapsing into a loader.
- CORS is configured centrally in `SecurityConfiguration`, not per-controller.
- Client-side utilities handle P/L aggregation, win rates, and daily matrix formatting.

## Domain Model: how money is calculated

**The trader inputs only what they observed** — ticker, direction, entry, exit, contracts.
Never a commission, never a dollar multiplier. A 10-point move is $20 on MNQ and $200 on NQ;
that difference belongs in the server, not the trader's head.

`Instrument` (enum, `com.alphatracker.api.trade`) is the single source of truth for contract
economics — 12 contracts (ES/MES, NQ/MNQ, YM/MYM, RTY/M2K, CL/MCL, GC/MGC), each with a
`pointValue` (fixed CME spec) and a `roundTurnFee` per contract.

```
priceMove  = LONG ? exit - entry : entry - exit
gross      = priceMove × pointValue × contracts
commission = roundTurnFee × contracts
profitLoss = round2(gross - commission)     ← stored NET, what hits the prop account
```

Unknown tickers are **rejected**, never defaulted to a 1.0 multiplier — a silently wrong
multiplier would corrupt the calendar and equity curve permanently.

## Key Files

**Backend** (`src/main/java/com/alphatracker/api/`)

- `trade/Instrument.java` — contract economics (point values + round-turn fees)
- `trade/TradeRequest.java` — POST write contract; excludes `id`, `user`, `commission`, `profitLoss` by design
- `trade/TradeService.java` — `logTrade` validates inputs and derives all money
- `exception/GlobalExceptionHandler.java` — `IllegalArgumentException` → 400, `SecurityException` → 403, each with a JSON `message` the frontend reads
- `security/SecurityConfiguration.java` — JWT chain + central CORS policy

**Frontend** (`alphatracker-frontend/src/`)

- `hooks/useTrades.ts` — all trades API calls, refetch, delete
- `utils/pnlAggregations.ts` — `groupTradesByDay` (sums) and `groupTradeListsByDay` (keeps trades)
- `components/dashboard/TradeRow.tsx` — renders a `<tr>`, reused by `TradeTable` and `DayDetailModal`

## Sprint 1 — UI & Trade Logging COMPLETE

- [x] Auth flow and JWT persistence
- [x] Calendar P/L matrix & Equity Curve visualization
- [x] Trade Entry Modal (`TradeEntryModal.tsx`)
- [x] Server-side P/L derivation via `Instrument` enum
- [x] `TradeRequest` DTO + global exception handling
- [x] Central CORS configuration (fixed 403/preflight failures on all trade endpoints)
- [x] `useTrades` refetch — dashboard updates in place after logging
- [x] Trade Execution Table View (with delete)
- [x] Win Rate Ring — SVG arc that tracks the actual percentage
- [x] Calendar day drill-down — click a day to see its executions and notes
- [~] Account Filter Dropdown — **moved to Sprint 2** (blocked: no `Account` entity exists)

## Sprint 2 — Accounts & Drawdown (next)

Unblocks three currently-hardcoded values: `availableCapital`, the "No funded account
selected" banner, and Evaluation vs. Funded filtering.

**Backend**

- [x] Unit tests for `Instrument` + `logTrade` — do this FIRST, before Account work touches `logTrade`
- [x] `Account` entity: label, firm, type (`EVALUATION` / `FUNDED`), starting balance,
      profit target, max drawdown, `drawdownMode`, `isPrimary`
- [x] `user_id` FK on Account; `account_id` FK on Trade
- [x] `GET /api/v1/accounts`, `POST /api/v1/accounts`, account-scoped trade queries
- [x] Drawdown calculation service (see rules below)
- [x] Account-scoped trade queries — `GET /api/v1/trades?accountId={id}` integrated into `TradeController`
      and `TradeService`

**Frontend**

- [ ] **Drawdown tracker** — distance to the drawdown floor
- [ ] Account filter dropdown; scope calendar, curve, stats, and table to the selection
- [ ] Real available capital
- [ ] Set/change primary account

**Priority note:** build drawdown tracking before the dropdown. The filter is convenience;
blowing the trailing drawdown is what actually ends a prop account.

### Decision: drawdown rules (resolved)

**Trailing on closed balance.** The floor follows the account's high-water mark and never
moves down:

```
closedBalance   = startingBalance + Σ profitLoss        (net, as already stored)
highWaterMark   = max(closedBalance) over account history
drawdownFloor   = highWaterMark - maxDrawdown
cushion         = closedBalance - drawdownFloor          ← the number that matters
```

`drawdownMode` is a per-account enum because "closed balance" is ambiguous and firms differ:

- `END_OF_DAY` — the high-water mark only updates at session close (more forgiving)
- `PER_TRADE_CLOSE` — updates as each trade closes, so an intraday peak can raise the floor

Default to `END_OF_DAY`; both are computable from data already stored.

**Optional `trailingStopsAtBalance`** — many firms freeze the floor once it reaches a
threshold (often the starting balance), after which it stops trailing entirely. Nullable;
when set, `drawdownFloor = min(highWaterMark - maxDrawdown, trailingStopsAtBalance)`.

**Not modelled:** intraday _equity_ peak (unrealized). That would require capturing MAE/MFE
per trade, which is not currently recorded.

### Decision: account backfill (resolved)

- `Account.isPrimary` boolean; exactly one primary per user, changeable from the UI.
- On migration: create one account, mark it primary, assign all existing trades to it.
- `Trade.account_id` starts **nullable** (`ddl-auto: update` cannot add NOT NULL to a
  populated table), backfilled immediately, and treated as required in application code.
- New trades default to the primary account when the request omits one.

## Sprint 3a — Journal View & Media Attachments

- [ ] Journal view implementation (wire up `/journal` sidebar route)
- [ ] `StorageService` interface + **local filesystem** implementation
- [ ] `TradeAttachment` entity (`id`, `trade_id`, `storageKey`, `attachmentType`, `caption`)
- [ ] Attachment upload / serve endpoints with ownership checks
- [ ] Drag-and-drop / clipboard paste (`Cmd+V`) screenshot upload in `TradeEntryModal` and Journal view
- [ ] Structured trade reflection fields (see below)
- [ ] AWS S3 `StorageService` implementation — only when deploying

### Attachment design notes

- **Store `storageKey`, never a URL.** A public bucket for trade screenshots is not
  acceptable, so reads use presigned URLs — and those expire in minutes. Persist the key
  (`trades/42/entry-a1b2c3.png`) and generate a fresh URL per read.
- **`StorageService` abstraction first, S3 later.** The app runs on localhost today;
  local-filesystem storage delivers the whole feature with no AWS SDK, IAM credentials,
  bucket policy, or cost on the critical path. `TradeAttachment` is unchanged when the S3
  adapter lands — only the adapter differs.
- **Ownership check on every read.** Serving an attachment must verify the parent trade
  belongs to the requester, following the pattern in `TradeService.getTradeById`. Without it,
  anyone holding a URL reads another user's charts.
- **`attachmentType` is an enum** (`ENTRY`, `EXIT`, `HTF_CONTEXT`) — a typo'd string silently
  creates a category that never renders.
- Validate file type and max size on upload; screenshots are pasted, so expect PNG.

### Structured reflection fields

Free-text notes cannot be aggregated — "what's my win rate on this setup?" needs enumerated
data. Build these BEFORE the analytics that consume them.

- **HTF Bias** — enum (`BULLISH` / `BEARISH` / `NEUTRAL`)
- **Execution Rating** — int 1–5
- **Setup Model** — a user-managed tag entity, NOT a Java enum. The vocabulary will evolve,
  and an enum means a code change and redeploy per new setup.
- **Liquidity Target** — free text initially; promote to tags if patterns emerge.
- **Overlap warning:** Execution Rating and `followedPlan` measure adjacent things. Keep
  `followedPlan` as the binary rule-adherence flag feeding the Discipline Score; treat the
  rating as separate execution-quality data. They must not silently feed the same number.

## Sprint 3b — Analytics & Rule Tracking

- [ ] Real Discipline Score (derived from `followedPlan` across trades)
- [ ] Per-instrument breakdown (P/L by MNQ vs NQ vs ES)
- [ ] Time-of-day / session analysis (London vs NY AM vs PM)
- [ ] R-multiple & expectancy tracking
- [ ] Streaks + peak-to-trough drawdown curve
- [ ] Win rate colouring by expectancy rather than the current flat 50% threshold

### Analytics design notes

- **R-multiple needs the PLANNED stop, set at entry** — not where the trade actually exited,
  or R becomes circular. Add optional `stopLoss` to `Trade` / `TradeRequest`:
  `riskDollars = |entry - stopLoss| × pointValue × contracts`, then `R = profitLoss / riskDollars`.
  Consider a planned `target` too: it is the only way to measure cutting winners early, which
  is usually the more expensive habit.
- **Timezone landmine — settle before collecting months of data.** `Trade.tradeDate` is a
  `LocalDateTime` with NO zone. Sessions are defined in exchange time, so bucketing a zoneless
  timestamp only works while logging from one machine in one timezone, and breaks silently
  across DST. Decide: standardize on `America/New_York`, or store the zone alongside the
  timestamp. Retrofitting sessions onto ambiguous timestamps means never trusting the buckets.

## Backlog

- [ ] **Session expiry handling** — JWTs last 24h but `AuthContext` treats any stored token as
      valid, so you appear logged in with a dead token and every call 403s until you log out
      and back in. Fix: Axios response interceptor + clean 401 from the JWT filter.
- [ ] Edit trade (needs a new `PUT` endpoint + DTO; delete already exists)
- [ ] `@JsonIgnore` on `Trade.user` — every trade currently serializes email and role
- [ ] Lint: 6 errors (`any` in two catch blocks, setState-in-effect in `AuthContext` and `useTrades`)

## Gotchas & Decisions

- **`roundTurnFee` values are placeholders** (~$1.34 micros / $4.28 minis, Tradovate/Apex-style).
  Tune to the actual firm schedule. Point values are fixed CME specs and are correct.
- **`commission` is stored per trade**, so changing the fee schedule later never silently
  rewrites the P/L of trades already logged.
- **New entity columns must be nullable** — `ddl-auto: update` cannot add a `NOT NULL` column
  to a table that already has rows.
- **`tradeDate` uses local time, not `toISOString()`** — UTC conversion would file an evening
  trade under the next day on the calendar.
- **Typechecking:** plain `npx tsc --noEmit` checks NOTHING (root `tsconfig.json` has
  `"files": []`). Use `npx tsc -b` or `./node_modules/.bin/tsc -p tsconfig.app.json --noEmit`.
- **CORS uses `allowedOriginPatterns("http://localhost:*")`** — DEV ONLY; Vite moves to
  5174/5175 when 5173 is taken. Narrow before deploying.
- **No tests exist** in either codebase. The `Instrument` math is the highest-value place to start.

## Guidelines for AI Collaboration

- **Style:** Explain architectural choices step-by-step before outputting code.
- **Rule:** Do not auto-generate full component files unless requested; highlight where
  specific logic should be inserted instead.
