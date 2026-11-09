package com.alphatracker.api;

import com.alphatracker.api.account.Account;
import com.alphatracker.api.account.AccountRepository;
import com.alphatracker.api.account.AccountStatus;
import com.alphatracker.api.account.AccountType;
import com.alphatracker.api.account.DrawdownMode;
import com.alphatracker.api.importer.ImportConfirmResponse;
import com.alphatracker.api.importer.ImportService;
import com.alphatracker.api.importer.ImportSource;
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// The REAL stack against the local Postgres: real importer, real TradeService, real AccountService replay,
// real unique key. @Transactional rolls everything back after each test, so nothing persists.
//
// Expected balances are worked out BY HAND from the sample, not read from the code under test. The sample's
// daily P&L is: 10/13 -550, 10/15 -392.5, 10/16 -500, 10/19 +512.5, 10/20 +1030, 10/21 -282.5, 10/22 -457.5,
// 10/23 +747.5, 10/26 -460, 10/27 -137.5, 10/30 +985, so the closing balances (from 50,000) end at 50,495,
// which is also the high-water mark (no later day exceeds it), floor = 50,495 - 2,000 = 48,495.
@SpringBootTest
@Transactional
public class ImportConfirmIntegrationTest {

    private static final String HEADER = "Id,ContractName,EnteredAt,ExitedAt,EntryPrice,ExitPrice,Fees,PnL,Size,Type,TradeDay,TradeDuration,Commissions";

    @Autowired
    private ImportService importService;
    @Autowired
    private TradeService tradeService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private TradeRepository tradeRepository;

    private User alice;
    private User bob;
    private Account aliceEod;

    @BeforeEach
    void setUp() {
        alice = persistUser("alice");
        bob = persistUser("bob");
        aliceEod = persistAccount(alice, "Alice EOD", DrawdownMode.END_OF_DAY);
    }

    // ---- helpers ----

    private User persistUser(String name) {
        return userRepository.save(User.builder().email(name + "-" + UUID.randomUUID() + "@example.test")
                .password("hashed").firstName(name).role(Role.USER).build());
    }

    private Account persistAccount(User owner, String name, DrawdownMode mode) {
        return accountRepository.save(Account.builder().name(name).firm("Topstep").accountType(AccountType.EVALUATION)
                .startingBalance(50000.0).currentBalance(50000.0).maxDrawdown(2000.0).profitTarget(3000.0)
                .drawdownMode(mode).status(AccountStatus.ACTIVE).user(owner).build());
    }

    private static InputStream sample() {
        return TopStepXParserTest.class.getResourceAsStream("/imports/topstepx/trades_export.csv");
    }

