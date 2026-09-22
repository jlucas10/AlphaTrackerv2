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
  It supports account-scoped queries (`/trades?accountId=...`), and exposes `loading` (first load
  only) and `refreshing` (background) so the dashboard updates in place instead of collapsing into
  a loader.
- `useAccounts` manages the list of prop accounts, account creation, and the active account filter.
- CORS is configured centrally in `SecurityConfiguration`, not per-controller.
- Client-side utilities handle P/L aggregation, win rates, and daily matrix formatting.

## Domain Model: How Money & Risk Are Calculated

**The trader inputs only what they observed** — ticker, direction, entry, exit, contracts.
Never a commission, never a dollar multiplier. A 10-point move is $20 on MNQ and $200 on NQ;
that difference belongs in the server, not the trader's head.

`Instrument` (enum, `com.alphatracker.api.trade`) is the single source of truth for contract
economics — 12 contracts (ES/MES, NQ/MNQ, YM/MYM, RTY/M2K, CL/MCL, GC/MGC), each with a
`pointValue` (fixed CME spec) and a `roundTurnFee` per contract.

```text
priceMove  = LONG ? exit - entry : entry - exit
gross      = priceMove × pointValue × contracts
commission = roundTurnFee × contracts
profitLoss = round2(gross - commission)      ← stored NET, what hits the prop account
```

Unknown tickers are **rejected**, never defaulted to a 1.0 multiplier.

---

## Key Files

**Backend** (`src/main/java/com/alphatracker/api/`)

- `account/Account.java` — prop entity: starting/current balance, maxDrawdown, profitTarget, `drawdownMode`, `trailingStopsAtBalance`, `isPrimary`
- `account/AccountService.java` — account CRUD and user boundary checks
- `account/AccountController.java` — REST endpoints (`/api/v1/accounts`)
- `account/DrawdownMode.java` — enum (`END_OF_DAY`, `PER_TRADE_CLOSE`)
- `trade/Instrument.java` — contract economics (point values + round-turn fees)
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

### Sprint 4 — Monetization & Billing (Stripe Integration)

- [ ] Stripe customer creation on user registration
- [ ] Stripe Checkout session endpoint for subscription tiers (e.g. Free vs Pro Trader)
- [ ] Stripe Webhook handler (`/api/v1/webhooks/stripe`) for subscription lifecycle events (`customer.subscription.created`, `invoice.payment_succeeded`, etc.)
- [ ] Backend subscription tier security guardrails / access gates (e.g. max active accounts limit)
- [ ] Frontend billing settings & subscription status badge

### Sprint 5 / Update 1 — Analytics, Discipline Streaks & Cloud Deploy

- [ ] Real Discipline Score & Streaks engine
- [ ] Peak-to-trough drawdown curve & session analytics (NY AM vs PM vs London)
- [ ] Docker containerization & AWS deployment (ECS/Fargate + RDS PostgreSQL)

---

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
closedBalance   = startingBalance + Σ profitLoss        (net, as already stored)
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
