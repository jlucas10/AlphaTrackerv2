package com.alphatracker.api.trade;

import java.util.List;
import org.springframework.stereotype.Service;
import com.alphatracker.api.user.User;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TradeService {

    // Tradeservice handles business logic for trade
    // Log trade, get trade for user, get trade by id, and delete trade (keeping it
    // simple for now)
    private final TradeRepository tradeRepository;

    // Saves a new trade entry to the database, explicitly linking it to the
    // authenticated user.
    public Trade logTrade(Trade trade, User authenticatedUser) {
        trade.setUser(authenticatedUser); // Force the relationship boundary
        return tradeRepository.save(trade);
    }

    // Extracts all trades belonging exclusively to the logged-in user's ID.
    public List<Trade> getTradesForUser(User authenticatedUser) {
        return tradeRepository.findByUserId(authenticatedUser.getId());
    }

    // Fetches a single trade but verifies the requester actually owns it before
    // handing it over.
    public Trade getTradeById(Long tradeId, User authenticatedUser) {
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new IllegalArgumentException("Trade execution not found with ID: " + tradeId));

        // Data Boundary Check: Is another user trying to peek at this trade?
        if (!trade.getUser().getId().equals(authenticatedUser.getId())) {
            throw new SecurityException("Unauthorized access: You do not own this trade entry.");
        }

        return trade;
    }

    // Deletes a trade entry after validating ownership boundaries.
    public void deleteTrade(Long tradeId, User authenticatedUser) {
        Trade trade = getTradeById(tradeId, authenticatedUser); // Reuses our validation logic above
        tradeRepository.delete(trade);
    }
}