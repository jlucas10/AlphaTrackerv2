package com.alphatracker.api;

import com.alphatracker.api.account.Account;
import com.alphatracker.api.security.JwtAuthenticationFilter;
import com.alphatracker.api.trade.Trade;
import com.alphatracker.api.trade.TradeController;
import com.alphatracker.api.trade.TradeService;
import com.alphatracker.api.user.Role;
import com.alphatracker.api.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Pins the public JSON shape of the trade endpoints - the frontend (and the
// future assistant service) depend on it, and Trade embeds a User, so a
// regression here is a data leak, not just a broken field.
@WebMvcTest(controllers = TradeController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
public class TradeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TradeService tradeService;

    private User mockUser;
    private Trade trade;

    @BeforeEach
    void setUp() {
        mockUser = User.builder().id(1L).email("trader@alphatracker.com").password("hashed-secret")
                .firstName("Trader").role(Role.USER).build();
        trade = Trade.builder().id(99L).ticker("MNQ").direction("LONG").entryPrice(100.0).exitPrice(110.0)
                .contracts(2).profitLoss(40.0).tradeDate(LocalDateTime.of(2026, 9, 18, 9, 45))
                .setupTags(List.of("FVG")).user(mockUser).account(Account.builder().id(3L).build()).build();
    }

    @Test
    @DisplayName("GET /trades returns TradeResponse JSON: accountId present, no user object, no password")
    void listHasNoUserAndHasAccountId() throws Exception {
        when(tradeService.getTradesForUser(mockUser, null)).thenReturn(List.of(trade));

        mockMvc.perform(get("/api/v1/trades").with(user(mockUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(99))
                .andExpect(jsonPath("$[0].profitLoss").value(40.0))
                .andExpect(jsonPath("$[0].accountId").value(3))
                .andExpect(jsonPath("$[0].setupTags[0]").value("FVG"))
                .andExpect(jsonPath("$[0].user").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hashed-secret"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("trader@alphatracker.com"))));
    }

    @Test
    @DisplayName("a foreign trade is still a 403 through the exception handler")
    void foreignTradeIs403() throws Exception {
        when(tradeService.getTradeById(99L, mockUser)).thenThrow(new SecurityException("Unauthorized"));

        mockMvc.perform(get("/api/v1/trades/99").with(user(mockUser)))
                .andExpect(status().isForbidden());
    }
}
