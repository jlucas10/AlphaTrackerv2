package com.alphatracker.api.demo;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alphatracker.api.account.Account;
import com.alphatracker.api.account.AccountRepository;
import com.alphatracker.api.account.AccountType;
import com.alphatracker.api.account.DrawdownMode;
import com.alphatracker.api.journal.JournalEntry;
import com.alphatracker.api.journal.JournalEntryRepository;
import com.alphatracker.api.trade.Instrument;
import com.alphatracker.api.trade.Trade;
import com.alphatracker.api.trade.TradeRepository;
import com.alphatracker.api.trade.TradeService;
import com.alphatracker.api.user.Role;
import com.alphatracker.api.user.User;
import com.alphatracker.api.user.UserRepository;

import lombok.RequiredArgsConstructor;

// Creates the shared demo user and fills it with a realistic ~10-week trading history.
//
// Everything is defined in code (no SQL paste) and is deterministic: the same call always produces the same
// trades, only anchored to "today" so the data always looks recent. Trades go through TradeService.derivePnl
// so the dollars match what a hand-logged trade would produce.
//
// The three accounts are tuned so each shows the engine something different:
//   Apex 50K        end-of-day trailing, comfortably profitable
//   Topstep 50K     per-trade-close trailing, a run-up then a slide: sits ~$300 above its drawdown floor
//   Lucid 100K      funded, end-of-day trailing with the floor locked at start + $100
@Service
@RequiredArgsConstructor
public class DemoSeedService {

    private static final int TRADING_DAYS = 50; // ten weeks of weekdays

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TradeRepository tradeRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final TradeService tradeService;
    private final PasswordEncoder passwordEncoder;
    private final DemoProperties properties;

    // One scripted trade. `points` is the move IN THE TRADE'S FAVOUR (negative = a loser), so the entry/exit
    // prices are built from it and the dollars fall out of the real P&L formula.
    private record Spec(int day, String ticker, String direction, double points, int contracts) {
    }

    private static final List<Spec> ACCOUNT_A_TRADES = List.of(
        new Spec(1, "MES", "LONG", 3.5, 4),
        new Spec(2, "MNQ", "SHORT", -9.0, 3),
        new Spec(3, "ES", "LONG", 2.0, 1),
        new Spec(5, "MNQ", "LONG", 13.5, 3),
        new Spec(6, "NQ", "SHORT", 6.5, 1),
        new Spec(8, "MES", "SHORT", -2.5, 5),
        new Spec(9, "ES", "LONG", -1.5, 1),
        new Spec(11, "NQ", "LONG", -10.5, 1),
        new Spec(12, "MNQ", "LONG", 18.0, 4),
        new Spec(14, "ES", "SHORT", 2.75, 2),
        new Spec(15, "M2K", "LONG", 2.0, 6),
        new Spec(17, "MES", "LONG", -3.0, 5),
        new Spec(18, "NQ", "LONG", 14.5, 1),
        new Spec(20, "MNQ", "SHORT", -7.5, 4),
        new Spec(21, "ES", "LONG", 3.25, 2),
        new Spec(23, "M2K", "SHORT", -1.75, 5),
        new Spec(24, "NQ", "SHORT", 12.0, 1),
        new Spec(26, "MES", "LONG", 4.5, 6),
        new Spec(27, "ES", "LONG", -2.0, 2),
        new Spec(29, "MNQ", "LONG", 16.5, 5),
        new Spec(30, "NQ", "LONG", -8.5, 1),
        new Spec(32, "ES", "SHORT", 2.5, 2),
        new Spec(33, "MES", "SHORT", -3.5, 5),
        new Spec(35, "MNQ", "SHORT", 11.5, 4),
        new Spec(36, "NQ", "LONG", 9.0, 1),
        new Spec(38, "ES", "LONG", -2.75, 2),
        new Spec(39, "M2K", "LONG", 2.75, 6),
        new Spec(41, "MNQ", "LONG", -12.5, 3),
        new Spec(42, "ES", "LONG", 4.0, 2),
        new Spec(44, "MES", "LONG", 5.5, 6),
        new Spec(45, "NQ", "SHORT", -7.25, 1),
        new Spec(47, "MNQ", "LONG", 15.0, 4),
        new Spec(48, "ES", "SHORT", 3.0, 2),
        new Spec(49, "MES", "SHORT", -1.75, 4));

