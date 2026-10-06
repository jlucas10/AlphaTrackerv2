package com.alphatracker.api;

import com.alphatracker.api.account.Account;
import com.alphatracker.api.account.AccountRepository;
import com.alphatracker.api.analytics.AnalyticsService;
import com.alphatracker.api.analytics.BreakdownResponse;
import com.alphatracker.api.analytics.SummaryResponse;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AnalyticsServiceTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    private User mockUser;
    private final LocalDate day = LocalDate.of(2026, 9, 18);

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
    }

    @Test
    @DisplayName("an accountId the user doesn't own is a SecurityException, and no trades are ever queried")
    void foreignAccountIsForbidden() {
        when(accountRepository.findByIdAndUserId(9L, 1L)).thenReturn(Optional.empty());

        assertThrows(SecurityException.class, () -> analyticsService.summary(mockUser, 9L, null, null));

        verifyNoInteractions(tradeRepository);
    }

    @Test
    @DisplayName("from after to, and an unknown breakdown key, are rejected before any query runs")
    void invalidInputIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> analyticsService.summary(mockUser, null, day.plusDays(1), day));
        assertThrows(IllegalArgumentException.class,
                () -> analyticsService.breakdown(mockUser, null, null, null, "color"));

        verifyNoInteractions(tradeRepository);
    }

    @Test
    @DisplayName("the inclusive 'to' date becomes the next midnight (exclusive); an accountId routes to the account query")
    void windowAndAccountRouting() {
        when(accountRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(Account.builder().id(5L).build()));
        Trade win = Trade.builder().id(1L).ticker("MNQ").profitLoss(60.0)
                .tradeDate(day.atTime(9, 30)).setupTags(new ArrayList<>()).build();
        Trade loss = Trade.builder().id(2L).ticker("MNQ").profitLoss(-20.0)
                .tradeDate(day.atTime(10, 0)).setupTags(new ArrayList<>()).build();
        when(tradeRepository.findForAnalyticsByAccount(1L, 5L, day.atStartOfDay(), day.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(win, loss));

        SummaryResponse summary = analyticsService.summary(mockUser, 5L, day, day);

        assertEquals(2, summary.trades().tradeCount());
        assertEquals(40.0, summary.trades().totalPnl());
        assertEquals(1, summary.days().tradingDays());
        assertEquals(1, summary.days().winDays());
        verify(tradeRepository, never()).findForAnalytics(any(), any(), any());
    }

    @Test
    @DisplayName("with no dates the wide default window is used; breakdown dispatches on 'by' and flags the setup caveat")
    void defaultWindowAndBreakdownDispatch() {
        Trade t = Trade.builder().id(1L).ticker("NQ").profitLoss(10.0)
                .tradeDate(day.atTime(9, 30)).setupTags(new ArrayList<>(List.of("FVG"))).build();
        when(tradeRepository.findForAnalytics(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(t));

        BreakdownResponse byTicker = analyticsService.breakdown(mockUser, null, null, null, "Instrument");
        BreakdownResponse bySetup = analyticsService.breakdown(mockUser, null, null, null, "setup");

        assertEquals("instrument", byTicker.by());
        assertEquals("NQ", byTicker.groups().get(0).key());
        assertNull(byTicker.note());
        assertEquals("FVG", bySetup.groups().get(0).key());
        assertNotNull(bySetup.note());
    }
}
