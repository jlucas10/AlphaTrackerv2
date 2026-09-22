package com.alphatracker.api.journal;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.alphatracker.api.trade.Trade;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// The full "day bundle" GET/PUT /api/v1/journal/{date} returns - everything
// the journal day panel needs in one call: the day's reflection (notes/bias),
// its screenshots, and the trades executed that day (read-only here; trades
// are edited via PATCH /api/v1/trades/{id}, not through this endpoint).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalDayResponse {
    private LocalDate entryDate;
    private String notes;
    private String htfBias;
    private LocalDateTime updatedAt;
    private List<JournalAttachmentResponse> attachments;
    private List<Trade> trades;

    public static JournalDayResponse fromEntities(LocalDate date, JournalEntry entry,
            List<JournalAttachment> attachments, List<Trade> trades) {
        return JournalDayResponse.builder()
                .entryDate(date)
                .notes(entry.getNotes())
                .htfBias(entry.getHtfBias())
                .updatedAt(entry.getUpdatedAt())
                .attachments(attachments.stream().map(JournalAttachmentResponse::fromEntity).toList())
                .trades(trades)
                .build();
    }
}
