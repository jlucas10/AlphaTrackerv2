package com.alphatracker.api.trade;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.alphatracker.api.account.Account;
import com.alphatracker.api.importer.ImportSource;
import com.alphatracker.api.user.User;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
// One broker trade can be imported once PER ACCOUNT (copy-trading means the same file legitimately goes into
// several accounts), so the key is (account, source, external id). Hand-logged trades have null source and
// external id, and Postgres treats NULLs as distinct, so any number of them can coexist.
@Table(name = "trade", uniqueConstraints = @UniqueConstraint(
        name = "uk_trade_account_source_external_id", columnNames = { "account_id", "source", "external_id" }))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String ticker; // Ticker: ES, MES, NQ, MNQ

    @Column(nullable = false)
    private String direction; // Long or Short

    @Column(nullable = false)
    private Double entryPrice;

    @Column(nullable = false)
    private Double exitPrice;

    @Column(nullable = false)
    private Integer contracts; // num of lots

    // Gross move x point value x contracts, no commission subtracted - see
    // Instrument for why. This is the number that hits the account.
    @Column(nullable = false)
    private Double profitLoss;

    // Discipline flag - feeds the Discipline Score on the dashboard.
    private Boolean followedPlan;

    @Column(length = 1000) // Grants extra string length for in depth psychology notes
    private String notes; // e.g., "FVG fill or swept liquidity"

    @Column(nullable = false)
    private LocalDateTime tradeDate;

    // Establishes the One-to-Many mapping.
    // Tells JPA to link this trade row back to a specific User's primary key
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = true)
    @JsonIgnore
    private Account account;

    // Where this trade came from. Null = logged by hand. Nullable (like Account.status) because
    // ddl-auto=update cannot add a NOT NULL column to a table that already holds rows.
    @Enumerated(EnumType.STRING)
    private ImportSource source;

    // The broker's own id for the trade (TopstepX "Id"). With the account and source it makes a re-import
    // detectable. Null for hand-logged trades.
    private String externalId;

    // Trade-level reflection fields (Sprint 3.5). Distinct from `notes` above
    // (the original quick-capture field from trade entry) and from
    // JournalEntry.notes/htfBias (day-level reflection) - these grade one
    // specific execution, since different trades in the same day can use
    // different setups and execute with different quality.
    private Integer executionRating; // 1-5, self-graded

    // Freeform, reusable tag strings rather than a separate Tag entity -
    // still gives real multi-select chips in the UI (autocompleting over a
    // trader's own previously-used tags), just without a whole CRUD surface
    // for a bounded, low-cardinality set of short strings.
    @ElementCollection
    @CollectionTable(name = "trade_setup_tag", joinColumns = @JoinColumn(name = "trade_id"))
    @Column(name = "tag")
    @Builder.Default
    private List<String> setupTags = new ArrayList<>();

}