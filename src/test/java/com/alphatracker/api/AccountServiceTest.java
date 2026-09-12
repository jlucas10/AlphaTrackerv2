package com.alphatracker.api;

import com.alphatracker.api.account.Account;
import com.alphatracker.api.account.AccountRepository;
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
    @DisplayName("A null drawdownMode on an existing account is treated as END_OF_DAY")
    void testNullDrawdownModeDefaultsToEndOfDay() {
        Account account = Account.builder()
                .id(4L)
                .startingBalance(50000.0)
                .maxDrawdown(2000.0)
                .drawdownMode(null)
                .build();

        List<Trade> trades = List.of(
                trade(2000.0, LocalDateTime.of(2026, 1, 5, 10, 0)),
                trade(-1500.0, LocalDateTime.of(2026, 1, 5, 14, 0)));

        when(tradeRepository.findAllByAccountIdOrderByTradeDateAsc(4L)).thenReturn(trades);

        DrawdownSnapshot snapshot = accountService.computeDrawdownSnapshot(account);

        // Behaves like the END_OF_DAY test above: the intraday 52000 spike
        // never counts, only the day's close of 50500 does.
        assertEquals(50500.0, snapshot.highWaterMark(), 0.001);
    }

    @Test
    @DisplayName("createAccount rejects a trailingStopsAtBalance that isn't positive")
    void testCreateAccountRejectsNonPositiveTrailingStop() {
        AccountRequest request = AccountRequest.builder()
                .name("Apex 50k #1")
                .firm("Apex")
                .startingBalance(50000.0)
                .maxDrawdown(2000.0)
                .trailingStopsAtBalance(0.0)
                .build();

        assertThrows(IllegalArgumentException.class, () -> accountService.createAccount(request, mockUser));
    }

    @Test
    @DisplayName("createAccount defaults drawdownMode to END_OF_DAY when not specified")
    void testCreateAccountDefaultsDrawdownMode() {
        AccountRequest request = AccountRequest.builder()
                .name("Apex 50k #1")
                .firm("Apex")
                .startingBalance(50000.0)
                .maxDrawdown(2000.0)
                .build();

        when(accountRepository.save(org.mockito.ArgumentMatchers.any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        // The saved account has no id assigned by this mock, so the lookup
        // arrives with a null accountId.
        when(tradeRepository.findAllByAccountIdOrderByTradeDateAsc(org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(List.of());

        AccountResponse response = accountService.createAccount(request, mockUser);

        assertEquals(DrawdownMode.END_OF_DAY, response.getDrawdownMode());
        assertEquals(50000.0, response.getHighWaterMark(), 0.001);
        assertEquals(48000.0, response.getDrawdownFloor(), 0.001);
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
    @DisplayName("createAccount does not make a second account primary")
    void testCreateAccountSecondAccountIsNotPrimary() {
        Account existing = Account.builder().id(1L).isPrimary(true).build();
        AccountRequest request = AccountRequest.builder()
                .name("Topstep 100k #1")
                .firm("Topstep")
                .startingBalance(100000.0)
                .maxDrawdown(3000.0)
                .build();

        when(accountRepository.findAllByUserId(mockUser.getId())).thenReturn(List.of(existing));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tradeRepository.findAllByAccountIdOrderByTradeDateAsc(any())).thenReturn(List.of());

        AccountResponse response = accountService.createAccount(request, mockUser);

        assertFalse(response.getIsPrimary());
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
    @DisplayName("setPrimaryAccount is a no-op on the primary flag when the account is already primary")
    void testSetPrimaryAccountAlreadyPrimary() {
        Account account = Account.builder().id(1L).isPrimary(true).user(mockUser)
                .startingBalance(50000.0).maxDrawdown(2000.0).build();

        when(accountRepository.findByIdAndUserId(1L, mockUser.getId())).thenReturn(Optional.of(account));
        when(accountRepository.findAllByUserIdAndIsPrimaryTrue(mockUser.getId())).thenReturn(List.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tradeRepository.findAllByAccountIdOrderByTradeDateAsc(any())).thenReturn(List.of());

        AccountResponse response = accountService.setPrimaryAccount(1L, mockUser);

        assertTrue(response.getIsPrimary());
        // The account is both the target and the (only) currently-primary
        // account, so the "unset others" loop must skip it rather than
        // toggling it off right before the code below turns it back on.
        assertTrue(account.getIsPrimary());
    }

    @Test
    @DisplayName("setPrimaryAccount throws when the account does not belong to the requesting user")
    void testSetPrimaryAccountRejectsUnownedAccount() {
        when(accountRepository.findByIdAndUserId(99L, mockUser.getId())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> accountService.setPrimaryAccount(99L, mockUser));
    }

    @Test
    @DisplayName("backfillUnassignedTrades throws when the user has no primary account")
    void testBackfillThrowsWithoutPrimaryAccount() {
        when(accountRepository.findAllByUserIdAndIsPrimaryTrue(mockUser.getId())).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> accountService.backfillUnassignedTrades(mockUser));
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

    @Test
    @DisplayName("backfillUnassignedTrades is a no-op when there are no unassigned trades")
    void testBackfillNoOpWhenNothingUnassigned() {
        Account primary = Account.builder().id(1L).startingBalance(50000.0).maxDrawdown(2000.0)
                .currentBalance(50000.0).isPrimary(true).user(mockUser).build();

        when(accountRepository.findAllByUserIdAndIsPrimaryTrue(mockUser.getId())).thenReturn(List.of(primary));
        when(tradeRepository.findAllByUserIdAndAccountIsNull(mockUser.getId())).thenReturn(List.of());
        when(tradeRepository.findAllByAccountIdOrderByTradeDateAsc(any())).thenReturn(List.of());

        BackfillResult result = accountService.backfillUnassignedTrades(mockUser);

        assertEquals(0, result.tradesBackfilled());
        assertEquals(50000.0, result.account().getCurrentBalance(), 0.001);
        verify(accountRepository, never()).save(any());
        verify(tradeRepository, never()).saveAll(any());
    }
}
