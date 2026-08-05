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
