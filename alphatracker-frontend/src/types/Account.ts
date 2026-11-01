export type AccountType = 'EVALUATION' | 'FUNDED';

// Mirrors AccountStatus.java. An evaluation is ACTIVE until the trader marks it PASSED or FAILED; a funded
// account can only go ACTIVE -> FAILED. The server's replay only SUGGESTS one (suggestedStatus below).
export type AccountStatus = 'ACTIVE' | 'PASSED' | 'FAILED';

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
    // Profit-target and breach facts from the same server replay (AccountService.replay): derived on every
    // read from the closed trades, never stored, never recomputed here.
    profit: number;                         // closed P&L since the starting balance
    targetProgress: number | null;          // profit / profitTarget (0.71 = 71%, may exceed 1); null when there is no target
    targetReachedOn: string | null;         // 'YYYY-MM-DD', the first day the target was met
    breachedOn: string | null;              // 'YYYY-MM-DD', the first day a closed trade hit the drawdown floor
    suggestedStatus: AccountStatus | null;  // whichever of the two came first: PASSED or FAILED
    // The trader's own call, stored. Accounts that predate the column come back as ACTIVE.
    status: AccountStatus;
    closedAt: string | null;
    promotedFromAccountId: number | null;   // for a funded account created after passing an evaluation
    // Exactly one account per user is primary at a time — see
    // AccountService.setPrimaryAccount. New trades logged without an
    // explicit account attach here instead of staying unassigned.
    isPrimary: boolean;
    active: boolean;
    createdAt: string;
}

export interface BackfillResult {
    tradesBackfilled: number;
    account: Account;
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