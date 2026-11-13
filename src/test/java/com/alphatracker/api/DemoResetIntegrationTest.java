package com.alphatracker.api;

import com.alphatracker.api.account.Account;
import com.alphatracker.api.account.AccountRepository;
import com.alphatracker.api.account.AccountResponse;
import com.alphatracker.api.account.AccountService;
import com.alphatracker.api.account.AccountType;
import com.alphatracker.api.account.DrawdownMode;
import com.alphatracker.api.account.DrawdownSnapshot;
import com.alphatracker.api.demo.DemoResetService;
import com.alphatracker.api.demo.DemoSeedService;
import com.alphatracker.api.journal.JournalEntryRepository;
import com.alphatracker.api.trade.Trade;
import com.alphatracker.api.trade.TradeRepository;
import com.alphatracker.api.trade.TradeRequest;
import com.alphatracker.api.trade.TradeService;
import com.alphatracker.api.user.Role;
import com.alphatracker.api.user.User;
import com.alphatracker.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

// The REAL stack against the local Postgres, rolled back after each test. A throwaway demo email keeps this
// from ever touching a real demo account in the dev database.
@SpringBootTest(properties = "application.demo.email=demo-test@example.test")
@Transactional
public class DemoResetIntegrationTest {

    private static final Set<String> SUPPORTED = Set.of(
            "ES", "MES", "NQ", "MNQ", "YM", "MYM", "RTY", "M2K", "CL", "MCL", "GC", "MGC");

    @Autowired
    private DemoSeedService seedService;
    @Autowired
    private DemoResetService resetService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private TradeService tradeService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private TradeRepository tradeRepository;
    @Autowired
    private JournalEntryRepository journalEntryRepository;

    private User demo;

    @BeforeEach
    void setUp() {
        demo = seedService.ensureSeeded();
    }

    private List<Account> accountsOf(User user) {
        return accountRepository.findAllByUserId(user.getId());
    }

    private Account accountNamed(String name) {
        return accountsOf(demo).stream().filter(a -> a.getName().equals(name)).findFirst().orElseThrow();
    }

    // ---- the seed ----

    @Test
    @DisplayName("seed: a flagged demo user with an unusable password, 3 accounts using both trailing modes")
    void seedCreatesFlaggedUserAndAccounts() {
        assertTrue(demo.isDemo());
        assertEquals("demo-test@example.test", demo.getEmail());
        assertTrue(demo.getPassword().startsWith("$2"), "stored as a BCrypt hash of a value nobody knows");

        List<Account> accounts = accountsOf(demo);
        assertEquals(3, accounts.size());
        assertEquals(Set.of(DrawdownMode.END_OF_DAY, DrawdownMode.PER_TRADE_CLOSE),
                accounts.stream().map(Account::getDrawdownMode).collect(Collectors.toSet()));
        assertEquals(1, accounts.stream().filter(a -> Boolean.TRUE.equals(a.getIsPrimary())).count());
        assertTrue(accounts.stream().anyMatch(a -> a.getAccountType() == AccountType.FUNDED));
    }

    @Test
    @DisplayName("seed: ~10 weeks of weekday trades over many supported contracts, with both winners and losers")
    void seedTradesAreRealistic() {
        List<Trade> trades = tradeRepository.findByUserId(demo.getId());

        assertTrue(trades.size() >= 80, "expected a few months of activity, got " + trades.size());
        Set<String> tickers = trades.stream().map(Trade::getTicker).collect(Collectors.toSet());
        assertTrue(SUPPORTED.containsAll(tickers));
        assertTrue(tickers.size() >= 8, "expected several contracts, got " + tickers);
        assertTrue(trades.stream().anyMatch(t -> t.getProfitLoss() > 0));
        assertTrue(trades.stream().anyMatch(t -> t.getProfitLoss() < 0));
        assertTrue(trades.stream().anyMatch(t -> Boolean.FALSE.equals(t.getFollowedPlan())));

        LocalDate newest = trades.stream().map(t -> t.getTradeDate().toLocalDate()).max(LocalDate::compareTo).orElseThrow();
        LocalDate oldest = trades.stream().map(t -> t.getTradeDate().toLocalDate()).min(LocalDate::compareTo).orElseThrow();
        assertFalse(newest.isBefore(LocalDate.now().minusDays(4)), "newest trade should be recent, was " + newest);
        assertTrue(newest.isBefore(LocalDate.now()), "no trades dated today or later");
        assertTrue(oldest.isBefore(LocalDate.now().minusWeeks(9)), "history should span ~10 weeks, oldest " + oldest);
        assertTrue(trades.stream().allMatch(t -> {
            DayOfWeek d = t.getTradeDate().getDayOfWeek();
            return d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY;
        }));
    }

