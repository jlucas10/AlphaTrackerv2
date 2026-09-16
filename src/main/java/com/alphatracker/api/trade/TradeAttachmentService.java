package com.alphatracker.api.trade;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alphatracker.api.storage.StorageService;
import com.alphatracker.api.storage.StoredFile;
import com.alphatracker.api.user.User;

import lombok.RequiredArgsConstructor;

// Business logic for trade screenshots: validates uploads, enforces ownership,
// and keeps TradeAttachment rows (Postgres) and their bytes (StorageService)
// in sync - every write here either updates both or neither, never one alone.
@Service
@RequiredArgsConstructor
public class TradeAttachmentService {

    // Screenshots only, and capped well above what a screen-capture tool
    // produces - this exists to stop a single oversized upload from filling
    // disk, not to be a tight limit traders will ever bump into.
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp", "image/gif");

    private final TradeAttachmentRepository attachmentRepository;
    private final TradeRepository tradeRepository;
    private final StorageService storageService;

    // Uploads one screenshot onto an existing trade. Ownership is checked
    // against the trade (not the attachment, which doesn't exist yet) via the
    // same findByIdAndUserId pattern AccountService/TradeService already use.
    @Transactional
    public TradeAttachment uploadAttachment(Long tradeId, InputStream content, long sizeBytes,
            String originalFilename, String contentType, String caption, User authenticatedUser) {
        Trade trade = tradeRepository.findByIdAndUserId(tradeId, authenticatedUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("Trade not found or does not belong to user."));

        validateContentType(contentType);
        validateSize(sizeBytes);

        StoredFile stored = storageService.store(content, sizeBytes, originalFilename, contentType,
                authenticatedUser.getId());

        TradeAttachment attachment = TradeAttachment.builder()
                .trade(trade)
                .storageKey(stored.storageKey())
                .attachmentType(AttachmentType.SCREENSHOT)
                .contentType(stored.contentType())
                .sizeBytes(stored.sizeBytes())
                .uploadedAt(LocalDateTime.now())
                .caption(caption)
                .build();

        return attachmentRepository.save(attachment);
    }

    @Transactional(readOnly = true)
    public List<TradeAttachment> getAttachmentsForTrade(Long tradeId, User authenticatedUser) {
        return attachmentRepository.findAllByTrade_IdAndTrade_User_IdOrderByUploadedAtAsc(tradeId,
                authenticatedUser.getId());
    }

    // Returns the attachment row (metadata + ownership check) so a controller
    // can set the right Content-Type/filename before streaming the bytes.
    @Transactional(readOnly = true)
    public TradeAttachment getAttachmentForDownload(Long attachmentId, User authenticatedUser) {
        return attachmentRepository.findByIdAndTrade_User_Id(attachmentId, authenticatedUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found or does not belong to user."));
    }

    // Kept separate from getAttachmentForDownload so a controller can send
    // headers (built from the already-fetched row) before opening the byte
    // stream, instead of buffering the whole file in memory first.
    public InputStream openAttachmentContent(TradeAttachment attachment) {
        return storageService.retrieve(attachment.getStorageKey());
    }

    @Transactional
    public void deleteAttachment(Long attachmentId, User authenticatedUser) {
        TradeAttachment attachment = attachmentRepository.findByIdAndTrade_User_Id(attachmentId, authenticatedUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found or does not belong to user."));

        storageService.delete(attachment.getStorageKey());
        attachmentRepository.delete(attachment);
    }

    // Called by TradeService.deleteTrade before the trade row itself is
    // deleted. trade_attachment.trade_id is a non-nullable FK with no cascade
    // configured, so without this a trade with attachments would fail to
    // delete with a constraint violation instead of a clean 200. Deletes disk
    // files first, then rows, so a mid-failure never leaves an orphaned row
    // pointing at bytes that no longer exist.
    @Transactional
    public void deleteAllAttachmentsForTrade(Trade trade) {
        List<TradeAttachment> attachments = attachmentRepository
                .findAllByTrade_IdAndTrade_User_IdOrderByUploadedAtAsc(trade.getId(), trade.getUser().getId());

        for (TradeAttachment attachment : attachments) {
            storageService.delete(attachment.getStorageKey());
        }
        attachmentRepository.deleteAll(attachments);
    }

    private void validateContentType(String contentType) {
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Unsupported file type: " + contentType
                    + ". Allowed types: " + ALLOWED_CONTENT_TYPES);
        }
    }

    private void validateSize(long sizeBytes) {
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("Uploaded file is empty.");
        }
        if (sizeBytes > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File exceeds the " + (MAX_FILE_SIZE_BYTES / (1024 * 1024))
                    + "MB upload limit.");
        }
    }
}
