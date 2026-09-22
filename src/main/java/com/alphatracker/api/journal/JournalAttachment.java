package com.alphatracker.api.journal;

import java.time.LocalDateTime;

import com.alphatracker.api.trade.AttachmentType;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// A screenshot/media file attached to a day's JournalEntry (not a single
// Trade - see Sprint 3.5 in CONTEXT.md for why this moved day-level). This
// row never holds the file bytes themselves - storageKey is the opaque
// pointer StorageService.store() returned, and ownership is always resolved
// by walking journalEntry -> user, never trusted on this entity directly.
@Entity
@Table(name = "journal_attachment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    @JsonIgnore
    private JournalEntry journalEntry;

    // Opaque key handed back by StorageService.store(). Never exposed to the
    // frontend directly - retrieval goes through an ownership-checked
    // endpoint, never a raw storageKey/path.
    @Column(nullable = false)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttachmentType attachmentType;

    @Column(nullable = false)
    private String contentType; // e.g. "image/png" - needed to set the Content-Type header on retrieval

    @Column(nullable = false)
    private Long sizeBytes;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    private String caption; // optional trader-written note on the screenshot
}