    private static InputStream csv(String... lines) {
        return new ByteArrayInputStream((String.join("\r\n", lines) + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    // MNQ, 5 contracts, so every point is $10. Long: exit - entry.
    private static String row(String id, String entered, String exited, String entry, String exit, String type) {
        return String.join(",", id, "MNQZ6", entered, exited, entry, exit, "0", "0", "5", type,
                "10/13/2026 00:00:00 -05:00", "00:05:00", "0");
    }

    private ImportConfirmResponse confirm(User user, List<Long> accountIds, InputStream file) {
        return importService.confirm(ImportSource.TOPSTEPX, accountIds, file, Map.of(), null, user);
    }

    private List<Trade> tradesOf(Account account) {
        return tradeRepository.findAllByAccountIdOrderByTradeDateAsc(account.getId());
    }

    // ---- balance and drawdown ----

    @Test
    @DisplayName("sample import: 21 trades land, balance is exactly 50,495, high-water mark 50,495, floor 48,495")
    void sampleImportBalanceAndDrawdown() {
        ImportConfirmResponse response = confirm(alice, List.of(aliceEod.getId()), sample());

        assertEquals(21, response.created());
        assertEquals(0, response.skipped());
        assertEquals(0, response.failed());
        ImportConfirmResponse.AccountResult result = response.accounts().get(0);
        assertEquals(50495.0, result.currentBalance());
        assertEquals(495.0, result.profit());
        assertEquals(50495.0, result.highWaterMark());
        assertEquals(48495.0, result.drawdownFloor());
        assertNull(result.suggestedStatus());
        assertNull(result.breachedOn());

        // The stored account agrees with the response, and every trade is attached to it.
        assertEquals(50495.0, accountRepository.findById(aliceEod.getId()).orElseThrow().getCurrentBalance());
        assertEquals(21, tradesOf(aliceEod).size());
    }

    @Test
    @DisplayName("an imported trade is stored exactly like a hand-logged one, plus its source and broker id; followedPlan is unspecified")
    void importedTradeFields() {
        confirm(alice, List.of(aliceEod.getId()), sample());

        Trade first = tradesOf(aliceEod).get(0);
        assertEquals("MNQ", first.getTicker());
        assertEquals("LONG", first.getDirection());
        assertEquals(30756.75, first.getEntryPrice());
        assertEquals(30723.75, first.getExitPrice());
        assertEquals(5, first.getContracts());
        assertEquals(-330.0, first.getProfitLoss());
        assertEquals(LocalDateTime.of(2026, 10, 13, 7, 45, 11), first.getTradeDate()); // ENTRY time, LA
        assertEquals(ImportSource.TOPSTEPX, first.getSource());
        assertEquals("3198364077", first.getExternalId());
        assertEquals(alice.getId(), first.getUser().getId());
        assertNull(first.getFollowedPlan(), "an import must never claim the plan was followed");
    }

    @Test
    @DisplayName("a batch dated BEFORE trades already on the account: balance and drawdown reflect all of it, in date order")
    void backDatedBatchIsReplayedInDateOrder() {
        // A hand-logged +$1,000 trade on Nov 2 (100 -> 200 on 5 MNQ). The import is all October: older.
        tradeService.logTrade(TradeRequest.builder().ticker("MNQ").direction("LONG").entryPrice(100.0).exitPrice(200.0)
                .contracts(5).tradeDate(LocalDateTime.of(2026, 11, 2, 9, 0)).accountId(aliceEod.getId()).build(), alice);
        assertEquals(51000.0, accountRepository.findById(aliceEod.getId()).orElseThrow().getCurrentBalance());

        ImportConfirmResponse response = confirm(alice, List.of(aliceEod.getId()), sample());

        ImportConfirmResponse.AccountResult result = response.accounts().get(0);
        assertEquals(51495.0, result.currentBalance());                 // 50,000 + 495 + 1,000
        assertEquals(51495.0, result.highWaterMark());                  // Nov 2 closes the day on top
        assertEquals(49495.0, result.drawdownFloor());
        List<Trade> replayOrder = tradesOf(aliceEod);
        assertEquals(22, replayOrder.size());
        assertEquals(LocalDateTime.of(2026, 10, 13, 7, 45, 11), replayOrder.get(0).getTradeDate());   // oldest first
        assertEquals(LocalDateTime.of(2026, 11, 2, 9, 0), replayOrder.get(21).getTradeDate());        // hand-logged last
    }

    @Test
    @DisplayName("drawdown mode is honoured on imported trades: +$500 then -$400 the same day gives HWM 50,500 per trade but 50,100 end-of-day")
    void drawdownModesOnImportedTrades() {
        Account perTrade = persistAccount(alice, "Alice per-trade", DrawdownMode.PER_TRADE_CLOSE);
        // Same day: +50 pts (+$500) at 08:00, then -40 pts (-$400) at 09:00.
        InputStream file = csv(HEADER,
                row("1", "10/13/2026 08:00:00 -07:00", "10/13/2026 08:05:00 -07:00", "100", "150", "Long"),
                row("2", "10/13/2026 09:00:00 -07:00", "10/13/2026 09:05:00 -07:00", "100", "60", "Long"));

        ImportConfirmResponse response = confirm(alice, List.of(aliceEod.getId(), perTrade.getId()), file);

        ImportConfirmResponse.AccountResult eod = response.accounts().get(0);
        ImportConfirmResponse.AccountResult ptc = response.accounts().get(1);
        assertEquals(50100.0, eod.currentBalance());
        assertEquals(50100.0, eod.highWaterMark());      // judged at the day's close
        assertEquals(48100.0, eod.drawdownFloor());
        assertEquals(50100.0, ptc.currentBalance());
        assertEquals(50500.0, ptc.highWaterMark());      // the intraday peak counts
        assertEquals(48500.0, ptc.drawdownFloor());
    }

    @Test
    @DisplayName("an imported batch that takes the balance to the floor is reported as a breach (suggested FAILED), not auto-applied")
    void importedBreachIsDetected() {
        // -200 pts x 5 MNQ x $2 = -$2,000: 50,000 -> 48,000 = exactly the floor (touching counts).
        ImportConfirmResponse response = confirm(alice, List.of(aliceEod.getId()), csv(HEADER,
                row("1", "10/13/2026 08:00:00 -07:00", "10/13/2026 08:05:00 -07:00", "300", "100", "Long")));

        ImportConfirmResponse.AccountResult result = response.accounts().get(0);
        assertEquals(48000.0, result.currentBalance());
        assertEquals(AccountStatus.FAILED, result.suggestedStatus());
        assertEquals(LocalDate.of(2026, 10, 13), result.breachedOn());
        assertEquals(AccountStatus.ACTIVE, accountRepository.findById(aliceEod.getId()).orElseThrow().currentStatus(),
                "the trader confirms a status change; the import does not make it");
    }

    // ---- duplicates ----

    @Test
    @DisplayName("importing the same file twice: the second run creates nothing and the balance does not move")
    void reimportCreatesNothing() {
        confirm(alice, List.of(aliceEod.getId()), sample());

        ImportConfirmResponse again = confirm(alice, List.of(aliceEod.getId()), sample());

        assertEquals(0, again.created());
        assertEquals(21, again.skipped());
        assertEquals(21, tradesOf(aliceEod).size());
        assertEquals(50495.0, again.accounts().get(0).currentBalance());
        assertEquals(50495.0, accountRepository.findById(aliceEod.getId()).orElseThrow().getCurrentBalance());
    }

    @Test
    @DisplayName("copy-trading: the same file goes into a second account; duplicates are judged per account")
    void sameFileIntoSecondAccount() {
        Account second = persistAccount(alice, "Alice B", DrawdownMode.END_OF_DAY);
        confirm(alice, List.of(aliceEod.getId()), sample());

        ImportConfirmResponse both = confirm(alice, List.of(aliceEod.getId(), second.getId()), sample());

        assertEquals(21, both.created());   // only the second account gets trades
        assertEquals(21, both.skipped());   // the first already had them
        assertEquals(21, both.accounts().get(0).skipped());
        assertEquals(21, both.accounts().get(1).created());
        assertEquals(21, tradesOf(aliceEod).size());
        assertEquals(21, tradesOf(second).size());
        assertEquals(50495.0, both.accounts().get(1).currentBalance());
    }

    @Test
    @DisplayName("one request into two fresh accounts creates the trades in both, each with its own balance")
    void twoAccountsAtOnce() {
        Account second = persistAccount(alice, "Alice B", DrawdownMode.END_OF_DAY);

        ImportConfirmResponse response = confirm(alice, List.of(aliceEod.getId(), second.getId()), sample());

        assertEquals(42, response.created());
        assertEquals(50495.0, response.accounts().get(0).currentBalance());
        assertEquals(50495.0, response.accounts().get(1).currentBalance());
    }

    // ---- ownership ----

    @Test
    @DisplayName("ownership: user A cannot import into user B's account; B's account is untouched")
    void cannotImportIntoSomeoneElsesAccount() {
        Account bobs = persistAccount(bob, "Bob 50k", DrawdownMode.END_OF_DAY);

        assertThrows(SecurityException.class, () -> confirm(alice, List.of(bobs.getId()), sample()));

        assertTrue(tradesOf(bobs).isEmpty());
        assertEquals(50000.0, accountRepository.findById(bobs.getId()).orElseThrow().getCurrentBalance());
    }

    @Test
    @DisplayName("ownership: one foreign account among owned ones rejects the whole request, so nothing lands in the owned one either")
    void foreignAccountAmongOwnedWritesNothing() {
        Account bobs = persistAccount(bob, "Bob 50k", DrawdownMode.END_OF_DAY);

        assertThrows(SecurityException.class,
                () -> confirm(alice, List.of(aliceEod.getId(), bobs.getId()), sample()));

        assertTrue(tradesOf(aliceEod).isEmpty());
        assertEquals(50000.0, accountRepository.findById(aliceEod.getId()).orElseThrow().getCurrentBalance());
    }

    // ---- followed plan ----

    @Test
    @DisplayName("followedPlan: per-trade answers win, the default covers the rest, and with neither a trade stays unspecified")
    void followedPlanChoices() {
        InputStream file = csv(HEADER,
                row("1", "10/13/2026 08:00:00 -07:00", "10/13/2026 08:05:00 -07:00", "100", "101", "Long"),
                row("2", "10/13/2026 09:00:00 -07:00", "10/13/2026 09:05:00 -07:00", "100", "101", "Long"),
                row("3", "10/13/2026 10:00:00 -07:00", "10/13/2026 10:05:00 -07:00", "100", "101", "Long"));

        importService.confirm(ImportSource.TOPSTEPX, List.of(aliceEod.getId()), file,
                Map.of("1", true, "2", false), null, alice);

        List<Trade> trades = tradesOf(aliceEod);
        assertEquals(Boolean.TRUE, trades.get(0).getFollowedPlan());
        assertEquals(Boolean.FALSE, trades.get(1).getFollowedPlan());
        assertNull(trades.get(2).getFollowedPlan());
    }

    @Test
    @DisplayName("followedPlan: a default applies to every trade not answered individually")
    void followedPlanDefault() {
        InputStream file = csv(HEADER,
                row("1", "10/13/2026 08:00:00 -07:00", "10/13/2026 08:05:00 -07:00", "100", "101", "Long"),
                row("2", "10/13/2026 09:00:00 -07:00", "10/13/2026 09:05:00 -07:00", "100", "101", "Long"));

        importService.confirm(ImportSource.TOPSTEPX, List.of(aliceEod.getId()), file, Map.of("2", false), true, alice);

        List<Trade> trades = tradesOf(aliceEod);
        assertEquals(Boolean.TRUE, trades.get(0).getFollowedPlan());
        assertEquals(Boolean.FALSE, trades.get(1).getFollowedPlan());
    }

    // ---- bad rows, midnight ----

    @Test
    @DisplayName("rejected rows are reported as failed and do not stop the valid ones")
    void failedRowsDoNotBlockValidOnes() {
        InputStream file = csv(HEADER,
                row("1", "10/13/2026 08:00:00 -07:00", "10/13/2026 08:05:00 -07:00", "100", "110", "Long"),
                "2,XYZZ6,10/13/2026 08:00:00 -07:00,10/13/2026 08:05:00 -07:00,100,110,0,0,5,Long,x,x,0");

        ImportConfirmResponse response = confirm(alice, List.of(aliceEod.getId()), file);

        assertEquals(1, response.created());
        assertEquals(1, response.failed());
        assertEquals(3, response.errors().get(0).line());
        assertTrue(response.errors().get(0).message().contains("Unsupported contract"));
        assertEquals(50100.0, response.accounts().get(0).currentBalance());
    }

    @Test
    @DisplayName("midnight: 23:59:59 and 00:00:00 import onto different journal days, and the journal's own day query agrees")
    void midnightBoundaryLandsOnTheRightJournalDay() {
        confirm(alice, List.of(aliceEod.getId()), csv(HEADER,
                row("1", "11/03/2026 23:59:59 -08:00", "11/04/2026 00:30:00 -08:00", "100", "101", "Long"),
                row("2", "11/04/2026 00:00:00 -08:00", "11/04/2026 00:30:00 -08:00", "100", "102", "Long")));

        List<Trade> nov3 = tradeService.getTradesForDate(LocalDate.of(2026, 11, 3), alice);
        List<Trade> nov4 = tradeService.getTradesForDate(LocalDate.of(2026, 11, 4), alice);

        assertEquals(List.of("1"), nov3.stream().map(Trade::getExternalId).toList());
        assertEquals(List.of("2"), nov4.stream().map(Trade::getExternalId).toList());
    }

    @Test
    @DisplayName("deleting an imported trade rolls the balance back like any other trade")
    void deletingAnImportedTradeUndoesItsBalance() {
        confirm(alice, List.of(aliceEod.getId()), sample());
        Trade first = tradesOf(aliceEod).get(0); // -330

        tradeService.deleteTrade(first.getId(), alice);

        assertEquals(50825.0, accountRepository.findById(aliceEod.getId()).orElseThrow().getCurrentBalance());
    }
}
