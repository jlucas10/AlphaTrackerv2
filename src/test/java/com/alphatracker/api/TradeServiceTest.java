package com.alphatracker.api;

import com.alphatracker.api.trade.Trade;
import com.alphatracker.api.trade.TradeRepository;
import com.alphatracker.api.trade.TradeRequest;
import com.alphatracker.api.trade.TradeService;
import com.alphatracker.api.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.exceptions.base.MockitoException;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
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
        assertEquals(68.16, savedTrade.getProfitLoss(), 0.001);
        assertEquals(1.34, savedTrade.getCommission(), 0.001);
        assertEquals("MNQ", savedTrade.getTicker());
        assertTrue(savedTrade.getFollowedPlan());
        assertNotNull(savedTrade.getTradeDate());
    }
}
