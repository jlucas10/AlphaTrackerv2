package com.alphatracker.api.journal;

import java.time.LocalDateTime;

import com.alphatracker.api.trade.AttachmentType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// What the frontend actually receives - notably, never the raw storageKey.
// "url" points at the ownership-checked retrieval endpoint instead, so the
// browser fetches bytes through JournalAttachmentController (which re-checks
// ownership on every request) rather than through anything that could be
// replayed against storage directly.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalAttachmentResponse {
    private Long id;
    private Long journalEntryId;
    private AttachmentType attachmentType;
    private String contentType;
    private Long sizeBytes;
    private String caption;
    private LocalDateTime uploadedAt;
    private String url;

    public static JournalAttachmentResponse fromEntity(JournalAttachment attachment) {
        return JournalAttachmentResponse.builder()
                .id(attachment.getId())
                .journalEntryId(attachment.getJournalEntry().getId())
                .attachmentType(attachment.getAttachmentType())
                .contentType(attachment.getContentType())
                .sizeBytes(attachment.getSizeBytes())
                .caption(attachment.getCaption())
                .uploadedAt(attachment.getUploadedAt())
                .url("/api/v1/journal-attachments/" + attachment.getId() + "/file")
                .build();
    }
}
