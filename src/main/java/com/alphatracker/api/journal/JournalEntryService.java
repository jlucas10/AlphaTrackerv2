package com.alphatracker.api.journal;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alphatracker.api.user.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JournalEntryService {

    private final JournalEntryRepository journalEntryRepository;

    // The only place a JournalEntry row gets created. There's no separate
    // "start today's journal entry" action in the UI - the first note, bias,
    // or screenshot for a day creates the row implicitly.
    @Transactional
    public JournalEntry findOrCreate(LocalDate date, User user) {
        return journalEntryRepository.findByUser_IdAndEntryDate(user.getId(), date)
                .orElseGet(() -> journalEntryRepository.save(
                        JournalEntry.builder()
                                .user(user)
                                .entryDate(date)
                                .updatedAt(LocalDateTime.now())
                                .build()));
    }

    @Transactional(readOnly = true)
    public JournalEntry getOrDefault(LocalDate date, User user) {
        return journalEntryRepository.findByUser_IdAndEntryDate(user.getId(), date)
                // Not persisted - a day with no notes/bias/screenshots yet has
                // nothing worth writing to the database just to answer a GET.
                .orElseGet(() -> JournalEntry.builder().user(user).entryDate(date).build());
    }

    // Max span of a range read, inclusive of both ends. Bounds the response
    // size (and so what an assistant can pull into its context in one call).
    static final int MAX_RANGE_DAYS = 366;

    // Entries between two dates, both inclusive. Rows with neither notes nor a
    // bias are dropped: those exist only because a screenshot was uploaded
    // (findOrCreate), and have nothing to say for a text review.
    @Transactional(readOnly = true)
    public List<JournalEntry> getRange(LocalDate from, LocalDate to, User user) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must not be after 'to'.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Date range too large: at most " + MAX_RANGE_DAYS + " days.");
        }
        return journalEntryRepository.findAllByUser_IdAndEntryDateBetweenOrderByEntryDateAsc(user.getId(), from, to)
                .stream()
                .filter(e -> hasText(e.getNotes()) || hasText(e.getHtfBias()))
                .toList();
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    @Transactional
    public JournalEntry upsertNotes(LocalDate date, String notes, String htfBias, User user) {
        JournalEntry entry = findOrCreate(date, user);
        entry.setNotes(notes);
        entry.setHtfBias(htfBias);
        entry.setUpdatedAt(LocalDateTime.now());
        return journalEntryRepository.save(entry);
    }
}
