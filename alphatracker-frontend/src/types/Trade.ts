export interface Trade {
  id: number;
  ticker: string;
  direction: string;
  entryPrice: number;
  exitPrice: number;
  contracts: number;
  profitLoss: number;
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