    @Test
    @DisplayName("seed: every account's stored balance equals start + the sum of its trades' P&L, and none is breached")
    void seedBalancesAreConsistentAndUnbreached() {
        for (Account account : accountsOf(demo)) {
            double sum = tradeRepository.findAllByAccountIdOrderByTradeDateAsc(account.getId()).stream()
                    .mapToDouble(Trade::getProfitLoss).sum();
            assertEquals(account.getStartingBalance() + sum, account.getCurrentBalance(), 0.01, account.getName());
            assertNull(accountService.computeProgress(account).breachedOn(), account.getName() + " must not be breached");
        }
    }

    @Test
    @DisplayName("seed: the per-trade account sits just above its drawdown floor, so the engine has something to show")
    void seedHasAnAccountNearItsFloor() {
        Account topstep = accountNamed("Topstep 50K");
        DrawdownSnapshot snapshot = accountService.computeDrawdownSnapshot(topstep);

        double cushion = topstep.getCurrentBalance() - snapshot.drawdownFloor();
        assertTrue(cushion > 0 && cushion < 500, "cushion above the floor was " + cushion);
        assertTrue(snapshot.highWaterMark() > topstep.getStartingBalance(), "it ran up before sliding back");

        // And the others are comfortable, so the contrast is visible.
        Account apex = accountNamed("Apex 50K #1");
        assertTrue(apex.getCurrentBalance() - accountService.computeDrawdownSnapshot(apex).drawdownFloor() > 1500);
    }

    @Test
    @DisplayName("seed is idempotent: calling ensureSeeded again changes nothing")
    void seedIsIdempotent() {
        long accountsBefore = accountsOf(demo).size();
        long tradesBefore = tradeRepository.findByUserId(demo.getId()).size();

        User again = seedService.ensureSeeded();

        assertEquals(demo.getId(), again.getId());
        assertEquals(accountsBefore, accountsOf(demo).size());
        assertEquals(tradesBefore, tradeRepository.findByUserId(demo.getId()).size());
    }

    // ---- the reset ----

    @Test
    @DisplayName("reset restores the seed state: edits, deletions and new rows are undone, balances return to the seed")
    void resetRestoresSeedState() {
        int accountCount = accountsOf(demo).size();
        int tradeCount = tradeRepository.findByUserId(demo.getId()).size();
        int journalCount = journalEntryRepository.findAllByUser_Id(demo.getId()).size();
        double apexBalance = accountNamed("Apex 50K #1").getCurrentBalance();
        double topstepBalance = accountNamed("Topstep 50K").getCurrentBalance();

        // A visitor makes a mess: logs a big loser, deletes a trade, adds an account, corrupts a balance.
        Account apex = accountNamed("Apex 50K #1");
        tradeService.logTrade(TradeRequest.builder().ticker("ES").direction("LONG").entryPrice(5000.0)
                .exitPrice(4900.0).contracts(5).accountId(apex.getId()).build(), demo);
        tradeService.deleteTrade(tradeRepository.findByUserId(demo.getId()).get(0).getId(), demo);
        accountRepository.save(Account.builder().name("Visitor Junk").firm("X").accountType(AccountType.EVALUATION)
                .startingBalance(1.0).currentBalance(1.0).maxDrawdown(1.0).user(demo).build());
        Account topstep = accountNamed("Topstep 50K");
        topstep.setCurrentBalance(1.0);
        accountRepository.save(topstep);

        resetService.reset();

        assertEquals(accountCount, accountsOf(demo).size());
        assertTrue(accountsOf(demo).stream().noneMatch(a -> a.getName().equals("Visitor Junk")));
        assertEquals(tradeCount, tradeRepository.findByUserId(demo.getId()).size());
        assertEquals(journalCount, journalEntryRepository.findAllByUser_Id(demo.getId()).size());
        assertEquals(apexBalance, accountNamed("Apex 50K #1").getCurrentBalance(), 0.01);
        assertEquals(topstepBalance, accountNamed("Topstep 50K").getCurrentBalance(), 0.01);
    }

