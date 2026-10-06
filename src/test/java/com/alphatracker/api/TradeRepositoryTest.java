package com.alphatracker.api;

import com.alphatracker.api.account.Account;
import com.alphatracker.api.account.AccountType;
import com.alphatracker.api.trade.Trade;
import com.alphatracker.api.trade.TradeRepository;
import com.alphatracker.api.user.Role;
import com.alphatracker.api.user.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Runs against the real Postgres datasource, like the other repository tests;
// @DataJpaTest rolls back after each test so nothing persists.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class TradeRepositoryTest {

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 9, 18, 0, 0);
    private static final LocalDateTime TO_EXCLUSIVE = LocalDateTime.of(2026, 9, 19, 0, 0);

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private EntityManager entityManager;

    private User owner;
    private User otherUser;

    @BeforeEach
    void setUp() {
        owner = persistUser("trade-owner@alphatracker.com");
        otherUser = persistUser("trade-other@alphatracker.com");
    }

    @Test
    @DisplayName("analytics window is half-open: from 00:00 is in, the next midnight is out; setupTags load without lazy errors")
    void windowBoundariesAndTagFetch() {
        persistTrade(owner, null, FROM.minusSeconds(1), 1.0);                    // just before - out
        Trade atFrom = persistTrade(owner, null, FROM, 2.0, "FVG", "A+");        // exactly from - in
        persistTrade(owner, null, TO_EXCLUSIVE.minusSeconds(1), 3.0);            // last second - in
        persistTrade(owner, null, TO_EXCLUSIVE, 4.0);                            // exactly next midnight - out
        entityManager.flush();
        entityManager.clear(); // detach everything: an un-fetched lazy collection would now throw

        List<Trade> result = tradeRepository.findForAnalytics(owner.getId(), FROM, TO_EXCLUSIVE);

        assertEquals(2, result.size());
        assertEquals(atFrom.getId(), result.get(0).getId());          // chronological order
        assertEquals(3.0, result.get(1).getProfitLoss());
        assertEquals(Set.of("FVG", "A+"), new HashSet<>(result.get(0).getSetupTags()));
    }

    @Test
    @DisplayName("results are scoped to the user (never another trader's trades) and to the account when one is given")
    void scopesToUserAndAccount() {
        Account account = persistAccount(owner);
        persistTrade(owner, account, FROM.plusHours(1), 10.0);
        persistTrade(owner, null, FROM.plusHours(2), 20.0);        // unassigned
        persistTrade(otherUser, null, FROM.plusHours(3), 999.0);   // another user's trade
        entityManager.flush();
        entityManager.clear();

        List<Trade> all = tradeRepository.findForAnalytics(owner.getId(), FROM, TO_EXCLUSIVE);
        List<Trade> scoped = tradeRepository.findForAnalyticsByAccount(owner.getId(), account.getId(), FROM, TO_EXCLUSIVE);
        List<Trade> asOther = tradeRepository.findForAnalyticsByAccount(otherUser.getId(), account.getId(), FROM, TO_EXCLUSIVE);

        assertEquals(List.of(10.0, 20.0), all.stream().map(Trade::getProfitLoss).toList()); // incl. unassigned, no 999
        assertEquals(1, scoped.size());
        assertEquals(10.0, scoped.get(0).getProfitLoss());
        assertTrue(asOther.isEmpty()); // someone else's accountId matches nothing
    }

    private Trade persistTrade(User user, Account account, LocalDateTime when, double pnl, String... tags) {
        Trade trade = Trade.builder().ticker("MNQ").direction("LONG").entryPrice(100.0).exitPrice(101.0)
                .contracts(1).profitLoss(pnl).tradeDate(when).user(user).account(account)
                .setupTags(new ArrayList<>(List.of(tags))).build();
        entityManager.persist(trade);
        return trade;
    }

    private Account persistAccount(User user) {
        Account account = Account.builder().name("Apex 50k").firm("Apex").accountType(AccountType.EVALUATION)
                .startingBalance(50000.0).currentBalance(50000.0).maxDrawdown(2000.0).user(user).build();
        entityManager.persist(account);
        return account;
    }

    private User persistUser(String email) {
        User user = User.builder().email(email).password("hashed").firstName("Test").role(Role.USER).build();
        entityManager.persist(user);
        return user;
    }
}
