export type AccountType = 'EVALUATION' | 'FUNDED';

// Mirrors DrawdownMode.java. END_OF_DAY: the high-water mark only moves on a
// day's closing balance. PER_TRADE_CLOSE: it moves after every trade close.
export type DrawdownMode = 'END_OF_DAY' | 'PER_TRADE_CLOSE';

export interface Account {
    id: number;
    name: string;
    firm: string;
    accountType: AccountType;
    startingBalance: number;
    currentBalance: number;
    profitTarget?: number;
    maxDrawdown: number;
    drawdownMode: DrawdownMode;
    trailingStopsAtBalance?: number;
    // Computed server-side by AccountService.computeDrawdownSnapshot from the
    // account's full trade history — never recompute these on the client.
    highWaterMark: number;
    drawdownFloor: number;
    active: boolean;
    createdAt: string;
}

export interface CreateAccountPayload {
    name: string;
    firm: string;
    accountType: AccountType;
    startingBalance: number;
    profitTarget?: number;
    maxDrawdown: number;
    drawdownMode?: DrawdownMode;
    trailingStopsAtBalance?: number;
}