    private static final List<Spec> ACCOUNT_B_TRADES = List.of(
        new Spec(1, "MNQ", "LONG", 20.0, 3),
        new Spec(2, "MES", "LONG", 5.0, 5),
        new Spec(4, "MCL", "LONG", 0.3, 5),
        new Spec(5, "MNQ", "SHORT", 27.5, 4),
        new Spec(7, "MES", "SHORT", -4.5, 5),
        new Spec(8, "MNQ", "LONG", 32.5, 4),
        new Spec(10, "MCL", "SHORT", 0.4, 6),
        new Spec(11, "MYM", "LONG", 40.0, 10),
        new Spec(13, "MNQ", "SHORT", -20.0, 4),
        new Spec(14, "MES", "LONG", 7.0, 6),
        new Spec(16, "MCL", "LONG", -0.25, 5),
        new Spec(17, "MNQ", "LONG", 35.0, 4),
        new Spec(19, "MES", "SHORT", 6.0, 6),
        new Spec(20, "MYM", "SHORT", -30.0, 10),
        new Spec(22, "MNQ", "LONG", -27.5, 4),
        new Spec(23, "MCL", "SHORT", -0.35, 6),
        new Spec(25, "MES", "LONG", -5.5, 6),
        new Spec(26, "MNQ", "SHORT", -24.0, 5),
        new Spec(28, "MCL", "LONG", 0.2, 5),
        new Spec(29, "MES", "LONG", -6.5, 6),
        new Spec(31, "MNQ", "LONG", -31.0, 5),
        new Spec(32, "MYM", "LONG", -45.0, 10),
        new Spec(34, "MES", "SHORT", -5.0, 6),
        new Spec(35, "MNQ", "LONG", 17.5, 4),
        new Spec(37, "MCL", "SHORT", -0.12, 6),
        new Spec(38, "MNQ", "SHORT", -8.75, 5),
        new Spec(40, "MES", "LONG", -1.75, 5),
        new Spec(41, "MYM", "SHORT", 30.0, 10),
        new Spec(43, "MNQ", "LONG", -7.5, 4),
        new Spec(44, "MES", "SHORT", 4.0, 5),
        new Spec(46, "MNQ", "LONG", -6.0, 4),
        new Spec(47, "MCL", "LONG", -0.08, 5),
        new Spec(48, "MES", "LONG", 3.0, 4),
        new Spec(49, "MNQ", "SHORT", -2.75, 3));

    private static final List<Spec> ACCOUNT_C_TRADES = List.of(
        new Spec(2, "GC", "LONG", 2.25, 1),
        new Spec(3, "YM", "LONG", 19.75, 2),
        new Spec(5, "RTY", "SHORT", 3.0, 1),
        new Spec(6, "MGC", "LONG", 4.0, 5),
        new Spec(8, "ES", "LONG", 4.5, 1),
        new Spec(9, "GC", "SHORT", -1.5, 1),
        new Spec(11, "YM", "SHORT", 28.0, 2),
        new Spec(12, "RTY", "LONG", 3.75, 1),
        new Spec(14, "MGC", "LONG", 3.0, 6),
        new Spec(15, "ES", "SHORT", -2.75, 1),
        new Spec(17, "GC", "LONG", 2.75, 2),
        new Spec(18, "YM", "LONG", -23.0, 2),
        new Spec(20, "RTY", "SHORT", -2.75, 1),
        new Spec(21, "ES", "LONG", 5.25, 1),
        new Spec(23, "MGC", "SHORT", 3.25, 6),
        new Spec(24, "GC", "LONG", 2.25, 1),
        new Spec(26, "YM", "SHORT", 31.25, 2),
        new Spec(27, "ES", "SHORT", -3.0, 1),
        new Spec(29, "RTY", "LONG", 4.0, 2),
        new Spec(30, "GC", "LONG", -1.75, 1),
        new Spec(32, "MGC", "LONG", 4.5, 5),
        new Spec(33, "YM", "LONG", 36.25, 2),
        new Spec(35, "ES", "LONG", -3.25, 1),
        new Spec(36, "RTY", "SHORT", 3.25, 1),
        new Spec(38, "GC", "SHORT", 3.0, 2),
        new Spec(39, "MGC", "LONG", -2.75, 6),
        new Spec(41, "YM", "LONG", 24.75, 2),
        new Spec(42, "ES", "SHORT", 4.0, 1),
        new Spec(44, "RTY", "LONG", -3.0, 1),
        new Spec(45, "GC", "LONG", 2.75, 1),
        new Spec(47, "YM", "SHORT", -21.5, 2),
        new Spec(48, "ES", "LONG", 4.25, 1));

