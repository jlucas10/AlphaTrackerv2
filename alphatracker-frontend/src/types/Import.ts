import type { AccountStatus } from './Account';

// Mirrors the backend's importer package (ImportPreviewResponse / ImportConfirmResponse). Every dollar figure
// here is computed by the server; the client only displays it.

// Mirrors ImportSource.java. TopstepX only for now; a new broker is a new value here and a new option in the modal.
export type ImportSource = 'TOPSTEPX';

export type ImportRowStatus = 'NEW' | 'DUPLICATE' | 'ERROR';
export type ImportAccountCheckStatus = 'NEW' | 'DUPLICATE';

export interface ImportAccountInfo {
    id: number;
    name: string;
    status: AccountStatus;
    // Set when the account is already PASSED/FAILED: the import still saves, the trader is just told first.
    warning: string | null;
}

export interface ImportSummary {
    total: number;
    ready: number;          // rows with something to create
    duplicates: number;     // rows already imported into EVERY chosen account
    errors: number;
    tradesToCreate: number; // (row, account) pairs: 20 rows into 2 accounts = 40
}

// The trade as it WOULD be saved. profitLoss is derived server-side (same code as a hand-logged trade);
// sourcePnl is what the file claimed.
export interface ImportTradePreview {
    ticker: string;
    direction: 'LONG' | 'SHORT';
    entryPrice: number;
    exitPrice: number;
    contracts: number;
    tradeDate: string;      // entry time in the journal's zone (America/Los_Angeles), 'YYYY-MM-DDTHH:mm:ss'
    exitTime: string;
    profitLoss: number;
    sourcePnl: number | null;
}

export interface ImportAccountCheck {
    accountId: number;
    status: ImportAccountCheckStatus;
}

export interface ImportPreviewRow {
    line: number;
    externalId: string | null;
    status: ImportRowStatus;
    trade: ImportTradePreview | null;   // null for ERROR rows
    accounts: ImportAccountCheck[];
    warnings: string[];
    errors: string[];
}

export interface ImportPreview {
    source: ImportSource;
    accounts: ImportAccountInfo[];
    summary: ImportSummary;
    rows: ImportPreviewRow[];
}

export interface ImportRowError {
    line: number;
    externalId: string | null;
    message: string;
}

export interface ImportAccountResult {
    id: number;
    name: string;
    created: number;
    skipped: number;
    currentBalance: number;
    highWaterMark: number;
    drawdownFloor: number;
    profit: number;
    suggestedStatus: AccountStatus | null;
    breachedOn: string | null;
    targetReachedOn: string | null;
}

export interface ImportConfirmResult {
    source: ImportSource;
    created: number;
    skipped: number;
    failed: number;
    accounts: ImportAccountResult[];
    errors: ImportRowError[];
}
