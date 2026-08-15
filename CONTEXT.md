# AlphaTracker - Project Context & Architecture

## Core Tech Stack

- **Backend:** Java 17, Spring Boot, Spring Security, JWT Auth, PostgreSQL (Hibernate/JPA)
- **Frontend:** React 19, TypeScript, Vite, Tailwind CSS v4, Axios, Recharts

## Data Flow & Architecture

- Authentication via `/api/v1/auth/register` and `/api/v1/auth/authenticate` returning JWTs.
- `apiClient.ts` handles Bearer token injection for protected REST calls.
- Frontend uses `useTrades` hook to pull live trade execution data from `GET /api/v1/trades`.
- Client-side utility functions handle P/L aggregation, win rates, and daily matrix formatting.

## Current Working Sprint: UI & Trade Logging

- [x] Auth flow and JWT persistence
- [x] Calendar P/L matrix & Equity Curve visualization
- [x] Trade Entry Modal (`TradeEntryModal.tsx`)
- [ ] Account Filter Dropdown (Evaluation vs. Funded)
- [ ] Trade Execution Table View

## Guidelines for AI Collaboration

- **Style:** Explain architectural choices step-by-step before outputting code.
- **Rule:** Do not auto-generate full component files unless requested; highlight where specific logic should be inserted instead.

## Phase 1 — Server-side trade economics (complete)

- Goal: trader inputs only observable facts (ticker, direction, entry, exit, contracts); all money is derived server-side.

# New files

- trade/Instrument.java — enum of 12 futures contracts (ES/MES, NQ/MNQ, YM/MYM, RTY/M2K, CL/MCL, GC/MGC), each with pointValue (CME spec) and roundTurnFee per contract. fromTicker() normalizes case/whitespace and rejects unknown tickers rather than defaulting.
- trade/TradeRequest.java — POST write contract. Excludes id, user, commission, profitLoss by design.
- exception/GlobalExceptionHandler.java — IllegalArgumentException → 400, SecurityException → 403, both with a JSON message the frontend can read.

# Modified

- trade/Trade.java — added nullable commission and followedPlan columns.
- trade/TradeService.java — logTrade(TradeRequest, User) resolves instrument, validates inputs, derives P/L; tradeDate defaults to now, followedPlan to true.
- trade/TradeController.java — POST binds TradeRequest instead of the Trade entity.

# P/L formula: profitLoss = round2((LONG ? exit-entry : entry-exit) × pointValue × contracts − roundTurnFee × contracts) — stored net.

# Notes for future work

- roundTurnFee values are prop-firm placeholders (~$1.34 micros / $4.28 minis); tune to actual firm schedule before logging real trades.
- commission stored separately so fee-schedule changes don't rewrite existing trade history.
- Columns are nullable because ddl-auto: update can't add NOT NULL to a populated table.

- Sprint checklist additions: you may want [x] Server-side P/L derivation via Instrument enum and [x] TradeRequest DTO + global exception handling under the current sprint.

- Still open: useTrades has no refetch (Phase 2); modal posts symbol/quantity instead of ticker/contracts and has no date field, so UI submission still 400s (Phase 3).

Phase 3 is done and the frontend typechecks clean. All changes were targeted edits to TradeEntryModal.tsx — no regenerated file, so your existing markup and styling are intact.

What changed

Export name — TradeEntryModalProps → TradeEntryModal (the interface keeps the Props name). This clears the TS2724 error that was blocking the build.

Payload now matches TradeRequest exactly:

┌────────────┬──────────────────────────┐
│ Before │ After │
├────────────┼──────────────────────────┤
│ symbol │ ticker │
├────────────┼──────────────────────────┤
│ quantity │ contracts │
├────────────┼──────────────────────────┤
│ commission │ removed — server-derived │
├────────────┼──────────────────────────┤
│ — │ tradeDate │
└────────────┴──────────────────────────┘