    @Test
    @DisplayName("reset shifts the dates: run 'in the future', the whole history moves forward with it")
    void resetShiftsDatesToStayRecent() {
        LocalDate later = LocalDate.now().plusDays(30);

        resetService.reset(later);

        LocalDate newest = tradeRepository.findByUserId(demo.getId()).stream()
                .map(t -> t.getTradeDate().toLocalDate()).max(LocalDate::compareTo).orElseThrow();
        assertFalse(newest.isBefore(later.minusDays(4)), "newest trade " + newest + " should be within days of " + later);
        assertTrue(newest.isBefore(later));
    }

    @Test
    @DisplayName("reset refuses to touch a user that is not flagged demo, and leaves their data alone")
    void resetRefusesNonDemoUser() {
        // Re-point the demo email at a real (unflagged) user: a misconfigured DEMO_EMAIL must not wipe them.
        demo.setDemo(false);
        userRepository.saveAndFlush(demo);
        int tradeCount = tradeRepository.findByUserId(demo.getId()).size();

        assertThrows(IllegalStateException.class, () -> resetService.reset());
        assertThrows(IllegalStateException.class, () -> seedService.ensureSeeded());
        assertEquals(tradeCount, tradeRepository.findByUserId(demo.getId()).size());
    }

    // ---- ownership: the demo user is an ordinary user as far as other people's data goes ----

    private User persistOtherUser() {
        return userRepository.save(User.builder().email("other-" + UUID.randomUUID() + "@example.test")
                .password("hashed").firstName("Other").role(Role.USER).build());
    }

    private Account persistAccount(User owner) {
        return accountRepository.save(Account.builder().name("Other 50K").firm("Topstep")
                .accountType(AccountType.EVALUATION).startingBalance(50000.0).currentBalance(50000.0)
                .maxDrawdown(2000.0).drawdownMode(DrawdownMode.END_OF_DAY).user(owner).build());
    }

    @Test
    @DisplayName("the demo user cannot list, read, trade into or change another user's account or trades")
    void demoUserCannotTouchAnotherUsersData() {
        User other = persistOtherUser();
        Account otherAccount = persistAccount(other);
        Trade otherTrade = tradeService.logTrade(TradeRequest.builder().ticker("MNQ").direction("LONG")
                .entryPrice(20000.0).exitPrice(20010.0).contracts(1).accountId(otherAccount.getId())
                .tradeDate(LocalDateTime.now().minusDays(1)).build(), other);

        // Not visible in lists...
        assertTrue(accountService.getUserAccounts(demo).stream()
                .map(AccountResponse::getId).noneMatch(id -> id.equals(otherAccount.getId())));
        assertTrue(tradeService.getTradesForUser(demo, null).stream()
                .noneMatch(t -> t.getId().equals(otherTrade.getId())));
        assertTrue(tradeService.getTradesForUser(demo, otherAccount.getId()).isEmpty());

        // ...and not reachable by id.
        assertThrows(SecurityException.class, () -> tradeService.getTradeById(otherTrade.getId(), demo));
        assertThrows(SecurityException.class, () -> tradeService.deleteTrade(otherTrade.getId(), demo));
        assertThrows(IllegalArgumentException.class, () -> tradeService.logTrade(TradeRequest.builder()
                .ticker("MNQ").direction("LONG").entryPrice(1.0).exitPrice(2.0).contracts(1)
                .accountId(otherAccount.getId()).build(), demo));
        assertThrows(IllegalArgumentException.class,
                () -> accountService.setPrimaryAccount(otherAccount.getId(), demo));

        // The other user's data is exactly as it was.
        assertEquals(50020.0, accountRepository.findById(otherAccount.getId()).orElseThrow().getCurrentBalance());
        assertEquals(1, tradeRepository.findByUserId(other.getId()).size());
    }

    @Test
    @DisplayName("resetting the demo account does not touch any other user's data")
    void resetLeavesOtherUsersAlone() {
        User other = persistOtherUser();
        Account otherAccount = persistAccount(other);
        tradeService.logTrade(TradeRequest.builder().ticker("ES").direction("SHORT").entryPrice(5000.0)
                .exitPrice(4990.0).contracts(1).accountId(otherAccount.getId()).build(), other);

        resetService.reset();

        assertEquals(1, accountsOf(other).size());
        assertEquals(1, tradeRepository.findByUserId(other.getId()).size());
        assertEquals(50500.0, accountRepository.findById(otherAccount.getId()).orElseThrow().getCurrentBalance());
    }
}
