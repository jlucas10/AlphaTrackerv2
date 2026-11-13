package com.alphatracker.api.demo;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alphatracker.api.account.AccountRepository;
import com.alphatracker.api.journal.JournalAttachment;
import com.alphatracker.api.journal.JournalAttachmentRepository;
import com.alphatracker.api.journal.JournalEntryRepository;
import com.alphatracker.api.storage.StorageService;
import com.alphatracker.api.trade.TradeRepository;
import com.alphatracker.api.user.User;
import com.alphatracker.api.user.UserRepository;

import lombok.RequiredArgsConstructor;

// Puts the demo user back to the seed state: delete everything it owns, then seed again with dates
// anchored to today. Runs in a single transaction, so a failure part-way leaves the previous data intact.
@Service
@RequiredArgsConstructor
public class DemoResetService {

    private static final Logger log = LoggerFactory.getLogger(DemoResetService.class);

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TradeRepository tradeRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final JournalAttachmentRepository journalAttachmentRepository;
    private final StorageService storageService;
    private final DemoSeedService seedService;
    private final DemoProperties properties;

    @Transactional
    public void reset() {
        reset(LocalDate.now());
    }

    @Transactional
    public void reset(LocalDate today) {
        User user = userRepository.findByEmail(properties.getEmail()).orElse(null);
        if (user == null) {
            seedService.ensureSeeded();
            return;
        }
        DemoSeedService.requireDemo(user);

        // Children before parents: attachments -> journal entries -> trades -> accounts.
        List<JournalAttachment> attachments = journalAttachmentRepository.findAllByJournalEntry_User_Id(user.getId());
        List<String> storedKeys = attachments.stream().map(JournalAttachment::getStorageKey).toList();
        journalAttachmentRepository.deleteAll(attachments);
        journalAttachmentRepository.flush();
        journalEntryRepository.deleteAll(journalEntryRepository.findAllByUser_Id(user.getId()));
        journalEntryRepository.flush();
        tradeRepository.deleteAll(tradeRepository.findByUserId(user.getId()));
        tradeRepository.flush();
        accountRepository.deleteAll(accountRepository.findAllByUserId(user.getId()));
        accountRepository.flush();

        seedService.seedData(user, today);

        // Stored bytes last: if anything above failed the rows rolled back and the files must still exist.
        // A leftover file is only wasted space, so a failure here is logged, not fatal.
        for (String key : storedKeys) {
            try {
                storageService.delete(key);
            } catch (RuntimeException e) {
                log.warn("Demo reset could not delete stored file {}: {}", key, e.getMessage());
            }
        }
        log.info("Demo account reset to seed state.");
    }
}
