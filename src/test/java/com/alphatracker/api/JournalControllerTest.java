package com.alphatracker.api;

import com.alphatracker.api.journal.JournalAttachmentService;
import com.alphatracker.api.journal.JournalController;
import com.alphatracker.api.journal.JournalEntry;
import com.alphatracker.api.journal.JournalEntryService;
import com.alphatracker.api.security.JwtAuthenticationFilter;
import com.alphatracker.api.trade.Trade;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = JournalController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
public class JournalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JournalEntryService journalEntryService;

    @MockitoBean
    private JournalAttachmentService journalAttachmentService;

    @MockitoBean
    private TradeService tradeService;

    private User mockUser;
    private final LocalDate day = LocalDate.of(2026, 9, 18);

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(1L).email("trader@alphatracker.com").password("hashed")
                .firstName("Trader").role(Role.USER).build();
    }

    @Test
    @DisplayName("GET /journal/{date} bundles notes, attachments, and that day's trades")
    void getDayReturnsFullBundle() throws Exception {
        JournalEntry entry = JournalEntry.builder().id(5L).user(mockUser).entryDate(day)
                .notes("Choppy session").htfBias("Neutral").build();
        Trade trade = Trade.builder().id(99L).ticker("MNQ").user(mockUser).build();

        when(journalEntryService.getOrDefault(day, mockUser)).thenReturn(entry);
        when(journalAttachmentService.getAttachmentsForDate(day, mockUser)).thenReturn(List.of());
        when(tradeService.getTradesForDate(day, mockUser)).thenReturn(List.of(trade));

        mockMvc.perform(get("/api/v1/journal/2026-09-18").with(user(mockUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("Choppy session"))
                .andExpect(jsonPath("$.htfBias").value("Neutral"))
                .andExpect(jsonPath("$.trades[0].id").value(99))
                .andExpect(jsonPath("$.attachments").isEmpty());
    }

    @Test
    @DisplayName("PUT /journal/{date} upserts notes/bias then returns the refreshed bundle")
    void upsertDayReturnsRefreshedBundle() throws Exception {
        JournalEntry updated = JournalEntry.builder().id(5L).user(mockUser).entryDate(day)
                .notes("Updated notes").htfBias("Bullish").build();

        when(journalEntryService.upsertNotes(eq(day), eq("Updated notes"), eq("Bullish"), eq(mockUser)))
                .thenReturn(updated);
        when(journalEntryService.getOrDefault(day, mockUser)).thenReturn(updated);
        when(journalAttachmentService.getAttachmentsForDate(day, mockUser)).thenReturn(List.of());
        when(tradeService.getTradesForDate(day, mockUser)).thenReturn(List.of());

        mockMvc.perform(put("/api/v1/journal/2026-09-18")
                        .with(user(mockUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"Updated notes\",\"htfBias\":\"Bullish\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("Updated notes"))
                .andExpect(jsonPath("$.htfBias").value("Bullish"));

        verify(journalEntryService, times(1)).upsertNotes(day, "Updated notes", "Bullish", mockUser);
    }
}
