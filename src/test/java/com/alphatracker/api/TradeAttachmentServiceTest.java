package com.alphatracker.api;

import com.alphatracker.api.storage.StorageService;
import com.alphatracker.api.storage.StoredFile;
import com.alphatracker.api.trade.AttachmentType;
import com.alphatracker.api.trade.Trade;
import com.alphatracker.api.trade.TradeAttachment;
import com.alphatracker.api.trade.TradeAttachmentRepository;
import com.alphatracker.api.trade.TradeAttachmentService;
import com.alphatracker.api.trade.TradeRepository;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TradeAttachmentServiceTest {

    @Mock
    private TradeAttachmentRepository attachmentRepository;

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private TradeAttachmentService attachmentService;

    private User mockUser;
    private Trade mockTrade;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("trader@alphatracker.com");

        mockTrade = Trade.builder()
                .id(50L)
                .user(mockUser)
                .build();
    }

    @Test
    @DisplayName("Should store the file and save an attachment row pointing at it")
    void uploadAttachmentStoresFileAndSavesRow() {
        InputStream content = new ByteArrayInputStream("bytes".getBytes());
        StoredFile stored = new StoredFile("1/uuid.png", 5L, "image/png");

        when(tradeRepository.findByIdAndUserId(50L, 1L)).thenReturn(Optional.of(mockTrade));
        when(storageService.store(eq(content), eq(5L), eq("chart.png"), eq("image/png"), eq(1L)))
                .thenReturn(stored);
        when(attachmentRepository.save(any(TradeAttachment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TradeAttachment result = attachmentService.uploadAttachment(50L, content, 5L, "chart.png",
                "image/png", "entry setup", mockUser);

        assertEquals("1/uuid.png", result.getStorageKey());
        assertEquals(AttachmentType.SCREENSHOT, result.getAttachmentType());
        assertEquals("entry setup", result.getCaption());
        assertEquals(mockTrade, result.getTrade());
        verify(attachmentRepository, times(1)).save(any(TradeAttachment.class));
    }

    @Test
    @DisplayName("Should reject an upload onto a trade that doesn't belong to the requester")
    void uploadAttachmentRejectsUnownedTrade() {
        when(tradeRepository.findByIdAndUserId(50L, 1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> attachmentService.uploadAttachment(
                50L, new ByteArrayInputStream("x".getBytes()), 1L, "a.png", "image/png", null, mockUser));

        verify(storageService, never()).store(any(), anyLong(), any(), any(), any());
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject an unsupported content type before ever touching storage")
    void uploadAttachmentRejectsUnsupportedContentType() {
        when(tradeRepository.findByIdAndUserId(50L, 1L)).thenReturn(Optional.of(mockTrade));

        assertThrows(IllegalArgumentException.class, () -> attachmentService.uploadAttachment(
                50L, new ByteArrayInputStream("x".getBytes()), 1L, "malware.exe",
                "application/x-msdownload", null, mockUser));

        verify(storageService, never()).store(any(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("Should reject a file over the size limit before ever touching storage")
    void uploadAttachmentRejectsOversizedFile() {
        when(tradeRepository.findByIdAndUserId(50L, 1L)).thenReturn(Optional.of(mockTrade));

        long tooLarge = 11L * 1024 * 1024;
        assertThrows(IllegalArgumentException.class, () -> attachmentService.uploadAttachment(
                50L, new ByteArrayInputStream("x".getBytes()), tooLarge, "huge.png",
                "image/png", null, mockUser));

        verify(storageService, never()).store(any(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("Should delete both the stored file and the row when the owner deletes an attachment")
    void deleteAttachmentRemovesFileAndRow() {
        TradeAttachment attachment = TradeAttachment.builder()
                .id(200L)
                .trade(mockTrade)
                .storageKey("1/uuid.png")
                .attachmentType(AttachmentType.SCREENSHOT)
                .build();

        when(attachmentRepository.findByIdAndTrade_User_Id(200L, 1L)).thenReturn(Optional.of(attachment));

        attachmentService.deleteAttachment(200L, mockUser);

        verify(storageService, times(1)).delete("1/uuid.png");
        verify(attachmentRepository, times(1)).delete(attachment);
    }

    @Test
    @DisplayName("Should throw when deleting an attachment that doesn't belong to the requester")
    void deleteAttachmentRejectsUnownedAttachment() {
        when(attachmentRepository.findByIdAndTrade_User_Id(200L, 1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> attachmentService.deleteAttachment(200L, mockUser));

        verify(storageService, never()).delete(any());
        verify(attachmentRepository, never()).delete(any(TradeAttachment.class));
    }

    @Test
    @DisplayName("Should delete every attachment's file before removing the rows when a trade is deleted")
    void deleteAllAttachmentsForTradeClearsFilesThenRows() {
        TradeAttachment first = TradeAttachment.builder().id(1L).trade(mockTrade).storageKey("1/a.png").build();
        TradeAttachment second = TradeAttachment.builder().id(2L).trade(mockTrade).storageKey("1/b.png").build();

        when(attachmentRepository.findAllByTrade_IdAndTrade_User_IdOrderByUploadedAtAsc(50L, 1L))
                .thenReturn(List.of(first, second));

        attachmentService.deleteAllAttachmentsForTrade(mockTrade);

        verify(storageService, times(1)).delete("1/a.png");
        verify(storageService, times(1)).delete("1/b.png");
        verify(attachmentRepository, times(1)).deleteAll(List.of(first, second));
    }

    @Test
    @DisplayName("Should return attachments for a trade in the order the repository provides")
    void getAttachmentsForTradeDelegatesToOwnershipScopedQuery() {
        TradeAttachment attachment = TradeAttachment.builder().id(1L).trade(mockTrade).storageKey("1/a.png").build();
        when(attachmentRepository.findAllByTrade_IdAndTrade_User_IdOrderByUploadedAtAsc(50L, 1L))
                .thenReturn(List.of(attachment));

        List<TradeAttachment> result = attachmentService.getAttachmentsForTrade(50L, mockUser);

        assertEquals(1, result.size());
        assertEquals("1/a.png", result.get(0).getStorageKey());
    }
}
