package com.alphatracker.api.journal;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    // The lookup findOrCreate() is built on - one entry per user per day, so
    // this pair uniquely identifies it (matches the unique constraint on the
    // table).
    Optional<JournalEntry> findByUser_IdAndEntryDate(Long userId, LocalDate entryDate);
}
