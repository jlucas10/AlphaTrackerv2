package com.alphatracker.api.account;

import org.springframework.stereotype.Service;
import com.alphatracker.api.trade.Trade;
import com.alphatracker.api.trade.TradeRepository;
import com.alphatracker.api.user.User;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
// validates business logic, assigns the authenticated user,
// and converts entities to DTO responses.

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final TradeRepository tradeRepository;

    @Transactional(readOnly = true)
    public List<AccountResponse> getUserAccounts(User user) {
        return accountRepository.findAllByUserId(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // Replays the account's trades in chronological order from startingBalance
    // to find the high-water mark, then derives the floor from maxDrawdown and
    // (if set) trailingStopsAtBalance. See CONTEXT.md "Drawdown Rules (Resolved)".
    @Transactional(readOnly = true)
    public DrawdownSnapshot computeDrawdownSnapshot(Account account) {
        return replay(account).drawdown();
    }

    // Profit-target progress and drawdown-breach facts, from the SAME replay as the floor above.
    @Transactional(readOnly = true)
    public AccountProgress computeProgress(Account account) {
        return replay(account).progress();
    }

    // Everything one pass over an account's history can tell us.
    private record Replay(DrawdownSnapshot drawdown, AccountProgress progress) {
    }

    // The single replay behind the drawdown floor AND the pass/fail facts, so the two can never
    // disagree about how an account's history is read.
    //
    // The drawdown mode decides WHEN the account is judged, exactly as it already decides when the
    // high-water mark moves:
    //   PER_TRADE_CLOSE  after every closed trade (the high-water mark, floor and target all update)
    //   END_OF_DAY       the high-water mark and the profit target are judged at the day's close, but
    //                    a breach is checked after every trade against the floor in force from the
    //                    PREVIOUS close, so an intraday plunge through the floor still counts.
    // Only closed trades exist in this data: a position that dipped below the floor mid-trade and
    // recovered before closing cannot be seen.
    private Replay replay(Account account) {
        List<Trade> trades = tradeRepository.findAllByAccountIdOrderByTradeDateAsc(account.getId());
        DrawdownMode mode = account.getDrawdownMode() == null ? DrawdownMode.END_OF_DAY : account.getDrawdownMode();
        boolean perTrade = mode == DrawdownMode.PER_TRADE_CLOSE;

        double start = account.getStartingBalance();
        Double target = targetOf(account);

        double balance = start;
        double highWaterMark = start;
        double floor = floorFor(account, highWaterMark);

        // "Which happened first" is decided by a running step count, not by date: a breach and a
        // target hit can share a day, and the day's trades come before its close.
        int step = 0;
        int breachStep = -1;
        int targetStep = -1;
        LocalDate breachedOn = null;
        LocalDate targetReachedOn = null;

        // `trades` is sorted ascending by tradeDate; LinkedHashMap keeps the days in that order.
        Map<LocalDate, List<Trade>> days = trades.stream()
                .collect(Collectors.groupingBy(
                        trade -> trade.getTradeDate().toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        for (Map.Entry<LocalDate, List<Trade>> day : days.entrySet()) {
            double dayStart = balance;
            double dayPnl = 0;

            for (Trade trade : day.getValue()) {
                step++;
                dayPnl += trade.getProfitLoss();
                // END_OF_DAY: a day's balance is dayStart + the day's P&L, rounded ONCE, which is how the
                // original engine computed a closing balance (so the high-water mark is unchanged).
                balance = perTrade
                        ? round(balance + trade.getProfitLoss())
                        : round(dayStart + dayPnl);

                if (perTrade) {
                    highWaterMark = Math.max(highWaterMark, balance);
                    floor = floorFor(account, highWaterMark);
                }
                // At or below the floor = the account is blown (<=, not <: touching it is enough).
                if (breachedOn == null && balance <= round(floor)) {
                    breachedOn = day.getKey();
                    breachStep = step;
                }
                if (perTrade && target != null && targetReachedOn == null && round(balance - start) >= target) {
                    targetReachedOn = day.getKey();
                    targetStep = step;
                }
            }

            if (!perTrade) {
                // The close of the day: now the high-water mark, the floor for tomorrow, and the target.
                highWaterMark = Math.max(highWaterMark, balance);
                floor = floorFor(account, highWaterMark);
                if (target != null && targetReachedOn == null && round(balance - start) >= target) {
                    targetReachedOn = day.getKey();
                    targetStep = ++step;
                }
            }
        }

        double profit = round(balance - start);
        Double targetProgress = target == null ? null : Math.round(profit / target * 10_000.0) / 10_000.0;

        // Whichever came first decides the suggestion. An account that breached and then recovered
        // above the target is a failure, not a pass.
        AccountStatus suggested = null;
        if (breachStep >= 0 && (targetStep < 0 || breachStep < targetStep)) {
            suggested = AccountStatus.FAILED;
        } else if (targetStep >= 0) {
            suggested = AccountStatus.PASSED;
        }

        return new Replay(
                new DrawdownSnapshot(round(highWaterMark), round(floor)),
                new AccountProgress(profit, targetProgress, targetReachedOn, breachedOn, suggested));
    }

    // Only an evaluation has a profit target to hit. A funded account may still carry a number in the
    // field (the create form pre-fills one), but it is not something that account is working toward.
    private Double targetOf(Account account) {
        boolean applies = account.getAccountType() == AccountType.EVALUATION
                && account.getProfitTarget() != null
                && account.getProfitTarget() > 0;
        return applies ? account.getProfitTarget() : null;
    }

    // See CONTEXT.md "Drawdown Rules (Resolved)": the floor trails the high-water mark by maxDrawdown,
    // and stops rising once it reaches trailingStopsAtBalance (when one is set).
    private double floorFor(Account account, double highWaterMark) {
        double rawFloor = highWaterMark - account.getMaxDrawdown();
        return account.getTrailingStopsAtBalance() != null
                ? Math.min(rawFloor, account.getTrailingStopsAtBalance())
                : rawFloor;
    }

    // One place that turns an account into its response, so every endpoint reports the same facts.
    private AccountResponse toResponse(Account account) {
        Replay replay = replay(account);
        return AccountResponse.fromEntity(account, replay.drawdown(), replay.progress());
    }

    // Mirrors TradeService.round: keeps replayed dollars clean instead of
    // accumulating floating point drift over many trades.
    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @Transactional
    public AccountResponse createAccount(AccountRequest request, User user) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Account name is required.");
        }
        if (request.getFirm() == null || request.getFirm().trim().isEmpty()) {
            throw new IllegalArgumentException("Prop firm name is required.");
        }
        if (request.getStartingBalance() == null || request.getStartingBalance() <= 0) {
            throw new IllegalArgumentException("Starting balance must be greater than 0.");
        }
        if (request.getMaxDrawdown() == null || request.getMaxDrawdown() <= 0) {
            throw new IllegalArgumentException("Max drawdown limit must be greater than 0.");
        }
        if (request.getAccountType() == null) {
            request.setAccountType(AccountType.EVALUATION);
        }
        if (request.getTrailingStopsAtBalance() != null && request.getTrailingStopsAtBalance() <= 0) {
            throw new IllegalArgumentException("Trailing stop balance must be greater than 0.");
        }

        // A brand new trader has nothing to choose from, so their very first
        // account becomes primary automatically — otherwise new trades logged
        // without picking an account would have nowhere to attach until the
        // trader discovers the primary-account setting exists.
        boolean isFirstAccount = accountRepository.findAllByUserId(user.getId()).isEmpty();

        Account account = Account.builder()
                .name(request.getName().trim())
                .firm(request.getFirm().trim())
                .accountType(request.getAccountType())
                .startingBalance(request.getStartingBalance())
                .currentBalance(request.getStartingBalance())
                .profitTarget(request.getProfitTarget())
                .maxDrawdown(request.getMaxDrawdown())
                .drawdownMode(request.getDrawdownMode() == null ? DrawdownMode.END_OF_DAY : request.getDrawdownMode())
                .trailingStopsAtBalance(request.getTrailingStopsAtBalance())
                .isPrimary(isFirstAccount)
                .active(true)
                .user(user)
                .build();

        Account saved = accountRepository.save(account);
        return toResponse(saved);
    }

    // Marks one account primary and unsets any other, so exactly one account
    // per user is ever primary. Reassigns nothing by itself — backfilling
    // existing unassigned trades onto the new primary is a separate, explicit
    // step (backfillUnassignedTrades) since it mutates historical balances.
    @Transactional
    public AccountResponse setPrimaryAccount(Long accountId, User user) {
        Account target = accountRepository.findByIdAndUserId(accountId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found or does not belong to user."));

        List<Account> currentlyPrimary = accountRepository.findAllByUserIdAndIsPrimaryTrue(user.getId());
        for (Account account : currentlyPrimary) {
            if (!account.getId().equals(target.getId())) {
                account.setIsPrimary(false);
                accountRepository.save(account);
            }
        }

        target.setIsPrimary(true);
        Account saved = accountRepository.save(target);
        return toResponse(saved);
    }

    // Preview count shown before the trader confirms a backfill.
    @Transactional(readOnly = true)
    public long countUnassignedTrades(User user) {
        return tradeRepository.countByUserIdAndAccountIsNull(user.getId());
    }

    // Attaches every unassigned trade to the user's primary account and rolls
    // its net P/L into that account's live balance. Requires a primary account
    // to already be set — there's no implicit "pick one for me" fallback here,
    // since that's exactly the ambiguity a primary account exists to resolve.
    @Transactional
    public BackfillResult backfillUnassignedTrades(User user) {
        List<Account> primaryAccounts = accountRepository.findAllByUserIdAndIsPrimaryTrue(user.getId());
        if (primaryAccounts.isEmpty()) {
            throw new IllegalArgumentException("No primary account set. Set a primary account before backfilling.");
        }
        Account primary = primaryAccounts.get(0);

        List<Trade> unassigned = tradeRepository.findAllByUserIdAndAccountIsNull(user.getId());
        if (unassigned.isEmpty()) {
            return new BackfillResult(0, toResponse(primary));
        }

        double totalPnl = unassigned.stream().mapToDouble(Trade::getProfitLoss).sum();
        for (Trade trade : unassigned) {
            trade.setAccount(primary);
        }
        tradeRepository.saveAll(unassigned);

        primary.setCurrentBalance(round(primary.getCurrentBalance() + totalPnl));
        Account saved = accountRepository.save(primary);

        return new BackfillResult(unassigned.size(), toResponse(saved));
    }
}
