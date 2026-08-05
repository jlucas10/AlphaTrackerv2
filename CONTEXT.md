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
