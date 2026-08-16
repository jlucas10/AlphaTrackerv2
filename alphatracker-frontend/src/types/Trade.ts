export interface Trade {
  id: number;
  ticker: string;
  direction: string;
  entryPrice: number;
  exitPrice: number;
  contracts: number;
  profitLoss: number; // net of commission, derived server-side
  // Added by the backend TradeRequest work. Optional because trades logged
  // before those columns existed come back with null.
  commission?: number | null;
  followedPlan?: boolean | null;
  notes: string | null;
  tradeDate: string;
  user?: {
    id: number;
    email: string;
    firstName: string;
    lastName: string;
    role: string;
  };
}
