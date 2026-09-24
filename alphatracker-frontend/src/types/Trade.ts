export interface Trade {
  id: number;
  ticker: string;
  direction: string;
  entryPrice: number;
  exitPrice: number;
  contracts: number;
  // Point move x instrument's point value x contracts, derived server-side.
  // No commission modeled - see Instrument.java for why.
  profitLoss: number;
  followedPlan?: boolean | null;
  notes: string | null;
  tradeDate: string;
  // Trade-level reflection fields (Sprint 3.5) - edited via PATCH /trades/{id},
  // distinct from JournalEntry's day-level notes/htfBias.
  executionRating?: number | null;
  setupTags?: string[];
  user?: {
    id: number;
    email: string;
    firstName: string;
    lastName: string;
    role: string;
  };
}
