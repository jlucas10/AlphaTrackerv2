package com.alphatracker.api;

import com.alphatracker.api.importer.ImportController;
import com.alphatracker.api.importer.ImportPreviewResponse;
import com.alphatracker.api.importer.ImportPreviewResponse.AccountCheck;
import com.alphatracker.api.importer.ImportPreviewResponse.AccountCheckStatus;
import com.alphatracker.api.importer.ImportPreviewResponse.AccountInfo;
import com.alphatracker.api.importer.ImportPreviewResponse.Row;
import com.alphatracker.api.importer.ImportPreviewResponse.RowStatus;
import com.alphatracker.api.importer.ImportPreviewResponse.Summary;
import com.alphatracker.api.importer.ImportPreviewResponse.TradePreview;
import com.alphatracker.api.importer.ImportService;
import com.alphatracker.api.importer.ImportSource;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Same slice setup as JournalAttachmentControllerTest: security filters run for real, the JWT filter is excluded.
@WebMvcTest(controllers = ImportController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
public class ImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImportService importService;

    private User mockUser;
    private MockMultipartFile file;

    @BeforeEach
    void setUp() {
        mockUser = User.builder().id(1L).email("trader@alphatracker.com").password("hashed")
                .firstName("Trader").role(Role.USER).build();
        file = new MockMultipartFile("file", "trades_export.csv", "text/csv", "Id,ContractName\r\n".getBytes());
    }

    private ImportPreviewResponse sampleResponse() {
        TradePreview trade = new TradePreview("MNQ", "LONG", 30756.75, 30723.75, 5,
                LocalDateTime.of(2026, 10, 13, 7, 45, 11), LocalDateTime.of(2026, 10, 13, 7, 51, 56), -330.0, -330.0);
        Row ok = new Row(2, "3198364077", RowStatus.NEW, trade,
                List.of(new AccountCheck(3L, AccountCheckStatus.NEW)), List.of(), List.of());
        Row bad = new Row(3, "9", RowStatus.ERROR, null, List.of(), List.of(),
                List.of("Unsupported contract 'XYZZ6'"));
        return new ImportPreviewResponse(ImportSource.TOPSTEPX,
                List.of(new AccountInfo(3L, "Topstep 50k", "ACTIVE", null)),
                new Summary(2, 1, 0, 1, 1), List.of(ok, bad));
    }

    @Test
    @DisplayName("POST /imports/preview returns the rows, per-account checks, errors and summary")
    void previewReturnsRows() throws Exception {
        when(importService.preview(eq(ImportSource.TOPSTEPX), eq(List.of(3L)), any(), eq(mockUser)))
                .thenReturn(sampleResponse());

        mockMvc.perform(multipart("/api/v1/imports/preview").file(file)
                        .param("source", "TOPSTEPX").param("accountIds", "3")
                        .with(user(mockUser)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("TOPSTEPX"))
                .andExpect(jsonPath("$.summary.total").value(2))
                .andExpect(jsonPath("$.summary.tradesToCreate").value(1))
                .andExpect(jsonPath("$.accounts[0].name").value("Topstep 50k"))
                .andExpect(jsonPath("$.rows[0].status").value("NEW"))
                .andExpect(jsonPath("$.rows[0].trade.ticker").value("MNQ"))
                .andExpect(jsonPath("$.rows[0].trade.profitLoss").value(-330.0))
                .andExpect(jsonPath("$.rows[0].trade.tradeDate").value("2026-10-13T07:45:11"))
                .andExpect(jsonPath("$.rows[0].accounts[0].status").value("NEW"))
                .andExpect(jsonPath("$.rows[1].status").value("ERROR"))
                .andExpect(jsonPath("$.rows[1].errors[0]").value("Unsupported contract 'XYZZ6'"));
    }

    @Test
    @DisplayName("several account ids (repeated or comma separated) reach the service as a list; source is case-insensitive")
    void multipleAccountIds() throws Exception {
        when(importService.preview(eq(ImportSource.TOPSTEPX), eq(List.of(3L, 4L)), any(), eq(mockUser)))
                .thenReturn(sampleResponse());

        mockMvc.perform(multipart("/api/v1/imports/preview").file(file)
                        .param("source", "topstepx").param("accountIds", "3").param("accountIds", "4")
                        .with(user(mockUser)).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/imports/preview").file(file)
                        .param("source", "TOPSTEPX").param("accountIds", "3,4")
                        .with(user(mockUser)).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("400: missing file, with a readable message")
    void missingFileIs400() throws Exception {
        mockMvc.perform(multipart("/api/v1/imports/preview")
                        .param("source", "TOPSTEPX").param("accountIds", "3")
                        .with(user(mockUser)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a file to upload."));
        verifyNoInteractions(importService);
    }

    @Test
    @DisplayName("400: missing or unknown source")
    void badSourceIs400() throws Exception {
        mockMvc.perform(multipart("/api/v1/imports/preview").file(file).param("accountIds", "3")
                        .with(user(mockUser)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Choose an import source")));
        mockMvc.perform(multipart("/api/v1/imports/preview").file(file).param("source", "TRADOVATE").param("accountIds", "3")
                        .with(user(mockUser)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Unsupported import source 'TRADOVATE'")));
        verifyNoInteractions(importService);
    }

    @Test
    @DisplayName("400: a non-numeric account id")
    void badAccountIdIs400() throws Exception {
        mockMvc.perform(multipart("/api/v1/imports/preview").file(file)
                        .param("source", "TOPSTEPX").param("accountIds", "abc")
                        .with(user(mockUser)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for 'accountIds'."));
    }

    @Test
    @DisplayName("400: a bad file or no account, carrying the service's message")
    void serviceValidationIs400() throws Exception {
        when(importService.preview(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("The file has a header but no trades."));

        mockMvc.perform(multipart("/api/v1/imports/preview").file(file)
                        .param("source", "TOPSTEPX").param("accountIds", "3")
                        .with(user(mockUser)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The file has a header but no trades."));
    }

    @Test
    @DisplayName("403: an account the user does not own")
    void foreignAccountIs403() throws Exception {
        when(importService.preview(any(), any(), any(), any()))
                .thenThrow(new SecurityException("Account not found or does not belong to user."));

        mockMvc.perform(multipart("/api/v1/imports/preview").file(file)
                        .param("source", "TOPSTEPX").param("accountIds", "99")
                        .with(user(mockUser)).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Account not found or does not belong to user."));
    }

    @Test
    @DisplayName("no authentication is rejected, and the service is never reached (the slice answers 401; the full app answers an empty 403, see CONTEXT.md)")
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/imports/preview").file(file)
                        .param("source", "TOPSTEPX").param("accountIds", "3").with(csrf()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(importService);
    }
}
