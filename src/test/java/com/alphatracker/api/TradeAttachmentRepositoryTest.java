package com.alphatracker.api;

import com.alphatracker.api.trade.AttachmentType;
import com.alphatracker.api.trade.Trade;
import com.alphatracker.api.trade.TradeAttachment;
import com.alphatracker.api.trade.TradeAttachmentRepository;
import com.alphatracker.api.user.Role;
import com.alphatracker.api.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

// Runs against the real Postgres datasource from application.yml (no H2 on the
// classpath), same as AlphaTrackerApplicationTests - @DataJpaTest wraps every
// test in a transaction it rolls back afterward, so this never leaves rows
// behind in the dev database.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class TradeAttachmentRepositoryTest {

    @Autowired
    private TradeAttachmentRepository attachmentRepository;

    @Autowired
    private EntityManager entityManager;

    private User owner;
    private User otherUser;
    private Trade ownerTrade;

    @BeforeEach
    void setUp() {
        owner = persistUser("owner@alphatracker.com");
        otherUser = persistUser("intruder@alphatracker.com");
        ownerTrade = persistTrade(owner);
    }

    @Test
    @DisplayName("findByIdAndTrade_User_Id returns the attachment only for its actual owner")
    void findByIdAndOwnerRespectsOwnership() {
        TradeAttachment saved = attachmentRepository.save(newAttachment(ownerTrade, "1/a.png"));
        entityManager.flush();

        Optional<TradeAttachment> asOwner = attachmentRepository.findByIdAndTrade_User_Id(saved.getId(), owner.getId());
        Optional<TradeAttachment> asIntruder = attachmentRepository.findByIdAndTrade_User_Id(saved.getId(), otherUser.getId());

        assertTrue(asOwner.isPresent());
        assertTrue(asIntruder.isEmpty());
    }

    @Test
    @DisplayName("findAllByTrade_IdAndTrade_User_IdOrderByUploadedAtAsc lists only this trade's attachments, oldest first")
    void findAllForTradeReturnsChronologicalOrder() {
        TradeAttachment older = newAttachment(ownerTrade, "1/older.png");
        older.setUploadedAt(LocalDateTime.now().minusHours(2));
        TradeAttachment newer = newAttachment(ownerTrade, "1/newer.png");
        newer.setUploadedAt(LocalDateTime.now());
        attachmentRepository.save(newer);
        attachmentRepository.save(older);
        entityManager.flush();

        List<TradeAttachment> result = attachmentRepository
                .findAllByTrade_IdAndTrade_User_IdOrderByUploadedAtAsc(ownerTrade.getId(), owner.getId());

        assertEquals(2, result.size());
        assertEquals("1/older.png", result.get(0).getStorageKey());
        assertEquals("1/newer.png", result.get(1).getStorageKey());
    }

    @Test
    @DisplayName("deleteByIdAndTrade_User_Id deletes nothing and reports 0 when the caller isn't the owner")
    void deleteByWrongOwnerDeletesNothing() {
        TradeAttachment saved = attachmentRepository.save(newAttachment(ownerTrade, "1/a.png"));
        entityManager.flush();

        long deletedCount = attachmentRepository.deleteByIdAndTrade_User_Id(saved.getId(), otherUser.getId());

        assertEquals(0, deletedCount);
        assertTrue(attachmentRepository.findById(saved.getId()).isPresent());
    }

    @Test
    @DisplayName("deleteByIdAndTrade_User_Id removes the row and reports 1 for the real owner")
    void deleteByOwnerRemovesRow() {
        TradeAttachment saved = attachmentRepository.save(newAttachment(ownerTrade, "1/a.png"));
        entityManager.flush();

        long deletedCount = attachmentRepository.deleteByIdAndTrade_User_Id(saved.getId(), owner.getId());

        assertEquals(1, deletedCount);
        assertTrue(attachmentRepository.findById(saved.getId()).isEmpty());
    }

    private TradeAttachment newAttachment(Trade trade, String storageKey) {
        return TradeAttachment.builder()
                .trade(trade)
                .storageKey(storageKey)
                .attachmentType(AttachmentType.SCREENSHOT)
                .contentType("image/png")
                .sizeBytes(1024L)
                .uploadedAt(LocalDateTime.now())
                .build();
    }

    private User persistUser(String email) {
        User user = User.builder()
                .email(email)
                .password("hashed")
                .firstName("Test")
                .role(Role.USER)
                .build();
        entityManager.persist(user);
        return user;
    }

    private Trade persistTrade(User forUser) {
        Trade trade = Trade.builder()
                .ticker("MNQ")
                .direction("LONG")
                .entryPrice(100.0)
                .exitPrice(110.0)
                .contracts(1)
                .profitLoss(20.0)
                .tradeDate(LocalDateTime.now())
                .user(forUser)
                .build();
        entityManager.persist(trade);
        return trade;
    }
}
