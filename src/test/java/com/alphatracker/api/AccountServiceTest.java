package com.alphatracker.api;

import com.alphatracker.api.account.Account;
import com.alphatracker.api.account.AccountProgress;
import com.alphatracker.api.account.AccountRepository;
import com.alphatracker.api.account.AccountStatus;
import com.alphatracker.api.account.AccountType;
import com.alphatracker.api.account.AccountRequest;
import com.alphatracker.api.account.AccountResponse;
import com.alphatracker.api.account.AccountService;
import com.alphatracker.api.account.BackfillResult;
import com.alphatracker.api.account.DrawdownMode;
import com.alphatracker.api.account.DrawdownSnapshot;
import com.alphatracker.api.trade.Trade;
import com.alphatracker.api.trade.TradeRepository;
import com.alphatracker.api.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TradeRepository tradeRepository;

    @InjectMocks
    private AccountService accountService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("trader@alphatracker.com");
    }

    private Trade trade(double profitLoss, LocalDateTime tradeDate) {
        return Trade.builder().profitLoss(profitLoss).tradeDate(tradeDate).build();
    }

    @Test
    @DisplayName("PER_TRADE_CLOSE: high-water mark ratchets up on every trade close, floor tracks the peak")
    void testPerTradeCloseRatchetsOnEveryTrade() {
        Account account = Account.builder()
                .id(1L)
                .startingBalance(50000.0)
                .maxDrawdown(2000.0)
                .drawdownMode(DrawdownMode.PER_TRADE_CLOSE)
                .build();

        // Day 1: +2000 then -1500 (intraday peak of 52000 is given back to 50500)
        // Day 2: +500
        List<Trade> trades = List.of(
                trade(2000.0, LocalDateTime.of(2026, 1, 5, 10, 0)),
                trade(-1500.0, LocalDateTime.of(2026, 1, 5, 14, 0)),
                trade(500.0, LocalDateTime.of(2026, 1, 6, 10, 0)));

        when(tradeRepository.findAllByAccountIdOrderByTradeDateAsc(1L)).thenReturn(trades);

        DrawdownSnapshot snapshot = accountService.computeDrawdownSnapshot(account);

        // Every trade close counts, so the 52000 intraday peak from trade 1
        // survives as the high-water mark even though it was given back.
        assertEquals(52000.0, snapshot.highWaterMark(), 0.001);
        assertEquals(50000.0, snapshot.drawdownFloor(), 0.001);
    }

    @Test
    @DisplayName("END_OF_DAY: an intraday peak given back before the close never raises the high-water mark")
    void testEndOfDayIgnoresIntradayPeaks() {
        Account account = Account.builder()
                .id(2L)
                .startingBalance(50000.0)
                .maxDrawdown(2000.0)
                .drawdownMode(DrawdownMode.END_OF_DAY)
                .build();

        // Same trades as the PER_TRADE_CLOSE test: day 1 nets to +500 (50500),
        // so the 52000 intraday spike must NOT show up in the high-water mark.
        List<Trade> trades = List.of(
                trade(2000.0, LocalDateTime.of(2026, 1, 5, 10, 0)),
                trade(-1500.0, LocalDateTime.of(2026, 1, 5, 14, 0)),
                trade(5000.0, LocalDateTime.of(2026, 1, 6, 10, 0)));

        when(tradeRepository.findAllByAccountIdOrderByTradeDateAsc(2L)).thenReturn(trades);

        DrawdownSnapshot snapshot = accountService.computeDrawdownSnapshot(account);

        // Day 1 close = 50500, day 2 close = 55500 -> HWM is the day-2 close.
        assertEquals(55500.0, snapshot.highWaterMark(), 0.001);
        assertEquals(53500.0, snapshot.drawdownFloor(), 0.001);
    }

    @Test
    @DisplayName("trailingStopsAtBalance caps the floor once the trailing floor would exceed it")
    void testTrailingStopsAtBalanceCapsFloor() {
        Account account = Account.builder()
                .id(3L)
                .startingBalance(50000.0)
                .maxDrawdown(2000.0)
                .drawdownMode(DrawdownMode.PER_TRADE_CLOSE)
                .trailingStopsAtBalance(51000.0)
                .build();

        List<Trade> trades = List.of(trade(5000.0, LocalDateTime.of(2026, 1, 5, 10, 0)));

        when(tradeRepository.findAllByAccountIdOrderByTradeDateAsc(3L)).thenReturn(trades);

        DrawdownSnapshot snapshot = accountService.computeDrawdownSnapshot(account);

        // Uncapped floor would be 55000 - 2000 = 53000, but the lock-in balance
        // of 51000 is lower, so the floor freezes there instead of trailing up.
        assertEquals(55000.0, snapshot.highWaterMark(), 0.001);
        assertEquals(51000.0, snapshot.drawdownFloor(), 0.001);
    }

    @Test
    @DisplayName("createAccount makes a trader's very first account primary automatically")
    void testCreateAccountFirstAccountIsPrimary() {
        AccountRequest request = AccountRequest.builder()
                .name("Apex 50k #1")
                .firm("Apex")
                .startingBalance(50000.0)
                .maxDrawdown(2000.0)
                .build();

        // Default Mockito answer for an unstubbed List-returning method is an
        // empty list, so this reads as "the trader has no accounts yet."
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tradeRepository.findAllByAccountIdOrderByTradeDateAsc(any())).thenReturn(List.of());

        AccountResponse response = accountService.createAccount(request, mockUser);

        assertTrue(response.getIsPrimary());
    }

    @Test
    @DisplayName("setPrimaryAccount unsets the previous primary and sets the new one")
    void testSetPrimaryAccountSwapsPrimary() {
        Account oldPrimary = Account.builder().id(1L).isPrimary(true).user(mockUser).build();
        Account newPrimary = Account.builder().id(2L).isPrimary(false).user(mockUser)
                .startingBalance(50000.0).maxDrawdown(2000.0).build();

        when(accountRepository.findByIdAndUserId(2L, mockUser.getId())).thenReturn(Optional.of(newPrimary));
        when(accountRepository.findAllByUserIdAndIsPrimaryTrue(mockUser.getId())).thenReturn(List.of(oldPrimary));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tradeRepository.findAllByAccountIdOrderByTradeDateAsc(any())).thenReturn(List.of());

        AccountResponse response = accountService.setPrimaryAccount(2L, mockUser);

        assertFalse(oldPrimary.getIsPrimary());
        assertTrue(response.getIsPrimary());
        verify(accountRepository, times(1)).save(oldPrimary);
        verify(accountRepository, times(1)).save(newPrimary);
    }

    @Test
    @DisplayName("setPrimaryAccount throws when the account does not belong to the requesting user")
    void testSetPrimaryAccountRejectsUnownedAccount() {
        when(accountRepository.findByIdAndUserId(99L, mockUser.getId())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> accountService.setPrimaryAccount(99L, mockUser));
    }

    @Test
    @DisplayName("backfillUnassignedTrades reassigns every unassigned trade and rolls its P/L into the primary balance")
    void testBackfillReassignsTradesAndUpdatesBalance() {
        Account primary = Account.builder().id(1L).startingBalance(50000.0).maxDrawdown(2000.0)
                .currentBalance(50000.0).isPrimary(true).user(mockUser).build();
        Trade orphan1 = Trade.builder().id(10L).profitLoss(100.0).tradeDate(LocalDateTime.of(2026, 1, 5, 10, 0)).build();
        Trade orphan2 = Trade.builder().id(11L).profitLoss(-40.0).tradeDate(LocalDateTime.of(2026, 1, 6, 10, 0)).build();

        when(accountRepository.findAllByUserIdAndIsPrimaryTrue(mockUser.getId())).thenReturn(List.of(primary));
        when(tradeRepository.findAllByUserIdAndAccountIsNull(mockUser.getId())).thenReturn(List.of(orphan1, orphan2));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tradeRepository.findAllByAccountIdOrderByTradeDateAsc(any())).thenReturn(List.of());

        BackfillResult result = accountService.backfillUnassignedTrades(mockUser);

        assertEquals(2, result.tradesBackfilled());
        assertEquals(50060.0, result.account().getCurrentBalance(), 0.001);
        assertEquals(primary, orphan1.getAccount());
        assertEquals(primary, orphan2.getAccount());
        verify(tradeRepository, times(1)).saveAll(List.of(orphan1, orphan2));
    }

    // ---- profit target and drawdown breach (computeProgress) ----

    private Account evaluation(DrawdownMode mode, Double profitTarget) {
        return Account.builder().id(7L).accountType(AccountType.EVALUATION).startingBalance(50000.0)
                .maxDrawdown(2000.0).profitTarget(profitTarget).drawdownMode(mode).build();
    }

    private void history(Account account, Trade... trades) {
        when(tradeRepository.findAllByAccountIdOrderByTradeDateAsc(account.getId())).thenReturn(List.of(trades));
    }

    private static LocalDateTime jan(int day, int hour) {
        return LocalDateTime.of(2026, 1, day, hour, 0);
    }

    private static LocalDate janDay(int day) {
        return LocalDate.of(2026, 1, day);
    }

    @Test
    @DisplayName("Target progress is profit / target, and the target is reached on the trade that crosses it")
    void testTargetProgressAndTheCrossingTrade() {
        Account account = evaluation(DrawdownMode.PER_TRADE_CLOSE, 3000.0);

        history(account, trade(1000.0, jan(5, 10)), trade(1140.0, jan(6, 10)));
        AccountProgress partway = accountService.computeProgress(account);
        assertEquals(2140.0, partway.profit(), 0.001);
        assertEquals(0.7133, partway.targetProgress(), 0.00001);
        assertNull(partway.targetReachedOn());
        assertNull(partway.suggestedStatus());

        history(account, trade(1000.0, jan(5, 10)), trade(1140.0, jan(6, 10)), trade(900.0, jan(7, 10)));
        AccountProgress reached = accountService.computeProgress(account);
        assertEquals(janDay(7), reached.targetReachedOn());   // the trade that took profit from 2140 to 3040
        assertEquals(1.0133, reached.targetProgress(), 0.00001); // can exceed 1: 3040 / 3000
        assertEquals(AccountStatus.PASSED, reached.suggestedStatus());
    }

    @Test
    @DisplayName("END_OF_DAY: the target is judged at the day's close, so an intraday peak given back does not count")
    void testEndOfDayTargetIsJudgedAtTheClose() {
        Account account = evaluation(DrawdownMode.END_OF_DAY, 3000.0);

        // Day 1 touches +3500 but closes at +2500, short of the target. Day 2 closes at +3100.
        history(account,
                trade(3500.0, jan(5, 10)), trade(-1000.0, jan(5, 14)),
                trade(600.0, jan(6, 10)));

        AccountProgress progress = accountService.computeProgress(account);

        assertEquals(janDay(6), progress.targetReachedOn());
        assertNull(progress.breachedOn());
    }

    @Test
    @DisplayName("A breach is a closed-trade balance AT or below the floor: touching counts, and END_OF_DAY uses the previous close's floor")
    void testBreachRules() {
        // PER_TRADE_CLOSE: floor is 48000. 48001 is safe; 48000 exactly is blown.
        Account perTrade = evaluation(DrawdownMode.PER_TRADE_CLOSE, null);
        history(perTrade, trade(-1999.0, jan(5, 10)));
        assertNull(accountService.computeProgress(perTrade).breachedOn());
        history(perTrade, trade(-1999.0, jan(5, 10)), trade(-1.0, jan(6, 10)));
        assertEquals(janDay(6), accountService.computeProgress(perTrade).breachedOn());

        // END_OF_DAY: day 1 closes at 51000, so the floor in force for day 2 is 49000. A drop to 48500
        // breaches it even though the day later recovers to close at 50500: the plunge was real.
        Account eod = evaluation(DrawdownMode.END_OF_DAY, null);
        history(eod, trade(1000.0, jan(5, 10)), trade(-2500.0, jan(6, 10)), trade(2000.0, jan(6, 14)));
        assertEquals(janDay(6), accountService.computeProgress(eod).breachedOn());
        history(eod, trade(1000.0, jan(5, 10)), trade(-500.0, jan(6, 10)));   // 50500: comfortably above 49000
        assertNull(accountService.computeProgress(eod).breachedOn());
    }

    @Test
    @DisplayName("Whichever happened first wins: breach then recovery is FAILED; target then breach is PASSED")
    void testFirstEventWins() {
        Account account = evaluation(DrawdownMode.PER_TRADE_CLOSE, 3000.0);

        // Blown at 48000, then a +5500 day lifts profit to 3500: past the target, but the account was already dead.
        history(account, trade(-2000.0, jan(5, 10)), trade(5500.0, jan(6, 10)));
        AccountProgress blownFirst = accountService.computeProgress(account);
        assertEquals(AccountStatus.FAILED, blownFirst.suggestedStatus());
        assertEquals(janDay(5), blownFirst.breachedOn());
        assertEquals(janDay(6), blownFirst.targetReachedOn());   // both facts are still reported

        // Target hit on day 1 (53000), then a -5000 loss drops it below the new floor of 51000.
        history(account, trade(3000.0, jan(5, 10)), trade(-5000.0, jan(6, 10)));
        AccountProgress passedFirst = accountService.computeProgress(account);
        assertEquals(AccountStatus.PASSED, passedFirst.suggestedStatus());
        assertEquals(janDay(5), passedFirst.targetReachedOn());
        assertEquals(janDay(6), passedFirst.breachedOn());
    }

    @Test
    @DisplayName("Only evaluations have a target: a funded account (or one with no target) shows no progress, but a funded breach still counts")
    void testOnlyEvaluationsHaveATarget() {
        Account funded = Account.builder().id(8L).accountType(AccountType.FUNDED).startingBalance(50000.0)
                .maxDrawdown(2000.0).profitTarget(3000.0).drawdownMode(DrawdownMode.PER_TRADE_CLOSE).build();
        history(funded, trade(3500.0, jan(5, 10)));
        AccountProgress up = accountService.computeProgress(funded);
        assertNull(up.targetProgress());                 // the form pre-fills 3000, but a funded account isn't working toward it
        assertNull(up.targetReachedOn());
        assertNull(up.suggestedStatus());

        history(funded, trade(-2000.0, jan(5, 10)));
        assertEquals(AccountStatus.FAILED, accountService.computeProgress(funded).suggestedStatus());

        for (Double noTarget : new Double[] { null, 0.0 }) {
            Account evalWithoutTarget = evaluation(DrawdownMode.PER_TRADE_CLOSE, noTarget);
            history(evalWithoutTarget, trade(3500.0, jan(5, 10)));
            assertNull(accountService.computeProgress(evalWithoutTarget).targetProgress());
            assertNull(accountService.computeProgress(evalWithoutTarget).suggestedStatus());
        }
    }

    @Test
    @DisplayName("The progress facts are part of every account response")
    void testProgressReachesTheAccountResponse() {
        Account account = evaluation(DrawdownMode.PER_TRADE_CLOSE, 3000.0);
        account.setName("Apex 50k #1");
        account.setCurrentBalance(52140.0);
        when(accountRepository.findAllByUserId(1L)).thenReturn(List.of(account));
        history(account, trade(1000.0, jan(5, 10)), trade(1140.0, jan(6, 10)));

        AccountResponse response = accountService.getUserAccounts(mockUser).get(0);

        assertEquals(2140.0, response.getProfit(), 0.001);
        assertEquals(0.7133, response.getTargetProgress(), 0.00001);
        assertNull(response.getSuggestedStatus());
        assertEquals(52140.0, response.getHighWaterMark(), 0.001);   // the drawdown figures are still alongside
    }
}
