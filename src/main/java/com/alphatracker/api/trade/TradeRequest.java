package com.alphatracker.api.trade;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// The write contract for logging an execution — everything the trader actually
// knows at the moment they close a position, and nothing else.
//
// Notably absent, all by design:
//   id            - binding the entity directly let a client POST an existing id
//                   and have save() quietly UPDATE that row. No id, no mass assignment.
//   user          - taken from the JWT in the controller, never from the body.
//   profitLoss    - derived server-side from the instrument's point value.
//   commission    - derived server-side from the instrument's round-turn fee.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeRequest {

    private String ticker; // ES, NQ, MNQ, MES... resolved via Instrument
    private String direction; // LONG or SHORT
    private Double entryPrice;
    private Double exitPrice;
    private Integer contracts; // number of lots: 1, 2, 3...
    private Boolean followedPlan; // discipline flag for the journal
    private String notes;
    private LocalDateTime tradeDate; // optional; defaults to now if omitted
    private Long accountId;
}
