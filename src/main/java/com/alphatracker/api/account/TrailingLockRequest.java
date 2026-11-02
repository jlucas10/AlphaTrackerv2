package com.alphatracker.api.account;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// PATCH /api/v1/accounts/{id}/trailing-lock write contract: just the new lock balance (null clears it).
// A DTO, like every other request here, so this endpoint can't change any other account field.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrailingLockRequest {
    private Double trailingStopsAtBalance;
}
