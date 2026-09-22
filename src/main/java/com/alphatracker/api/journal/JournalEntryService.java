package com.alphatracker.api.journal;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    @Transactional
    public JournalEntry upsertNotes(LocalDate date, String notes, String htfBias, User user) {
        JournalEntry entry = findOrCreate(date, user);
        entry.setNotes(notes);
        entry.setHtfBias(htfBias);
        entry.setUpdatedAt(LocalDateTime.now());
        return journalEntryRepository.save(entry);
    }
}
