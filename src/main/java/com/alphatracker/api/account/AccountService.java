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
                .map(account -> AccountResponse.fromEntity(account, computeDrawdownSnapshot(account)))
                .toList();
    }

    // Replays the account's trades in chronological order from startingBalance
    // to find the high-water mark, then derives the floor from maxDrawdown and
    // (if set) trailingStopsAtBalance. See CONTEXT.md "Drawdown Rules (Resolved)".
    @Transactional(readOnly = true)
    public DrawdownSnapshot computeDrawdownSnapshot(Account account) {
        List<Trade> trades = tradeRepository.findAllByAccountIdOrderByTradeDateAsc(account.getId());
        DrawdownMode mode = account.getDrawdownMode() == null ? DrawdownMode.END_OF_DAY : account.getDrawdownMode();

        double highWaterMark = account.getStartingBalance();
        double running = account.getStartingBalance();

        if (mode == DrawdownMode.PER_TRADE_CLOSE) {
            for (Trade trade : trades) {
                running = round(running + trade.getProfitLoss());
                highWaterMark = Math.max(highWaterMark, running);
            }
        } else {
            // END_OF_DAY: only the balance at the close of each day can raise the
            // high-water mark, so an intraday peak given back before the day ends
            // never counts. LinkedHashMap preserves chronological day order because
            // `trades` is already sorted ascending by tradeDate.
            Map<LocalDate, Double> dailyPnl = trades.stream()
                    .collect(Collectors.groupingBy(
                            trade -> trade.getTradeDate().toLocalDate(),
                            LinkedHashMap::new,
                            Collectors.summingDouble(Trade::getProfitLoss)));

            for (double dayPnl : dailyPnl.values()) {
                running = round(running + dayPnl);
                highWaterMark = Math.max(highWaterMark, running);
            }
        }

        double rawFloor = highWaterMark - account.getMaxDrawdown();
        double drawdownFloor = account.getTrailingStopsAtBalance() != null
                ? Math.min(rawFloor, account.getTrailingStopsAtBalance())
                : rawFloor;

        return new DrawdownSnapshot(round(highWaterMark), round(drawdownFloor));
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
        return AccountResponse.fromEntity(saved, computeDrawdownSnapshot(saved));
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
        return AccountResponse.fromEntity(saved, computeDrawdownSnapshot(saved));
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
            return new BackfillResult(0, AccountResponse.fromEntity(primary, computeDrawdownSnapshot(primary)));
        }

        double totalPnl = unassigned.stream().mapToDouble(Trade::getProfitLoss).sum();
        for (Trade trade : unassigned) {
            trade.setAccount(primary);
        }
        tradeRepository.saveAll(unassigned);

        primary.setCurrentBalance(round(primary.getCurrentBalance() + totalPnl));
        Account saved = accountRepository.save(primary);

        return new BackfillResult(unassigned.size(), AccountResponse.fromEntity(saved, computeDrawdownSnapshot(saved)));
    }
}
