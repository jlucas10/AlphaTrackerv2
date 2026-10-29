package com.alphatracker.api.account;

import java.time.LocalDate;
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
    // Profit-target progress and breach facts, from the same replay as the two figures above
    // (AccountService.replay). Derived on every read, never stored.
    private Double profit;
    private Double targetProgress;
    private LocalDate targetReachedOn;
    private LocalDate breachedOn;
    private AccountStatus suggestedStatus;
    private Boolean isPrimary;
    private AccountStatus status;
    private LocalDateTime closedAt;
    private Long promotedFromAccountId;
    // Kept for existing clients; now simply "status is ACTIVE".
    private Boolean active;
    private LocalDateTime createdAt;

    // highWaterMark/drawdownFloor and the progress fields are computed by AccountService.replay
    // from the account's full trade history, so they're passed in rather than
    // read off the entity the way every other field here is.
    public static AccountResponse fromEntity(Account account, DrawdownSnapshot snapshot, AccountProgress progress) {
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
                .profit(progress.profit())
                .targetProgress(progress.targetProgress())
                .targetReachedOn(progress.targetReachedOn())
                .breachedOn(progress.breachedOn())
                .suggestedStatus(progress.suggestedStatus())
                .isPrimary(Boolean.TRUE.equals(account.getIsPrimary()))
                .status(account.currentStatus())
                .closedAt(account.getClosedAt())
                .promotedFromAccountId(account.getPromotedFromAccountId())
                .active(account.currentStatus() == AccountStatus.ACTIVE)
                .createdAt(account.getCreatedAt())
                .build();
    }

}
