package com.alphatracker.api.analytics;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alphatracker.api.account.AccountRepository;
import com.alphatracker.api.trade.Trade;
import com.alphatracker.api.trade.TradeRepository;
import com.alphatracker.api.user.User;

import lombok.RequiredArgsConstructor;

// Read-only. Responsible for the request-shaped concerns - ownership, turning
// inclusive calendar dates into a half-open timestamp window, validating
// input - and nothing else: every number comes from TradeStats, and no money
// is ever recomputed here (profitLoss is used exactly as stored).
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    // Used when the caller gives no from/to. The repository queries need real
    // bounds (see TradeRepository.findForAnalytics); these are simply "before
    // any trade could exist" and "after any trade could exist".
    private static final LocalDateTime EARLIEST = LocalDate.of(1970, 1, 1).atStartOfDay();
    private static final LocalDateTime LATEST = LocalDate.of(2100, 1, 1).atStartOfDay();

    static final String SETUP_NOTE = "A trade with several setup tags counts in every one of its tags' groups, "
            + "so group trade counts add up to more than the overall trade count.";

    private final TradeRepository tradeRepository;
    private final AccountRepository accountRepository;

    public SummaryResponse summary(User user, Long accountId, LocalDate from, LocalDate to) {
        List<Trade> trades = loadTrades(user, accountId, from, to);
        return new SummaryResponse(accountId, from, to,
                TradeStats.summarize(trades), TradeStats.summarizeDays(trades));
    }

    public BreakdownResponse breakdown(User user, Long accountId, LocalDate from, LocalDate to, String by) {
        // Validate the cheap input before touching the database.
        String key = by == null ? "" : by.trim().toLowerCase();
        if (!List.of("instrument", "setup", "rating").contains(key)) {
            throw new IllegalArgumentException("'by' must be one of: instrument, setup, rating.");
        }
        List<Trade> trades = loadTrades(user, accountId, from, to);
        List<StatGroup> groups = switch (key) {
            case "instrument" -> TradeStats.byInstrument(trades);
            case "setup" -> TradeStats.bySetup(trades);
            default -> TradeStats.byRating(trades);
        };
        return new BreakdownResponse(key, accountId, from, to, groups, key.equals("setup") ? SETUP_NOTE : null);
    }

    public DisciplineResponse discipline(User user, Long accountId, LocalDate from, LocalDate to) {
        return new DisciplineResponse(accountId, from, to,
                TradeStats.discipline(loadTrades(user, accountId, from, to)));
    }

    private List<Trade> loadTrades(User user, Long accountId, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must not be after 'to'.");
        }

        // `to` is an inclusive calendar date, so the window ends at the start
        // of the NEXT day, exclusive - the same >= / < shape as the midnight
        // fix, so a trade at exactly 00:00 tomorrow is never counted today.
        LocalDateTime start = from == null ? EARLIEST : from.atStartOfDay();
        LocalDateTime endExclusive = to == null ? LATEST : to.plusDays(1).atStartOfDay();

        if (accountId == null) {
            return tradeRepository.findForAnalytics(user.getId(), start, endExclusive);
        }

        // Explicit ownership check, so the caller can tell "not your account"
        // (403) from "your account, no trades" (200 with zero counts). The
        // repository query also filters on user_id as a second layer.
        accountRepository.findByIdAndUserId(accountId, user.getId())
                .orElseThrow(() -> new SecurityException("Unauthorized access: account not found or not yours."));
        return tradeRepository.findForAnalyticsByAccount(user.getId(), accountId, start, endExclusive);
    }
}
