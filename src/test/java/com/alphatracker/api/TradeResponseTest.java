package com.alphatracker.api;

import com.alphatracker.api.account.Account;
import com.alphatracker.api.trade.Trade;
import com.alphatracker.api.trade.TradeResponse;
import com.alphatracker.api.user.Role;
import com.alphatracker.api.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TradeResponseTest {

    private final User owner = User.builder().id(1L).email("t@alphatracker.com")
            .password("hashed").firstName("T").role(Role.USER).build();

    @Test
    @DisplayName("fromEntity copies every trade field and flattens the account to accountId")
    void mapsAllFieldsAndAccountId() {
        Trade trade = Trade.builder().id(7L).ticker("MNQ").direction("LONG")
                .entryPrice(100.0).exitPrice(110.0).contracts(2).profitLoss(40.0)
                .followedPlan(true).notes("FVG fill").tradeDate(LocalDateTime.of(2026, 9, 18, 9, 45))
                .executionRating(4).setupTags(List.of("FVG", "A+"))
                .user(owner).account(Account.builder().id(3L).build()).build();

        TradeResponse r = TradeResponse.fromEntity(trade);

        assertEquals(7L, r.getId());
        assertEquals("MNQ", r.getTicker());
        assertEquals(40.0, r.getProfitLoss());
        assertEquals(4, r.getExecutionRating());
        assertEquals(List.of("FVG", "A+"), r.getSetupTags());
        assertEquals(3L, r.getAccountId());
    }

    @Test
    @DisplayName("an unassigned trade has a null accountId")
    void unassignedTradeHasNullAccountId() {
        Trade trade = Trade.builder().id(8L).ticker("ES").user(owner).build();

        assertNull(TradeResponse.fromEntity(trade).getAccountId());
    }
}
