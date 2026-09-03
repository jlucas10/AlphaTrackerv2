package com.alphatracker.api.account;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {
    private Long id;
    private String name;
    private String firm;
    private AccountType accountType;
    private Double startingBalance;
    private Double currentBalance;
    private Double profitTarget;
    private Double maxDrawdown;
    private Boolean active;
    private LocalDateTime createdAt;

    public static AccountResponse fromEntity(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .name(account.getName())
                .firm(account.getFirm())
                .accountType(account.getAccountType())
                .startingBalance(account.getStartingBalance())
                .currentBalance(account.getCurrentBalance())
                .profitTarget(account.getProfitTarget())
                .maxDrawdown(account.getMaxDrawdown())
                .active(account.getActive())
                .createdAt(account.getCreatedAt())
                .build();
    }

}
