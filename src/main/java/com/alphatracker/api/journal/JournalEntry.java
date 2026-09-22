package com.alphatracker.api.journal;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.alphatracker.api.user.User;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
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

// One row per (user, calendar day) - notes and HTF bias live here rather than
// on Trade, since a trading day can hold several trades but the trader
// reflects on the session as a whole, not each execution separately. Created
// lazily on first write (see JournalEntryService.findOrCreate); there's no
// user-visible "start a journal entry" step.
@Entity
@Table(name = "journal_entry", uniqueConstraints = @UniqueConstraint(columnNames = { "user_id", "entry_date" }))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Column(nullable = false)
    private LocalDate entryDate;

    @Column(length = 4000)
    private String notes;

    // Higher-timeframe bias for the session, e.g. "Bullish" / "Bearish" /
    // "Neutral" - freeform string rather than an enum so a trader's own
    // phrasing isn't forced into a fixed set from day one.
    private String htfBias;

    private LocalDateTime updatedAt;
}
