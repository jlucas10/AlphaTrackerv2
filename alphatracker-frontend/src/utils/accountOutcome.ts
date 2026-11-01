import { format, parseISO } from 'date-fns';
import type { Account } from '../types/Account';

// What an account's card should say about its profit target and drawdown breach. A plain function so
// every combination can be checked on its own: the awkward cases (target hit THEN floor breached,
// breached THEN recovered above the target, a status that disagrees with the history) live here and
// not buried in markup.
//
// Nothing here calculates money. The numbers come from the server's replay (AccountService.replay).

export type BannerTone = 'success' | 'danger';

export interface AccountBanner {
  tone: BannerTone;
  message: string;
  detail?: string;
}

// '2026-10-12' or '2026-10-28T17:54:26.610519' -> 'Oct 12'. Dates without a timezone are shown as written.
export function shortDate(iso: string): string {
  return format(parseISO(iso), 'MMM d');
}

export function describeAccountOutcome(
  a: Pick<Account, 'status' | 'closedAt' | 'targetReachedOn' | 'breachedOn' | 'suggestedStatus'>,
): AccountBanner | null {
  const reached = a.targetReachedOn ? shortDate(a.targetReachedOn) : null;
  const breached = a.breachedOn ? shortDate(a.breachedOn) : null;
  const closed = a.closedAt ? ` on ${shortDate(a.closedAt)}` : '';

  // The trader has made the call. Show it; and if the history disagrees, say so rather than hide it.
  if (a.status === 'PASSED') {
    return {
      tone: 'success',
      message: `Passed${closed}.`,
      detail:
        a.suggestedStatus === 'FAILED' && breached
          ? `Heads up: the drawdown floor was breached on ${breached}, before the target was reached.`
          : undefined,
    };
  }
  if (a.status === 'FAILED') {
    return {
      tone: 'danger',
      message: `Marked failed${closed}.`,
      detail:
        a.suggestedStatus === 'PASSED' && reached ? `Note: the profit target was reached on ${reached}.` : undefined,
    };
  }

  // Still ACTIVE: the replay's suggestion, which is only ever a suggestion (the app cannot see a firm's
  // other rules, such as minimum trading days), so the wording asks the trader to confirm.
  if (a.suggestedStatus === 'PASSED' && reached) {
    return {
      tone: 'success',
      message: `🎯 Profit target reached on ${reached}.`,
      detail:
        'Mark it passed once your firm has confirmed the pass.' +
        (breached ? ` The floor was breached later, on ${breached}.` : ''),
    };
  }
  if (a.suggestedStatus === 'FAILED' && breached) {
    return {
      tone: 'danger',
      message: `⚠️ Drawdown floor breached on ${breached}.`,
      detail:
        'Mark it failed if your firm has closed the account.' +
        (reached ? ` The target was reached later (${reached}), but the breach came first.` : ''),
    };
  }
  return null;
}

export function statusLabel(status: Account['status']): string {
  return status === 'PASSED' ? 'Passed' : status === 'FAILED' ? 'Failed' : 'Active';
}
