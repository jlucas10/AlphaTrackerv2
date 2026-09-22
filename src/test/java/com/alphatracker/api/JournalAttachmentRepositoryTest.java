package com.alphatracker.api;

import com.alphatracker.api.journal.JournalAttachment;
import com.alphatracker.api.journal.JournalAttachmentRepository;
import com.alphatracker.api.journal.JournalEntry;
import com.alphatracker.api.trade.AttachmentType;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class JournalAttachmentRepositoryTest {

    @Autowired
    private JournalAttachmentRepository attachmentRepository;

    @Autowired
    private EntityManager entityManager;

    private User owner;
    private User otherUser;
    private JournalEntry ownerEntry;

    @BeforeEach
    void setUp() {
        owner = persistUser("journal-attach-owner@alphatracker.com");
        otherUser = persistUser("journal-attach-other@alphatracker.com");
        ownerEntry = persistEntry(owner, LocalDate.of(2026, 9, 18));
    }

    @Test
    @DisplayName("findByIdAndJournalEntry_User_Id returns the attachment only for its actual owner")
    void findByIdAndOwnerRespectsOwnership() {
        JournalAttachment saved = attachmentRepository.save(newAttachment(ownerEntry, "1/a.png"));
        entityManager.flush();

        Optional<JournalAttachment> asOwner =
                attachmentRepository.findByIdAndJournalEntry_User_Id(saved.getId(), owner.getId());
        Optional<JournalAttachment> asIntruder =
                attachmentRepository.findByIdAndJournalEntry_User_Id(saved.getId(), otherUser.getId());

        assertTrue(asOwner.isPresent());
        assertTrue(asIntruder.isEmpty());
    }

    @Test
    @DisplayName("findAllByJournalEntry_IdOrderByUploadedAtAsc lists a day's attachments oldest first")
    void findAllForEntryReturnsChronologicalOrder() {
        JournalAttachment older = newAttachment(ownerEntry, "1/older.png");
        older.setUploadedAt(LocalDateTime.now().minusHours(2));
        JournalAttachment newer = newAttachment(ownerEntry, "1/newer.png");
        newer.setUploadedAt(LocalDateTime.now());
        attachmentRepository.save(newer);
        attachmentRepository.save(older);
        entityManager.flush();

        List<JournalAttachment> result = attachmentRepository.findAllByJournalEntry_IdOrderByUploadedAtAsc(ownerEntry.getId());

        assertEquals(2, result.size());
        assertEquals("1/older.png", result.get(0).getStorageKey());
        assertEquals("1/newer.png", result.get(1).getStorageKey());
    }

    @Test
    @DisplayName("deleteByIdAndJournalEntry_User_Id deletes nothing and reports 0 for the wrong owner")
    void deleteByWrongOwnerDeletesNothing() {
        JournalAttachment saved = attachmentRepository.save(newAttachment(ownerEntry, "1/a.png"));
        entityManager.flush();

        long deletedCount = attachmentRepository.deleteByIdAndJournalEntry_User_Id(saved.getId(), otherUser.getId());

        assertEquals(0, deletedCount);
        assertTrue(attachmentRepository.findById(saved.getId()).isPresent());
    }

    @Test
    @DisplayName("deleteByIdAndJournalEntry_User_Id removes the row and reports 1 for the real owner")
    void deleteByOwnerRemovesRow() {
        JournalAttachment saved = attachmentRepository.save(newAttachment(ownerEntry, "1/a.png"));
        entityManager.flush();

        long deletedCount = attachmentRepository.deleteByIdAndJournalEntry_User_Id(saved.getId(), owner.getId());

        assertEquals(1, deletedCount);
        assertTrue(attachmentRepository.findById(saved.getId()).isEmpty());
    }

    private JournalAttachment newAttachment(JournalEntry entry, String storageKey) {
        return JournalAttachment.builder()
                .journalEntry(entry)
                .storageKey(storageKey)
                .attachmentType(AttachmentType.SCREENSHOT)
                .contentType("image/png")
                .sizeBytes(1024L)
                .uploadedAt(LocalDateTime.now())
                .build();
    }

    private User persistUser(String email) {
        User user = User.builder().email(email).password("hashed").firstName("Test").role(Role.USER).build();
        entityManager.persist(user);
        return user;
    }

    private JournalEntry persistEntry(User forUser, LocalDate date) {
        JournalEntry entry = JournalEntry.builder().user(forUser).entryDate(date).build();
        entityManager.persist(entry);
        return entry;
    }
}
