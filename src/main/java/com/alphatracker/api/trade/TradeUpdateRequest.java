package com.alphatracker.api.trade;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// PATCH /api/v1/trades/{id} write contract. Deliberately narrow - only the
// trade-level reflection fields (Sprint 3.5) are editable here. Everything
// that derives money (ticker, prices, contracts, profitLoss, commission) is
// immutable after creation, same as it always has been.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeUpdateRequest {
    private Integer executionRating;
    private List<String> setupTags;
}
