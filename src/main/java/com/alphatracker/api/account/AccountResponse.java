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
    private DrawdownMode drawdownMode;
    private Double trailingStopsAtBalance;
    private Double highWaterMark;
    private Double drawdownFloor;
    private Boolean active;
    private LocalDateTime createdAt;

    // highWaterMark/drawdownFloor are computed by AccountService.computeDrawdownSnapshot
    // from the account's full trade history, so they're passed in rather than
    // read off the entity the way every other field here is.
    public static AccountResponse fromEntity(Account account, DrawdownSnapshot snapshot) {
        return AccountResponse.builder()
                .id(account.getId())
                .name(account.getName())
                .firm(account.getFirm())
                .accountType(account.getAccountType())
                .startingBalance(account.getStartingBalance())
                .currentBalance(account.getCurrentBalance())
                .profitTarget(account.getProfitTarget())
                .maxDrawdown(account.getMaxDrawdown())
                .drawdownMode(account.getDrawdownMode() == null ? DrawdownMode.END_OF_DAY : account.getDrawdownMode())
                .trailingStopsAtBalance(account.getTrailingStopsAtBalance())
                .highWaterMark(snapshot.highWaterMark())
                .drawdownFloor(snapshot.drawdownFloor())
                .active(account.getActive())
                .createdAt(account.getCreatedAt())
                .build();
    }

}
