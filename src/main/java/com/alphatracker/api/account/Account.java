package com.alphatracker.api.account;

import com.alphatracker.api.user.User;

import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // Apex 50k #1

    @Column(nullable = false)
    private String firm; // Apex, Topstep, MFF, Lucid, etc.

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType accountType;

    @Column(nullable = false)
    private Double startingBalance; // 50,000

    @Column(nullable = false)
    private Double currentBalance; // starting balance + p/l

    private Double profitTarget; // 3,0000

    @Column(nullable = false)
    private Double maxDrawdown; // 2,000

    // Nullable, like commission on Trade: ddl-auto=update cannot add a NOT NULL
    // column to a table that already holds rows. Null is treated as END_OF_DAY
    // everywhere this is read.
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DrawdownMode drawdownMode = DrawdownMode.END_OF_DAY;

    // Once the high-water mark minus maxDrawdown would exceed this balance, the
    // floor stops trailing upward and locks here. Null means the floor trails
    // indefinitely with no lock-in point.
    private Double trailingStopsAtBalance;

    // Exactly one account per user should have this true at a time; enforced in
    // AccountService.setPrimaryAccount, not at the schema level. Nullable for the
    // same ddl-auto=update reason as drawdownMode above — null reads as false.
    private Boolean isPrimary;

    @Builder.Default
    private Boolean active = true;

    // Where the account is in its life (see AccountStatus). NULLABLE on purpose: ddl-auto=update cannot add
    // a NOT NULL column to a table that already holds rows, so every account created before this column
    // existed has null here and reads as ACTIVE (see currentStatus()). Supersedes the boolean `active`
    // above, which AccountService keeps in step but nothing reads any more.
    @Enumerated(EnumType.STRING)
    private AccountStatus status;

    // When the account was marked PASSED or FAILED; null while ACTIVE.
    private LocalDateTime closedAt;

    // For a funded account created after passing an evaluation: the id of that evaluation. A plain id and
    // not a foreign key on purpose: it is display lineage ("funded from Apex 50k #1"), and a real FK would
    // only complicate deleting accounts later. The UI looks the name up in the accounts list.
    private Long promotedFromAccountId;

    // Not a getter (no "get" prefix), so it is never serialized or mistaken for a column.
    public AccountStatus currentStatus() {
        return status == null ? AccountStatus.ACTIVE : status;
    }

    private LocalDateTime createdAt; // Account creation metadata

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore // Prevent circular JSON - Prevents serializing the user's password/auth details
                // into account responses
    private User user;

    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.currentBalance == null) {
            this.currentBalance = this.startingBalance;
        }
    }
}
