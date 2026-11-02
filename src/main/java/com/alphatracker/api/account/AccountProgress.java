package com.alphatracker.api.account;

import java.time.LocalDate;

// What replaying an account's closed trades says about its profit target and drawdown breach.
// Derived on every read (never stored), exactly like the drawdown floor: delete a trade and the
// answer corrects itself, and there is no stored flag that could disagree with the trades.
//
//   profit            closed P&L since the starting balance
//   targetProgress    profit / profitTarget (0.7133 = 71%); may exceed 1 or be negative;
//                     null when the account has no target (funded accounts, or none set)
//   targetRemaining   profitTarget - profit in dollars, never below 0 (0 once the target is met); null
//                     when there is no target. Computed here so nothing downstream (the dashboard, the
//                     AI assistant, which must not do arithmetic) has to subtract.
//   targetReachedOn   the first day the target was met (judged per trade, or at the day's close
//                     in END_OF_DAY mode, the same way the drawdown floor is), else null
//   breachedOn        the first day a closed trade left the balance at or below the floor, else null
//   suggestedStatus   whichever of the two happened FIRST: FAILED or PASSED, else null. An account
//                     that blew its floor and later climbed above the target did not pass.
public record AccountProgress(
        Double profit,
        Double targetProgress,
        Double targetRemaining,
        LocalDate targetReachedOn,
        LocalDate breachedOn,
        AccountStatus suggestedStatus) {
}
