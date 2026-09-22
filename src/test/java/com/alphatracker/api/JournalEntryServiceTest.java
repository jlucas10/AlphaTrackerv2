package com.alphatracker.api;

import com.alphatracker.api.journal.JournalEntry;
import com.alphatracker.api.journal.JournalEntryRepository;
import com.alphatracker.api.journal.JournalEntryService;
import com.alphatracker.api.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class JournalEntryServiceTest {

    @Mock
    private JournalEntryRepository journalEntryRepository;

    @InjectMocks
    private JournalEntryService journalEntryService;

    private User mockUser;
    private final LocalDate day = LocalDate.of(2026, 9, 18);

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
    }

    @Test
    @DisplayName("findOrCreate returns the existing entry without creating a new one")
    void findOrCreateReturnsExistingEntry() {
        JournalEntry existing = JournalEntry.builder().id(5L).user(mockUser).entryDate(day).build();
        when(journalEntryRepository.findByUser_IdAndEntryDate(1L, day)).thenReturn(Optional.of(existing));

        JournalEntry result = journalEntryService.findOrCreate(day, mockUser);

        assertEquals(5L, result.getId());
        verify(journalEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("findOrCreate persists a new entry when none exists for the day yet")
    void findOrCreatePersistsNewEntry() {
        when(journalEntryRepository.findByUser_IdAndEntryDate(1L, day)).thenReturn(Optional.empty());
        when(journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        JournalEntry result = journalEntryService.findOrCreate(day, mockUser);

        assertEquals(day, result.getEntryDate());
        assertEquals(mockUser, result.getUser());
        verify(journalEntryRepository, times(1)).save(any(JournalEntry.class));
    }

    @Test
    @DisplayName("getOrDefault returns an unsaved placeholder for a day with no entry yet")
    void getOrDefaultReturnsUnsavedPlaceholderWhenMissing() {
        when(journalEntryRepository.findByUser_IdAndEntryDate(1L, day)).thenReturn(Optional.empty());

        JournalEntry result = journalEntryService.getOrDefault(day, mockUser);

        assertNull(result.getId());
        assertEquals(day, result.getEntryDate());
        verify(journalEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("getOrDefault returns the persisted entry when one already exists")
    void getOrDefaultReturnsExistingEntry() {
        JournalEntry existing = JournalEntry.builder().id(5L).user(mockUser).entryDate(day).notes("hi").build();
        when(journalEntryRepository.findByUser_IdAndEntryDate(1L, day)).thenReturn(Optional.of(existing));

        JournalEntry result = journalEntryService.getOrDefault(day, mockUser);

        assertEquals(5L, result.getId());
        assertEquals("hi", result.getNotes());
    }

    @Test
    @DisplayName("upsertNotes creates the entry on first write and sets notes/bias")
    void upsertNotesCreatesEntryOnFirstWrite() {
        when(journalEntryRepository.findByUser_IdAndEntryDate(1L, day)).thenReturn(Optional.empty());
        when(journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        JournalEntry result = journalEntryService.upsertNotes(day, "Choppy session", "Bearish", mockUser);

        assertEquals("Choppy session", result.getNotes());
        assertEquals("Bearish", result.getHtfBias());
        assertNotNull(result.getUpdatedAt());
        // findOrCreate's own save (the empty placeholder) plus upsertNotes'
        // save of the populated entry - both go through the same repository.
        verify(journalEntryRepository, times(2)).save(any(JournalEntry.class));
    }

    @Test
    @DisplayName("upsertNotes overwrites notes/bias on an entry that already exists")
    void upsertNotesOverwritesExistingEntry() {
        JournalEntry existing = JournalEntry.builder().id(5L).user(mockUser).entryDate(day)
                .notes("old notes").htfBias("Neutral").build();
        when(journalEntryRepository.findByUser_IdAndEntryDate(1L, day)).thenReturn(Optional.of(existing));
        when(journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        JournalEntry result = journalEntryService.upsertNotes(day, "new notes", "Bullish", mockUser);

        assertEquals(5L, result.getId());
        assertEquals("new notes", result.getNotes());
        assertEquals("Bullish", result.getHtfBias());
        verify(journalEntryRepository, times(1)).save(any(JournalEntry.class));
    }
}
