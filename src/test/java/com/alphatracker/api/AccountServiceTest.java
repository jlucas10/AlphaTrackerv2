package com.alphatracker.api;

import com.alphatracker.api.account.Account;
import com.alphatracker.api.account.AccountRepository;
import com.alphatracker.api.account.AccountRequest;
import com.alphatracker.api.account.AccountResponse;
import com.alphatracker.api.account.AccountService;
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

import static org.junit.jupiter.api.Assertions.*;
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
}
