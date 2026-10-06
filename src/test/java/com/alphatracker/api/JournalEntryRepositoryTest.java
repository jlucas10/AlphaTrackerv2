package com.alphatracker.api;

import com.alphatracker.api.journal.JournalEntry;
import com.alphatracker.api.journal.JournalEntryRepository;
import com.alphatracker.api.user.Role;
import com.alphatracker.api.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

// Runs against the real Postgres datasource (no H2 on the classpath), same as
// TradeAttachmentRepositoryTest - @DataJpaTest rolls back its transaction
// after each test, so nothing persists to the dev database.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class JournalEntryRepositoryTest {

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private EntityManager entityManager;

    private User owner;
    private User otherUser;

    @BeforeEach
    void setUp() {
        owner = persistUser("journal-owner@alphatracker.com");
        otherUser = persistUser("journal-other@alphatracker.com");
    }

    @Test
    @DisplayName("findByUser_IdAndEntryDate finds the entry for its owner and that exact day")
    void findsEntryForOwnerAndDate() {
        LocalDate day = LocalDate.of(2026, 9, 18);
        JournalEntry entry = JournalEntry.builder().user(owner).entryDate(day).notes("Solid session").build();
        journalEntryRepository.save(entry);
        entityManager.flush();

        Optional<JournalEntry> found = journalEntryRepository.findByUser_IdAndEntryDate(owner.getId(), day);

        assertTrue(found.isPresent());
        assertEquals("Solid session", found.get().getNotes());
    }

    @Test
    @DisplayName("findByUser_IdAndEntryDate never returns another user's entry for the same day")
    void doesNotLeakAcrossUsers() {
        LocalDate day = LocalDate.of(2026, 9, 18);
        journalEntryRepository.save(JournalEntry.builder().user(owner).entryDate(day).notes("Owner's day").build());
        entityManager.flush();

        Optional<JournalEntry> asOtherUser = journalEntryRepository.findByUser_IdAndEntryDate(otherUser.getId(), day);

        assertTrue(asOtherUser.isEmpty());
    }

    @Test
    @DisplayName("range query is inclusive on both end dates, ordered by date, and excludes other users' entries")
    void rangeIsInclusiveOrderedAndScopedToUser() {
        for (int d : new int[] { 17, 18, 20, 21 }) {
            journalEntryRepository.save(JournalEntry.builder().user(owner).entryDate(LocalDate.of(2026, 9, d)).build());
        }
        journalEntryRepository.save(JournalEntry.builder().user(otherUser).entryDate(LocalDate.of(2026, 9, 19)).build());
        entityManager.flush();

        List<JournalEntry> result = journalEntryRepository.findAllByUser_IdAndEntryDateBetweenOrderByEntryDateAsc(
                owner.getId(), LocalDate.of(2026, 9, 18), LocalDate.of(2026, 9, 20));

        assertEquals(List.of(LocalDate.of(2026, 9, 18), LocalDate.of(2026, 9, 20)),
                result.stream().map(JournalEntry::getEntryDate).toList());
    }

    private User persistUser(String email) {
        User user = User.builder().email(email).password("hashed").firstName("Test").role(Role.USER).build();
        entityManager.persist(user);
        return user;
    }
}
