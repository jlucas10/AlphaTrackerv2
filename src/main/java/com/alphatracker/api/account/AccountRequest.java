package com.alphatracker.api.account;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountRequest {
    // DTOs isolate the database from the client payload.
    private String name;
    private String firm;
    private AccountType accountType;
    private Double startingBalance;
    private Double profitTarget;
    private Double maxDrawdown;
}
