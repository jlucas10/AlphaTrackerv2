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

    @Builder.Default
    private Boolean active = true;

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
