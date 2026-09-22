package com.alphatracker.api.journal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JournalAttachmentRepository extends JpaRepository<JournalAttachment, Long> {

    // Ownership baked directly into the query (journal_entry_id ->
    // journal_entry.user_id) - there's no code path here that returns a row
    // without also proving it belongs to userId.
    Optional<JournalAttachment> findByIdAndJournalEntry_User_Id(Long id, Long userId);

    List<JournalAttachment> findAllByJournalEntry_IdOrderByUploadedAtAsc(Long journalEntryId);

    // long (not void) so callers can tell whether a row actually existed - a
    // delete of an id that doesn't belong to this user should read as "not
    // found," not silently succeed.
    long deleteByIdAndJournalEntry_User_Id(Long id, Long userId);
}