    private static final List<String> SETUP_TAGS = List.of(
            "FVG fill", "Liquidity sweep", "Opening range", "Trend pullback", "Breaker block", "Range fade");

    private static final List<String> WIN_NOTES = List.of(
            "Waited for the sweep, then entered on the displacement. Held to target.",
            "A+ setup, followed the plan end to end.",
            "Patient entry at the level, scaled out into strength.",
            "Higher-timeframe bias lined up, simple execution.");

    private static final List<String> LOSS_NOTES = List.of(
            "Stopped out cleanly. Setup was valid, price just didn't follow through.",
            "Entered a few ticks early before confirmation.",
            "Chased the move after missing the first entry. Should have skipped it.",
            "Sized up after a win and gave it back. Stick to the plan size.");

    private static final List<String> JOURNAL_NOTES = List.of(
            "Clean session. Took only A-setups and stopped after the second win.",
            "Choppy open. Sat out the first 30 minutes and it paid off.",
            "Overtraded the afternoon. Need a hard stop after two losses.",
            "News day, kept size small. Good discipline.",
            "Followed the plan on every trade. Review the exits tomorrow.",
            "Slow, rangebound tape. Fewer trades, no forced entries.");

    // Returns the demo user, creating and seeding it if needed. Safe to call repeatedly: it only seeds when
    // the user is missing or has no accounts, so it never duplicates data.
    @Transactional
    public User ensureSeeded() {
        User user = userRepository.findByEmail(properties.getEmail()).orElse(null);
        if (user == null) {
            try {
                user = userRepository.saveAndFlush(newDemoUser());
            } catch (DataIntegrityViolationException raced) {
                // Two first-ever calls raced and the unique email key let only one in; use the winner's row.
                user = userRepository.findByEmail(properties.getEmail()).orElseThrow(() -> raced);
            }
        }
        requireDemo(user);
        if (accountRepository.findAllByUserId(user.getId()).isEmpty()) {
            seedData(user, LocalDate.now());
        }
        return user;
    }

    // Writes the seed accounts, trades and journal entries for an (empty) demo user, with dates anchored to
    // `today`. Called by ensureSeeded and by the reset after it clears the old data.
    @Transactional
    public void seedData(User user, LocalDate today) {
        requireDemo(user);
        List<LocalDate> days = tradingDays(today);

        seedAccount(user, days, ACCOUNT_A_TRADES, Account.builder()
                .name("Apex 50K #1").firm("Apex").accountType(AccountType.EVALUATION)
                .startingBalance(50_000.0).profitTarget(3_000.0).maxDrawdown(2_500.0)
                .drawdownMode(DrawdownMode.END_OF_DAY).isPrimary(true));

        seedAccount(user, days, ACCOUNT_B_TRADES, Account.builder()
                .name("Topstep 50K").firm("Topstep").accountType(AccountType.EVALUATION)
                .startingBalance(50_000.0).profitTarget(3_000.0).maxDrawdown(2_000.0)
                .drawdownMode(DrawdownMode.PER_TRADE_CLOSE).isPrimary(false));

        seedAccount(user, days, ACCOUNT_C_TRADES, Account.builder()
                .name("Lucid 100K Funded").firm("Lucid").accountType(AccountType.FUNDED)
                .startingBalance(100_000.0).maxDrawdown(3_000.0)
                .drawdownMode(DrawdownMode.END_OF_DAY).trailingStopsAtBalance(100_100.0).isPrimary(false));

        seedJournal(user, days);
    }

