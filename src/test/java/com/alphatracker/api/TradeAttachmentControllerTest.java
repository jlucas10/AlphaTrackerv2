package com.alphatracker.api;

import com.alphatracker.api.security.JwtAuthenticationFilter;
import com.alphatracker.api.trade.AttachmentType;
import com.alphatracker.api.trade.Trade;
import com.alphatracker.api.trade.TradeAttachment;
import com.alphatracker.api.trade.TradeAttachmentController;
import com.alphatracker.api.trade.TradeAttachmentService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Filters run for real here (no addFilters=false): SecurityMockMvcRequestPostProcessors.user()
// stores the authentication in the mock session, and it's a security filter
// that loads that into SecurityContextHolder for the request - skip the
// filters and @AuthenticationPrincipal resolves to null even though the
// session-stored context looks correct. JwtAuthenticationFilter itself is
// still excluded from this slice's component scan, since it needs
// JwtService/UserRepository beans this test doesn't provide; Boot's default
// security auto-configuration fills in a working filter chain in its place.
@WebMvcTest(controllers = TradeAttachmentController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
public class TradeAttachmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TradeAttachmentService attachmentService;

    private User mockUser;
    private Trade mockTrade;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(1L)
                .email("trader@alphatracker.com")
                .password("hashed")
                .firstName("Trader")
                .role(Role.USER)
                .build();
        mockTrade = Trade.builder().id(50L).user(mockUser).build();
    }

    @Test
    @DisplayName("POST /trades/{id}/attachments uploads a file and returns the attachment DTO")
    void uploadAttachmentReturnsCreatedAttachment() throws Exception {
        TradeAttachment attachment = TradeAttachment.builder()
                .id(10L)
                .trade(mockTrade)
                .storageKey("1/uuid.png")
                .attachmentType(AttachmentType.SCREENSHOT)
                .contentType("image/png")
                .sizeBytes(5L)
                .uploadedAt(LocalDateTime.now())
                .caption("setup")
                .build();

        when(attachmentService.uploadAttachment(eq(50L), any(), eq(5L), eq("chart.png"),
                eq("image/png"), eq("setup"), eq(mockUser))).thenReturn(attachment);

        MockMultipartFile file = new MockMultipartFile("file", "chart.png", "image/png", "bytes".getBytes());

        mockMvc.perform(multipart("/api/v1/trades/50/attachments")
                        .file(file)
                        .param("caption", "setup")
                        .with(user(mockUser))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.tradeId").value(50))
                .andExpect(jsonPath("$.url").value("/api/v1/attachments/10/file"))
                .andExpect(jsonPath("$.storageKey").doesNotExist());
    }

    @Test
    @DisplayName("POST /trades/{id}/attachments surfaces a service rejection as 400 with a message body")
    void uploadAttachmentRejectedByServiceReturns400() throws Exception {
        when(attachmentService.uploadAttachment(any(), any(), anyLong(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Unsupported file type: application/zip."));

        MockMultipartFile file = new MockMultipartFile("file", "bad.zip", "application/zip", "bytes".getBytes());

        mockMvc.perform(multipart("/api/v1/trades/50/attachments")
                        .file(file)
                        .with(user(mockUser))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported file type: application/zip."));
    }

    @Test
    @DisplayName("GET /trades/{id}/attachments returns the service's list as DTOs")
    void getAttachmentsForTradeReturnsList() throws Exception {
        TradeAttachment attachment = TradeAttachment.builder()
                .id(10L).trade(mockTrade).storageKey("1/a.png")
                .attachmentType(AttachmentType.SCREENSHOT).contentType("image/png")
                .sizeBytes(5L).uploadedAt(LocalDateTime.now()).build();

        when(attachmentService.getAttachmentsForTrade(50L, mockUser)).thenReturn(List.of(attachment));

        mockMvc.perform(get("/api/v1/trades/50/attachments").with(user(mockUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].url").value("/api/v1/attachments/10/file"));
    }

    @Test
    @DisplayName("GET /attachments/{id}/file streams the bytes with the stored Content-Type")
    void getAttachmentFileStreamsBytesWithContentType() throws Exception {
        TradeAttachment attachment = TradeAttachment.builder()
                .id(10L).trade(mockTrade).storageKey("1/a.png")
                .attachmentType(AttachmentType.SCREENSHOT).contentType("image/png")
                .sizeBytes(5L).uploadedAt(LocalDateTime.now()).build();

        when(attachmentService.getAttachmentForDownload(10L, mockUser)).thenReturn(attachment);
        when(attachmentService.openAttachmentContent(attachment))
                .thenReturn(new ByteArrayInputStream("bytes".getBytes()));

        mockMvc.perform(get("/api/v1/attachments/10/file").with(user(mockUser)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    @DisplayName("GET /attachments/{id}/file for an unowned attachment returns 400, not the bytes")
    void getAttachmentFileForUnownedAttachmentReturns400() throws Exception {
        when(attachmentService.getAttachmentForDownload(10L, mockUser))
                .thenThrow(new IllegalArgumentException("Attachment not found or does not belong to user."));

        mockMvc.perform(get("/api/v1/attachments/10/file").with(user(mockUser)))
                .andExpect(status().isBadRequest());

        verify(attachmentService, never()).openAttachmentContent(any());
    }

    @Test
    @DisplayName("DELETE /attachments/{id} deletes via the service and returns 200")
    void deleteAttachmentReturnsOk() throws Exception {
        mockMvc.perform(delete("/api/v1/attachments/10").with(user(mockUser)).with(csrf()))
                .andExpect(status().isOk());

        verify(attachmentService, times(1)).deleteAttachment(10L, mockUser);
    }
}
