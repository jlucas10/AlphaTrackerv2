package com.alphatracker.api;

import com.alphatracker.api.analytics.AnalyticsController;
import com.alphatracker.api.analytics.AnalyticsService;
import com.alphatracker.api.analytics.SummaryResponse;
import com.alphatracker.api.analytics.TradeStats;
import com.alphatracker.api.security.JwtAuthenticationFilter;
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

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AnalyticsController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
public class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsService analyticsService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = User.builder().id(1L).email("trader@alphatracker.com").password("hashed")
                .firstName("Trader").role(Role.USER).build();
    }

    @Test
    @DisplayName("GET /analytics/summary binds accountId/from/to, echoes them back, and serializes 'no data' as null (not 0)")
    void summaryBindsParamsAndSerializesNulls() throws Exception {
        LocalDate day = LocalDate.of(2026, 9, 18);
        SummaryResponse empty = new SummaryResponse(5L, day, day,
                TradeStats.summarize(List.of()), TradeStats.summarizeDays(List.of()));
        when(analyticsService.summary(mockUser, 5L, day, day)).thenReturn(empty);

        mockMvc.perform(get("/api/v1/analytics/summary")
                        .param("accountId", "5").param("from", "2026-09-18").param("to", "2026-09-18")
                        .with(user(mockUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(5))
                .andExpect(jsonPath("$.from").value("2026-09-18"))
                .andExpect(jsonPath("$.trades.tradeCount").value(0))
                .andExpect(jsonPath("$.trades.winRate").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.days.tradingDays").value(0));
    }

    @Test
    @DisplayName("a foreign account is a 403 and bad input is a 400, both through the global exception handler")
    void errorsMapToHttpStatuses() throws Exception {
        when(analyticsService.discipline(any(), any(), any(), any())).thenThrow(new SecurityException("not yours"));
        when(analyticsService.breakdown(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("'by' must be one of: instrument, setup, rating."));

        mockMvc.perform(get("/api/v1/analytics/discipline").param("accountId", "9").with(user(mockUser)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/analytics/breakdown").param("by", "color").with(user(mockUser)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}
