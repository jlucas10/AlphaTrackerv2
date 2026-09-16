package com.alphatracker.api.trade;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// What the frontend actually receives - notably, never the raw storageKey.
// "url" points at the ownership-checked retrieval endpoint instead, so the
// browser fetches bytes through TradeAttachmentController (which re-checks
// ownership on every request) rather than through anything that could be
// replayed against storage directly.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeAttachmentResponse {
    private Long id;
    private Long tradeId;
    private AttachmentType attachmentType;
    private String contentType;
    private Long sizeBytes;
    private String caption;
    private LocalDateTime uploadedAt;
    private String url;

    public static TradeAttachmentResponse fromEntity(TradeAttachment attachment) {
        return TradeAttachmentResponse.builder()
                .id(attachment.getId())
                .tradeId(attachment.getTrade().getId())
                .attachmentType(attachment.getAttachmentType())
                .contentType(attachment.getContentType())
                .sizeBytes(attachment.getSizeBytes())
                .caption(attachment.getCaption())
                .uploadedAt(attachment.getUploadedAt())
                .url("/api/v1/attachments/" + attachment.getId() + "/file")
                .build();
    }
}
