package com.alphatracker.api.account;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// PATCH /api/v1/accounts/{id}/status write contract: just the new status. A DTO, like every other
// request here, so a client can't smuggle in any other account field through this endpoint.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountStatusRequest {
    private AccountStatus status;
}
