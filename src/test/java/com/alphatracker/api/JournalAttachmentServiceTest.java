package com.alphatracker.api;

import com.alphatracker.api.journal.JournalAttachment;
import com.alphatracker.api.journal.JournalAttachmentRepository;
import com.alphatracker.api.journal.JournalAttachmentService;
import com.alphatracker.api.journal.JournalEntry;
import com.alphatracker.api.journal.JournalEntryService;
import com.alphatracker.api.storage.StorageService;
import com.alphatracker.api.storage.StoredFile;
import com.alphatracker.api.trade.AttachmentType;
import com.alphatracker.api.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class JournalAttachmentServiceTest {

    @Mock
    private JournalAttachmentRepository attachmentRepository;

    @Mock
    private JournalEntryService journalEntryService;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private JournalAttachmentService attachmentService;

    private User mockUser;
    private JournalEntry mockEntry;
    private final LocalDate day = LocalDate.of(2026, 9, 18);

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockEntry = JournalEntry.builder().id(50L).user(mockUser).entryDate(day).build();
    }

    @Test
    @DisplayName("Should create the day's entry if missing, store the file, and save the row")
    void uploadAttachmentCreatesEntryAndSavesRow() {
        InputStream content = new ByteArrayInputStream("bytes".getBytes());
        StoredFile stored = new StoredFile("1/uuid.png", 5L, "image/png");

        when(journalEntryService.findOrCreate(day, mockUser)).thenReturn(mockEntry);
        when(storageService.store(eq(content), eq(5L), eq("chart.png"), eq("image/png"), eq(1L))).thenReturn(stored);
        when(attachmentRepository.save(any(JournalAttachment.class))).thenAnswer(inv -> inv.getArgument(0));

        JournalAttachment result = attachmentService.uploadAttachment(day, content, 5L, "chart.png",
                "image/png", "entry setup", mockUser);

        assertEquals("1/uuid.png", result.getStorageKey());
        assertEquals(AttachmentType.SCREENSHOT, result.getAttachmentType());
        assertEquals(mockEntry, result.getJournalEntry());
        verify(attachmentRepository, times(1)).save(any(JournalAttachment.class));
    }

    @Test
    @DisplayName("Should reject an unsupported content type before ever touching storage or the journal entry")
    void uploadAttachmentRejectsUnsupportedContentType() {
        assertThrows(IllegalArgumentException.class, () -> attachmentService.uploadAttachment(
                day, new ByteArrayInputStream("x".getBytes()), 1L, "malware.exe",
                "application/x-msdownload", null, mockUser));

        verify(journalEntryService, never()).findOrCreate(any(), any());
        verify(storageService, never()).store(any(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("Should reject a file over the size limit before ever touching storage")
    void uploadAttachmentRejectsOversizedFile() {
        long tooLarge = 11L * 1024 * 1024;

        assertThrows(IllegalArgumentException.class, () -> attachmentService.uploadAttachment(
                day, new ByteArrayInputStream("x".getBytes()), tooLarge, "huge.png", "image/png", null, mockUser));

        verify(storageService, never()).store(any(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("Should return an empty list without querying attachments when the day has no entry yet")
    void getAttachmentsForDateReturnsEmptyWhenNoEntryExists() {
        JournalEntry unsaved = JournalEntry.builder().user(mockUser).entryDate(day).build();
        when(journalEntryService.getOrDefault(day, mockUser)).thenReturn(unsaved);

        List<JournalAttachment> result = attachmentService.getAttachmentsForDate(day, mockUser);

        assertTrue(result.isEmpty());
        verify(attachmentRepository, never()).findAllByJournalEntry_IdOrderByUploadedAtAsc(any());
    }

    @Test
    @DisplayName("Should list attachments for a day that already has an entry")
    void getAttachmentsForDateReturnsExistingAttachments() {
        JournalAttachment attachment = JournalAttachment.builder().id(1L).journalEntry(mockEntry).storageKey("1/a.png").build();
        when(journalEntryService.getOrDefault(day, mockUser)).thenReturn(mockEntry);
        when(attachmentRepository.findAllByJournalEntry_IdOrderByUploadedAtAsc(50L)).thenReturn(List.of(attachment));

        List<JournalAttachment> result = attachmentService.getAttachmentsForDate(day, mockUser);

        assertEquals(1, result.size());
        assertEquals("1/a.png", result.get(0).getStorageKey());
    }

    @Test
    @DisplayName("Should delete both the stored file and the row when the owner deletes an attachment")
    void deleteAttachmentRemovesFileAndRow() {
        JournalAttachment attachment = JournalAttachment.builder()
                .id(200L).journalEntry(mockEntry).storageKey("1/uuid.png")
                .attachmentType(AttachmentType.SCREENSHOT).build();
        when(attachmentRepository.findByIdAndJournalEntry_User_Id(200L, 1L)).thenReturn(Optional.of(attachment));

        attachmentService.deleteAttachment(200L, mockUser);

        verify(storageService, times(1)).delete("1/uuid.png");
        verify(attachmentRepository, times(1)).delete(attachment);
    }

    @Test
    @DisplayName("Should throw when deleting an attachment that doesn't belong to the requester")
    void deleteAttachmentRejectsUnownedAttachment() {
        when(attachmentRepository.findByIdAndJournalEntry_User_Id(200L, 1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> attachmentService.deleteAttachment(200L, mockUser));

        verify(storageService, never()).delete(any());
        verify(attachmentRepository, never()).delete(any(JournalAttachment.class));
    }
}
