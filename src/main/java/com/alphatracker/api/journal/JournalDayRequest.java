package com.alphatracker.api.journal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// PUT /api/v1/journal/{date} write contract. Deliberately just notes + bias -
// attachments have their own upload endpoint, and trades aren't editable
// through the journal at all (that's PATCH /api/v1/trades/{id}).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalDayRequest {
    private String notes;
    private String htfBias;
}
