package com.alphatracker.api.journal;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alphatracker.api.storage.StorageService;
import com.alphatracker.api.storage.StoredFile;
import com.alphatracker.api.trade.AttachmentType;
import com.alphatracker.api.user.User;

import lombok.RequiredArgsConstructor;

// Business logic for day-journal screenshots: validates uploads, enforces
// ownership, and keeps JournalAttachment rows (Postgres) and their bytes
// (StorageService) in sync - every write here either updates both or
// neither, never one alone.
@Service
@RequiredArgsConstructor
public class JournalAttachmentService {

    // Screenshots only, and capped well above what a screen-capture tool
    // produces - this exists to stop a single oversized upload from filling
    // disk, not to be a tight limit traders will ever bump into.
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp", "image/gif");

    private final JournalAttachmentRepository attachmentRepository;
    private final JournalEntryService journalEntryService;
    private final StorageService storageService;

    // Uploads one screenshot onto a day's journal entry, creating that entry
    // if this is the first write for the day (see JournalEntryService.findOrCreate).
    @Transactional
    public JournalAttachment uploadAttachment(LocalDate date, InputStream content, long sizeBytes,
            String originalFilename, String contentType, String caption, User authenticatedUser) {
        validateContentType(contentType);
        validateSize(sizeBytes);

        JournalEntry entry = journalEntryService.findOrCreate(date, authenticatedUser);

        StoredFile stored = storageService.store(content, sizeBytes, originalFilename, contentType,
                authenticatedUser.getId());

        JournalAttachment attachment = JournalAttachment.builder()
                .journalEntry(entry)
                .storageKey(stored.storageKey())
                .attachmentType(AttachmentType.SCREENSHOT)
                .contentType(stored.contentType())
                .sizeBytes(stored.sizeBytes())
                .uploadedAt(LocalDateTime.now())
                .caption(caption)
                .build();

        return attachmentRepository.save(attachment);
    }

    // A day with no JournalEntry yet (nothing written for it) simply has no
    // attachments - no row needs to exist just to answer this query.
    @Transactional(readOnly = true)
    public List<JournalAttachment> getAttachmentsForDate(LocalDate date, User authenticatedUser) {
        JournalEntry entry = journalEntryService.getOrDefault(date, authenticatedUser);
        if (entry.getId() == null) {
            return List.of();
        }
        return attachmentRepository.findAllByJournalEntry_IdOrderByUploadedAtAsc(entry.getId());
    }

    // Returns the attachment row (metadata + ownership check) so a controller
    // can set the right Content-Type/filename before streaming the bytes.
    @Transactional(readOnly = true)
    public JournalAttachment getAttachmentForDownload(Long attachmentId, User authenticatedUser) {
        return attachmentRepository.findByIdAndJournalEntry_User_Id(attachmentId, authenticatedUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found or does not belong to user."));
    }

    // Kept separate from getAttachmentForDownload so a controller can send
    // headers (built from the already-fetched row) before opening the byte
    // stream, instead of buffering the whole file in memory first.
    public InputStream openAttachmentContent(JournalAttachment attachment) {
        return storageService.retrieve(attachment.getStorageKey());
    }

    @Transactional
    public void deleteAttachment(Long attachmentId, User authenticatedUser) {
        JournalAttachment attachment = attachmentRepository
                .findByIdAndJournalEntry_User_Id(attachmentId, authenticatedUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found or does not belong to user."));

        storageService.delete(attachment.getStorageKey());
        attachmentRepository.delete(attachment);
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
