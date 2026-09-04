export type AccountType = 'EVALUATION' | 'FUNDED';

export interface Account {
    id: number;
    name: string;
    firm: string; 
    accountType: AccountType;
    startingBalance: number;
    currentBalance: number;
    profitTarget?: number;
    maxDrawdown: number;
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
}