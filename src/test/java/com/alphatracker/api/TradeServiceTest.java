package com.alphatracker.api;

import com.alphatracker.api.account.Account;
import com.alphatracker.api.account.AccountRepository;
import com.alphatracker.api.trade.Trade;
import com.alphatracker.api.trade.TradeRepository;
import com.alphatracker.api.trade.TradeRequest;
import com.alphatracker.api.trade.TradeService;
import com.alphatracker.api.trade.TradeUpdateRequest;
import com.alphatracker.api.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TradeServiceTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private TradeService tradeService;

    private User mockUser;
    private Principal mockPrincipal;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("trader@alphatracker.com");
        mockPrincipal = () -> "trader@alphatracker.com";
    }

    @Test
    @DisplayName("Should correctly calculate P/L for an execution")
    void testLongCalculation() {
        TradeRequest request = new TradeRequest();
        request.setTicker("MNQ");
        request.setDirection("Long");
        request.setEntryPrice(20150.25);
        request.setExitPrice(20185.00);
        request.setContracts(1);
        request.setFollowedPlan(true);

        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Trade savedTrade = tradeService.logTrade(request, mockUser);

        assertNotNull(savedTrade);
        // 34.75 point move x $2 (MNQ) x 1 contract, no commission subtracted.
        assertEquals(69.5, savedTrade.getProfitLoss(), 0.001);
        assertEquals("MNQ", savedTrade.getTicker());
        assertTrue(savedTrade.getFollowedPlan());
        assertNotNull(savedTrade.getTradeDate());
    }

    @Test
    @DisplayName("Should correctly invert direction and multiply contracts for an NQ Short execution")
    void TestShortMultiContractCalculation() {
        TradeRequest request = new TradeRequest();
        request.setTicker("NQ");
        request.setDirection("SHORT");
        request.setEntryPrice(20200.00);
        request.setExitPrice(20150.00);
        request.setContracts(2);

        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Pass mockUser
        Trade savedTrade = tradeService.logTrade(request, mockUser);

        assertNotNull(savedTrade);
        // 50 point move (short, entry - exit) x $20 (NQ) x 2 contracts.
        assertEquals(2000.00, savedTrade.getProfitLoss(), 0.001);
        assertEquals("NQ", savedTrade.getTicker());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when an unrecognized ticker is provided")
    void TestUnknownTickerException() {
        TradeRequest request = new TradeRequest();
        request.setTicker("AAPL");
        request.setDirection("LONG");
        request.setEntryPrice(150.00);
        request.setExitPrice(155.00);
        request.setContracts(1);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            tradeService.logTrade(request, mockUser);
        });

        assertTrue(exception.getMessage().toLowerCase().contains("unknown")
                || exception.getMessage().toLowerCase().contains("unsupported"));
        verify(tradeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when contract count is less than 1")
    void testInvalidContractsThrowsException() {
        TradeRequest request = new TradeRequest();
        request.setTicker("MNQ");
        request.setDirection("LONG");
        request.setEntryPrice(20000.0);
        request.setExitPrice(20010.0);
        request.setContracts(0);

        assertThrows(IllegalArgumentException.class, () -> {
            tradeService.logTrade(request, mockUser);
        });

        verify(tradeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should delete trade and reverse its P/L on the linked account balance")
    void testDeleteTradeReversesAccountBalance() {
        // Arrange
        Account mockAccount = Account.builder()
                .id(10L)
                .currentBalance(50100.00)
                .user(mockUser)
                .build();

        Trade mockTrade = Trade.builder()
                .id(99L)
                .profitLoss(100.00)
                .user(mockUser)
                .account(mockAccount)
                .build();

        when(tradeRepository.findById(99L)).thenReturn(Optional.of(mockTrade));

        // Act
        tradeService.deleteTrade(99L, mockUser);

        // Assert
        assertEquals(50000.00, mockAccount.getCurrentBalance(), 0.01);
        verify(accountRepository, times(1)).save(mockAccount);
        verify(tradeRepository, times(1)).delete(mockTrade);
    }

    @Test
    @DisplayName("Should link trade to account and update live account balance")
    void testLogTradeWithAccountSync() {
        // Arrange
        Account mockAccount = Account.builder()
                .id(5L)
                .startingBalance(50000.00)
                .currentBalance(50000.00)
                .user(mockUser)
                .build();

        TradeRequest request = TradeRequest.builder()
                .ticker("MNQ")
                .direction("LONG")
                .entryPrice(20150.25)
                .exitPrice(20185.00) // Net P/L = $69.50
                .contracts(1)
                .accountId(5L)
                .build();

        when(accountRepository.findByIdAndUserId(5L, mockUser.getId())).thenReturn(Optional.of(mockAccount));
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Trade result = tradeService.logTrade(request, mockUser);

        // Assert
        assertNotNull(result.getAccount());
        assertEquals(5L, result.getAccount().getId());
        assertEquals(50069.5, mockAccount.getCurrentBalance(), 0.01);
        verify(accountRepository, times(1)).save(mockAccount);
    }

    @Test
    @DisplayName("Should throw SecurityException when deleting a trade owned by another user")
    void testDeleteTradeUnauthorizedThrowsException() {
        User otherUser = new User();
        otherUser.setId(2L);
        otherUser.setEmail("intruder@example.com");

        Trade otherUserTrade = Trade.builder()
                .id(101L)
                .profitLoss(250.00)
                .user(otherUser)
                .build();

        when(tradeRepository.findById(101L)).thenReturn(Optional.of(otherUserTrade));

        assertThrows(SecurityException.class, () -> {
            tradeService.deleteTrade(101L, mockUser);
        });

        verify(tradeRepository, never()).delete(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should filter trades by account ID when provided")
    void testGetTradesScopedToAccount() {
        Long accountId = 10L;

        tradeService.getTradesForUser(mockUser, accountId);

        verify(tradeRepository, times(1))
                .findAllByUserIdAndAccountIdOrderByTradeDateDesc(mockUser.getId(), accountId);
        verify(tradeRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("Should fetch all trades for user when account ID is null")
    void testGetTradesUnscopedWhenAccountIdIsNull() {
        tradeService.getTradesForUser(mockUser, null);

        verify(tradeRepository, times(1)).findByUserId(mockUser.getId());
        verify(tradeRepository, never())
                .findAllByUserIdAndAccountIdOrderByTradeDateDesc(any(), any());
    }

    @Test
    @DisplayName("Should attach a trade to the primary account when no accountId is given")
    void testLogTradeDefaultsToPrimaryAccountWhenAccountIdOmitted() {
        Account primaryAccount = Account.builder()
                .id(7L)
                .currentBalance(50000.00)
                .isPrimary(true)
                .user(mockUser)
                .build();

        TradeRequest request = TradeRequest.builder()
                .ticker("MNQ")
                .direction("LONG")
                .entryPrice(20150.25)
                .exitPrice(20185.00) // Net P/L = $69.50
                .contracts(1)
                .build();

        when(accountRepository.findAllByUserIdAndIsPrimaryTrue(mockUser.getId()))
                .thenReturn(List.of(primaryAccount));
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Trade result = tradeService.logTrade(request, mockUser);

        assertNotNull(result.getAccount());
        assertEquals(7L, result.getAccount().getId());
        assertEquals(50069.5, primaryAccount.getCurrentBalance(), 0.01);
        verify(accountRepository, times(1)).save(primaryAccount);
        // The trader never picked an account explicitly, so the ownership
        // lookup used for an explicit accountId must not run.
        verify(accountRepository, never()).findByIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("Should leave a trade unassigned when no accountId is given and no primary account exists")
    void testLogTradeStaysUnassignedWithoutPrimaryAccount() {
        TradeRequest request = TradeRequest.builder()
                .ticker("MNQ")
                .direction("LONG")
                .entryPrice(20150.25)
                .exitPrice(20185.00)
                .contracts(1)
                .build();

        when(accountRepository.findAllByUserIdAndIsPrimaryTrue(mockUser.getId())).thenReturn(List.of());
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Trade result = tradeService.logTrade(request, mockUser);

        assertNull(result.getAccount());
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update execution rating and setup tags via PATCH")
    void testUpdateTradeSetsReflectionFields() {
        Trade existing = Trade.builder().id(42L).user(mockUser).build();
        when(tradeRepository.findById(42L)).thenReturn(Optional.of(existing));
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TradeUpdateRequest request = TradeUpdateRequest.builder()
                .executionRating(4)
                .setupTags(List.of("FVG fill", "liquidity sweep"))
                .build();

        Trade result = tradeService.updateTrade(42L, request, mockUser);

        assertEquals(4, result.getExecutionRating());
        assertEquals(List.of("FVG fill", "liquidity sweep"), result.getSetupTags());
        verify(tradeRepository, times(1)).save(existing);
    }

    @Test
    @DisplayName("Should reject an execution rating outside 1-5")
    void testUpdateTradeRejectsOutOfRangeRating() {
        Trade existing = Trade.builder().id(42L).user(mockUser).build();
        when(tradeRepository.findById(42L)).thenReturn(Optional.of(existing));

        TradeUpdateRequest request = TradeUpdateRequest.builder().executionRating(6).build();

        assertThrows(IllegalArgumentException.class, () -> tradeService.updateTrade(42L, request, mockUser));
        verify(tradeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject updating a trade owned by another user")
    void testUpdateTradeUnauthorizedThrowsException() {
        User otherUser = new User();
        otherUser.setId(2L);
        Trade otherUserTrade = Trade.builder().id(101L).user(otherUser).build();
        when(tradeRepository.findById(101L)).thenReturn(Optional.of(otherUserTrade));

        TradeUpdateRequest request = TradeUpdateRequest.builder().executionRating(3).build();

        assertThrows(SecurityException.class, () -> tradeService.updateTrade(101L, request, mockUser));
        verify(tradeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should query trades within the given calendar day's bounds")
    void testGetTradesForDateUsesDayBounds() {
        LocalDate date = LocalDate.of(2026, 9, 18);

        tradeService.getTradesForDate(date, mockUser);

        verify(tradeRepository, times(1)).findAllByUserIdAndTradeDateGreaterThanEqualAndTradeDateLessThanOrderByTradeDateAsc(
                mockUser.getId(), date.atStartOfDay(), date.plusDays(1).atStartOfDay());
    }

}
