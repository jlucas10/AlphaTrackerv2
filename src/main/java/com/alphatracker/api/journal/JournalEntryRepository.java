package com.alphatracker.api.journal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    // The lookup findOrCreate() is built on - one entry per user per day, so
    // this pair uniquely identifies it (matches the unique constraint on the
    // table).
    Optional<JournalEntry> findByUser_IdAndEntryDate(Long userId, LocalDate entryDate);

    // Backs GET /api/v1/journal?from=&to=. Both ends inclusive. Between is
    // safe HERE - and only here - because entryDate is a LocalDate with no time
    // component; the same keyword on Trade.tradeDate (a LocalDateTime) is what
    // caused the midnight double-count bug, so trade queries use >= and <.
    List<JournalEntry> findAllByUser_IdAndEntryDateBetweenOrderByEntryDateAsc(
            Long userId, LocalDate from, LocalDate to);
}