Ticker is now a dropdown, not free text. It's backed by an INSTRUMENTS const mirroring the backend enum. Since the server rejects unknown tickers outright, a dropdown makes that error unreachable rather than something you discover after typing.

Commission input is gone, and the date/time field took its grid slot. This is the change you asked for in your model: you pick MNQ vs NQ and the server does the money.

tradeDate uses datetime-local, formatted in local time by a nowForInput() helper. I avoided toISOString() deliberately — it converts to UTC, which would file an evening trade under the next day on your calendar matrix. Given the calendar is the centerpiece, that would have been a quiet and very annoying bug.

Form resets on close and after a successful save. The component stays mounted while closed (the if (!isOpen) return null early return), so state persisted between opens. Both the ✕ and Cancel buttons now route through handleClose.

Submit awaits the refetch before closing, so the calendar and equity curve already reflect the new trade when the modal disappears. That required widening the prop type to () => void | Promise<void>.

Verification status

tsc -p tsconfig.app.json --noEmit exits 0. Two caveats worth stating plainly: my earlier "typecheck passes" during Phase 2 was wrong — the root tsconfig.json has "files": [], so plain tsc --noEmit checked nothing. And one run in this turn accidentally pulled a decoy tsc package from npm because the shell cwd had reset; the clean result above came from ./node_modules/.bin/tsc.

I have not run the app or posted a real trade. Compile-clean is not the same as working end-to-end.

Backend compiles clean. Here's the diagnosis and fix.

Root cause

All three errors were one problem: CORS was configured on AuthenticationController only.

@CrossOrigin(origins = "http://localhost:5173") // ← on auth, nowhere else
public class AuthenticationController

TradeController had nothing, and there was no global config. Chain of events on every trade request:

1. Because the request carries an Authorization header, the browser first sends a preflight OPTIONS /api/v1/trades
2. Preflights are sent without credentials by spec — so your JWT filter finds no token
3. anyRequest().authenticated() rejects it → 403
4. That 403 has no Access-Control-Allow-Origin header → CORS error
5. The real POST is never sent → Axios "failed"

This also means GET /trades has been failing the same way; the dashboard was showing empty data, not live data. CSRF was already correctly disabled, so it was never that.

The fix

SecurityConfiguration.java — three changes:

- .cors(Customizer.withDefaults()) on the filter chain, so Security picks up the policy bean
- .requestMatchers(HttpMethod.OPTIONS, "/\*\*").permitAll() as an explicit belt-and-braces so a future refactor can't silently reintroduce the 403
- A corsConfigurationSource() bean applying to /\*\*: allowed methods, Authorization + Content-Type headers, credentials enabled, and a 1-hour preflight cache

I used setAllowedOriginPatterns("http://localhost:\*") rather than a hardcoded :5173. Vite silently moves to 5174/5175 when the port is taken, and a fixed origin means this breaks again the next time that happens. It's marked DEV ONLY in a comment — narrow it before deploying.

AuthenticationController.java — removed the now-redundant @CrossOrigin and its import, leaving a comment pointing at the central config. One policy instead of two competing ones.

Restart the backend

The Spring context caches the security filter chain, so this needs a full restart:

./mvnw spring-boot:run

Then retry the MNQ long (20150.25 → 20185.00, 1 contract) and you should get $68.16 on today's calendar cell.

Verified by compile only — BUILD SUCCESS. I haven't run the server or exercised the endpoint, so if anything still fails, the useful detail is whether the failing request in the Network tab is the OPTIONS or the POST, and what status it returns.

@CrossOrigin(origins = "http://localhost:5173") // ← on auth, nowhere else
public class AuthenticationController

TradeController had nothing, and there was no global config. Chain of events on every trade request:

1. Because the request carries an Authorization header, the browser first sends a preflight OPTIONS /api/v1/trades
2. Preflights are sent without credentials by spec — so your JWT filter finds no token
3. anyRequest().authenticated() rejects it → 403
