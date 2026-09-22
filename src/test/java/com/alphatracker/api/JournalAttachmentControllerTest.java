package com.alphatracker.api;

import com.alphatracker.api.journal.JournalAttachment;
import com.alphatracker.api.journal.JournalAttachmentController;
import com.alphatracker.api.journal.JournalAttachmentService;
import com.alphatracker.api.journal.JournalEntry;
import com.alphatracker.api.security.JwtAuthenticationFilter;
import com.alphatracker.api.trade.AttachmentType;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Filters run for real (see TradeAttachmentControllerTest for why addFilters=false
// breaks @AuthenticationPrincipal); JwtAuthenticationFilter itself is still
// excluded from the slice since it needs beans this test doesn't provide.
@WebMvcTest(controllers = JournalAttachmentController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
public class JournalAttachmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JournalAttachmentService attachmentService;

    private User mockUser;
    private JournalEntry mockEntry;
    private final LocalDate day = LocalDate.of(2026, 9, 18);

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(1L).email("trader@alphatracker.com").password("hashed")
                .firstName("Trader").role(Role.USER).build();
        mockEntry = JournalEntry.builder().id(50L).user(mockUser).entryDate(day).build();
    }

    @Test
    @DisplayName("POST /journal/{date}/attachments uploads a file and returns the attachment DTO")
    void uploadAttachmentReturnsCreatedAttachment() throws Exception {
        JournalAttachment attachment = JournalAttachment.builder()
                .id(10L).journalEntry(mockEntry).storageKey("1/uuid.png")
                .attachmentType(AttachmentType.SCREENSHOT).contentType("image/png")
                .sizeBytes(5L).uploadedAt(LocalDateTime.now()).caption("setup").build();

        when(attachmentService.uploadAttachment(eq(day), any(), eq(5L), eq("chart.png"),
                eq("image/png"), eq("setup"), eq(mockUser))).thenReturn(attachment);

        MockMultipartFile file = new MockMultipartFile("file", "chart.png", "image/png", "bytes".getBytes());

        mockMvc.perform(multipart("/api/v1/journal/2026-09-18/attachments")
                        .file(file)
                        .param("caption", "setup")
                        .with(user(mockUser))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.journalEntryId").value(50))
                .andExpect(jsonPath("$.url").value("/api/v1/journal-attachments/10/file"))
                .andExpect(jsonPath("$.storageKey").doesNotExist());
    }

    @Test
    @DisplayName("POST /journal/{date}/attachments surfaces a service rejection as 400")
    void uploadAttachmentRejectedByServiceReturns400() throws Exception {
        when(attachmentService.uploadAttachment(any(), any(), anyLong(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Unsupported file type: application/zip."));

        MockMultipartFile file = new MockMultipartFile("file", "bad.zip", "application/zip", "bytes".getBytes());

        mockMvc.perform(multipart("/api/v1/journal/2026-09-18/attachments")
                        .file(file)
                        .with(user(mockUser))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported file type: application/zip."));
    }

    @Test
    @DisplayName("GET /journal/{date}/attachments returns the service's list as DTOs")
    void getAttachmentsForDateReturnsList() throws Exception {
        JournalAttachment attachment = JournalAttachment.builder()
                .id(10L).journalEntry(mockEntry).storageKey("1/a.png")
                .attachmentType(AttachmentType.SCREENSHOT).contentType("image/png")
                .sizeBytes(5L).uploadedAt(LocalDateTime.now()).build();

        when(attachmentService.getAttachmentsForDate(day, mockUser)).thenReturn(List.of(attachment));

        mockMvc.perform(get("/api/v1/journal/2026-09-18/attachments").with(user(mockUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].url").value("/api/v1/journal-attachments/10/file"));
    }

    @Test
    @DisplayName("GET /journal-attachments/{id}/file streams the bytes with the stored Content-Type")
    void getAttachmentFileStreamsBytesWithContentType() throws Exception {
        JournalAttachment attachment = JournalAttachment.builder()
                .id(10L).journalEntry(mockEntry).storageKey("1/a.png")
                .attachmentType(AttachmentType.SCREENSHOT).contentType("image/png")
                .sizeBytes(5L).uploadedAt(LocalDateTime.now()).build();

        when(attachmentService.getAttachmentForDownload(10L, mockUser)).thenReturn(attachment);
        when(attachmentService.openAttachmentContent(attachment)).thenReturn(new ByteArrayInputStream("bytes".getBytes()));

        mockMvc.perform(get("/api/v1/journal-attachments/10/file").with(user(mockUser)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    @DisplayName("GET /journal-attachments/{id}/file for an unowned attachment returns 400, not the bytes")
    void getAttachmentFileForUnownedAttachmentReturns400() throws Exception {
        when(attachmentService.getAttachmentForDownload(10L, mockUser))
                .thenThrow(new IllegalArgumentException("Attachment not found or does not belong to user."));

        mockMvc.perform(get("/api/v1/journal-attachments/10/file").with(user(mockUser)))
                .andExpect(status().isBadRequest());

        verify(attachmentService, never()).openAttachmentContent(any());
    }

    @Test
    @DisplayName("DELETE /journal-attachments/{id} deletes via the service and returns 200")
    void deleteAttachmentReturnsOk() throws Exception {
        mockMvc.perform(delete("/api/v1/journal-attachments/10").with(user(mockUser)).with(csrf()))
                .andExpect(status().isOk());

        verify(attachmentService, times(1)).deleteAttachment(10L, mockUser);
    }
}