    private void seedAccount(User user, List<LocalDate> days, List<Spec> specs, Account.AccountBuilder template) {
        Account account = template.active(true).user(user).createdAt(days.get(0).atTime(8, 0)).build();
        account.setCurrentBalance(account.getStartingBalance());
        account = accountRepository.save(account);

        List<Trade> trades = new ArrayList<>();
        double balance = account.getStartingBalance();
        for (int i = 0; i < specs.size(); i++) {
            Spec spec = specs.get(i);
            Instrument instrument = Instrument.fromTicker(spec.ticker());
            boolean isLong = "LONG".equals(spec.direction());

            double entry = entryPrice(spec.ticker(), spec.day(), i);
            double exit = round2(entry + (isLong ? spec.points() : -spec.points()));
            double pnl = tradeService.derivePnl(instrument, spec.direction(), entry, exit, spec.contracts());
            balance = round2(balance + pnl);

            boolean winner = pnl >= 0;
            // A third of the losers broke the plan, so the discipline score has something to show.
            boolean followedPlan = winner || i % 3 != 0;
            List<String> notes = winner ? WIN_NOTES : LOSS_NOTES;
            String note = followedPlan || winner ? notes.get(i % notes.size()) : LOSS_NOTES.get(2);

            trades.add(Trade.builder()
                    .ticker(instrument.name())
                    .direction(spec.direction())
                    .entryPrice(entry)
                    .exitPrice(exit)
                    .contracts(spec.contracts())
                    .profitLoss(pnl)
                    .followedPlan(followedPlan)
                    .notes(note)
                    .tradeDate(days.get(spec.day()).atTime(9, 30).plusMinutes((i * 37L) % 330))
                    .user(user)
                    .account(account)
                    .executionRating(winner ? 4 + i % 2 : followedPlan ? 3 : 2)
                    .setupTags(new ArrayList<>(List.of(SETUP_TAGS.get(i % SETUP_TAGS.size()))))
                    .build());
        }
        tradeRepository.saveAll(trades);

        account.setCurrentBalance(balance);
        accountRepository.save(account);
    }

    private void seedJournal(User user, List<LocalDate> days) {
        List<JournalEntry> entries = new ArrayList<>();
        // The most recent six trading days get a day-level reflection.
        for (int i = 0; i < JOURNAL_NOTES.size(); i++) {
            LocalDate day = days.get(days.size() - 1 - i);
            entries.add(JournalEntry.builder()
                    .user(user)
                    .entryDate(day)
                    .notes(JOURNAL_NOTES.get(i))
                    .htfBias(List.of("Bullish", "Bearish", "Neutral").get(i % 3))
                    .updatedAt(day.atTime(16, 30))
                    .build());
        }
        journalEntryRepository.saveAll(entries);
    }

    // The last 50 weekdays strictly before `today`, oldest first (index 49 is the most recent). Strictly
    // before, so a morning reset never leaves trades timestamped later today.
    static List<LocalDate> tradingDays(LocalDate today) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate day = today.minusDays(1);
        while (days.size() < TRADING_DAYS) {
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                days.add(0, day);
            }
            day = day.minusDays(1);
        }
        return days;
    }

    // A plausible price near each contract's recent range, wobbling a little from trade to trade.
    private static final Map<String, double[]> PRICE_BASE_AND_STEP = Map.ofEntries(
            Map.entry("ES", new double[] { 5900, 2.5 }), Map.entry("MES", new double[] { 5900, 2.5 }),
            Map.entry("NQ", new double[] { 20800, 10 }), Map.entry("MNQ", new double[] { 20800, 10 }),
            Map.entry("YM", new double[] { 43500, 20 }), Map.entry("MYM", new double[] { 43500, 20 }),
            Map.entry("RTY", new double[] { 2300, 1 }), Map.entry("M2K", new double[] { 2300, 1 }),
            Map.entry("CL", new double[] { 70, 0.1 }), Map.entry("MCL", new double[] { 70, 0.1 }),
            Map.entry("GC", new double[] { 2700, 1 }), Map.entry("MGC", new double[] { 2700, 1 }));

    private static double entryPrice(String ticker, int day, int index) {
        double[] baseAndStep = PRICE_BASE_AND_STEP.get(ticker);
        int wobble = (day * 13 + index * 7) % 41 - 20;
        return round2(baseAndStep[0] + wobble * baseAndStep[1]);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private User newDemoUser() {
        return User.builder()
                .email(properties.getEmail())
                .firstName("Demo")
                .lastName("Trader")
                // A BCrypt hash of a random value nobody ever sees: there is no password that logs in as
                // this user, and AuthenticationService also refuses demo users outright.
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role(Role.USER)
                .demo(true)
                .build();
    }

    // The reset deletes everything a user owns, so every entry point checks the flag first: a mistyped
    // DEMO_EMAIL pointing at a real account must fail loudly, not wipe that person's data.
    static void requireDemo(User user) {
        if (!user.isDemo()) {
            throw new IllegalStateException("Refusing to seed or reset a non-demo user.");
        }
    }
}
