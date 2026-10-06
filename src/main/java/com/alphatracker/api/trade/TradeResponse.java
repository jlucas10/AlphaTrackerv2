package com.alphatracker.api.trade;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// What every trade endpoint returns instead of the Trade entity. Trade embeds
// a full User, so serializing the entity directly meant the user's profile
// rode along on every trade (and the bcrypt hash would have too, were it not
// for the @JsonIgnore on User.password). A DTO makes the response shape an
// explicit allow-list: a field added to Trade later is NOT exposed unless it's
// added here on purpose.
//
// accountId is new - Trade.account is @JsonIgnore'd, so clients previously
// couldn't tell which account a trade belonged to.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeResponse {
    private Long id;
    private String ticker;
    private String direction;
    private Double entryPrice;
    private Double exitPrice;
    private Integer contracts;
    private Double profitLoss;
    private Boolean followedPlan;
    private String notes;
    private LocalDateTime tradeDate;
    private Integer executionRating;
    private List<String> setupTags;
    private Long accountId;

    public static TradeResponse fromEntity(Trade trade) {
        return TradeResponse.builder()
                .id(trade.getId())
                .ticker(trade.getTicker())
                .direction(trade.getDirection())
                .entryPrice(trade.getEntryPrice())
                .exitPrice(trade.getExitPrice())
                .contracts(trade.getContracts())
                .profitLoss(trade.getProfitLoss())
                .followedPlan(trade.getFollowedPlan())
                .notes(trade.getNotes())
                .tradeDate(trade.getTradeDate())
                .executionRating(trade.getExecutionRating())
                // Copied: don't hand out Hibernate's live PersistentBag.
                .setupTags(trade.getSetupTags() == null ? List.of() : List.copyOf(trade.getSetupTags()))
                // getId() on the lazy Account proxy doesn't trigger a load.
                .accountId(trade.getAccount() == null ? null : trade.getAccount().getId())
                .build();
    }
}
