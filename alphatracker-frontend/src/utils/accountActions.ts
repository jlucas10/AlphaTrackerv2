import type { Account, CreateAccountPayload } from '../types/Account';

// Which lifecycle buttons an account's card offers, and what the funded-account form starts with. Plain
// functions (no React) so each rule can be checked on its own.

export type AccountAction = 'PASS' | 'FAIL' | 'REOPEN' | 'SETUP_FUNDED';

export interface AvailableAction {
  action: AccountAction;
  label: string;
  // The server's replay suggested this one. It is highlighted, never done automatically: the trader decides.
  suggested: boolean;
}

// `hasSuccessor`: a funded account was already created from this one, so don't offer to set up another.
export function availableActions(account: Account, hasSuccessor: boolean): AvailableAction[] {
  if (account.status !== 'ACTIVE') {
    const actions: AvailableAction[] = [];
    // Passed, but the funded account was never created (the trader closed the form): a way back in.
    if (account.status === 'PASSED' && account.accountType === 'EVALUATION' && !hasSuccessor) {
      actions.push({ action: 'SETUP_FUNDED', label: 'Set up funded account', suggested: true });
    }
    // Reopening undoes a mistaken pass/fail, which is why neither of those needs a confirmation dialog.
    actions.push({ action: 'REOPEN', label: 'Reopen', suggested: false });
    return actions;
  }

  const actions: AvailableAction[] = [];
  // Only an evaluation can be passed; the server refuses it for a funded account too.
  if (account.accountType === 'EVALUATION') {
    actions.push({ action: 'PASS', label: 'Mark passed', suggested: account.suggestedStatus === 'PASSED' });
  }
  actions.push({ action: 'FAIL', label: 'Mark failed', suggested: account.suggestedStatus === 'FAILED' });
  return actions;
}

// The funded-account form's starting values. The trader keeps the same firm, size and drawdown rules in
// the common case; anything that differs they edit. The target is left blank: a funded account has none.
export function fundedPrefillFrom(source: Account): CreateAccountPayload {
  return {
    name: `${source.name} (Funded)`,
    firm: source.firm,
    accountType: 'FUNDED',
    startingBalance: source.startingBalance,
    maxDrawdown: source.maxDrawdown,
    drawdownMode: source.drawdownMode,
    trailingStopsAtBalance: source.trailingStopsAtBalance,
    promotedFromAccountId: source.id,
  };
}
