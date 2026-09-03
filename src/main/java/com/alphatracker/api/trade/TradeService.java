package com.alphatracker.api.trade;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alphatracker.api.account.Account;
import com.alphatracker.api.account.AccountRepository;
import com.alphatracker.api.user.User;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TradeService {

    // Tradeservice handles business logic for trade
    // Log trade, get trade for user, get trade by id, and delete trade (keeping it
    // simple for now)
    private final TradeRepository tradeRepository;
    private final AccountRepository accountRepository;

    // Builds a trade from the trader's raw inputs and saves it against the
    // authenticated user.
    //
    // The trader supplies only what they observed: contract, direction, entry,
    // exit, size. Everything monetary is derived here so that the dollar value
    // of a point lives in exactly one place (Instrument) instead of being
    // retyped per trade or duplicated in the frontend.
    public Trade logTrade(TradeRequest request, User authenticatedUser) {
        Instrument instrument = Instrument.fromTicker(request.getTicker());
        String direction = normalizeDirection(request.getDirection());

        double entryPrice = requirePrice(request.getEntryPrice(), "Entry price");
        double exitPrice = requirePrice(request.getExitPrice(), "Exit price");
        int contracts = requireContracts(request.getContracts());

        // A short profits when price falls, so the sign of the move flips.
        double priceMove = "LONG".equals(direction)
                ? exitPrice - entryPrice
                : entryPrice - exitPrice;

        double gross = priceMove * instrument.getPointValue() * contracts;
        double commission = instrument.getRoundTurnFee() * contracts;
        double netProfitLoss = round(gross - commission);

        // Sprint 2: Link to Account if accountId is passed
        Account targetAccount = null;
        if (request.getAccountId() != null) {
            targetAccount = accountRepository.findByIdAndUserId(request.getAccountId(), authenticatedUser.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Account not found or does not belong to user."));

            // Sync account live balance
            targetAccount.setCurrentBalance(round(targetAccount.getCurrentBalance() + netProfitLoss));
            accountRepository.save(targetAccount);
        }
        Trade trade = Trade.builder()
                .ticker(instrument.name())
                .direction(direction)
                .entryPrice(entryPrice)
                .exitPrice(exitPrice)
                .contracts(contracts)
                .commission(round(commission))
                .profitLoss(netProfitLoss) // net is what hits the account
                .followedPlan(request.getFollowedPlan() == null || request.getFollowedPlan())
                .notes(request.getNotes())
                .tradeDate(request.getTradeDate() == null ? LocalDateTime.now() : request.getTradeDate())
                .user(authenticatedUser) // Force the relationship boundary
                .account(targetAccount) // Links trade to account
                .build();

        return tradeRepository.save(trade);
    }

    private String normalizeDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            throw new IllegalArgumentException("Direction is required.");
        }
        String normalized = direction.trim().toUpperCase();
        if (!normalized.equals("LONG") && !normalized.equals("SHORT")) {
            throw new IllegalArgumentException("Direction must be LONG or SHORT, got '" + direction.trim() + "'.");
        }
        return normalized;
    }

    private double requirePrice(Double price, String fieldName) {
        if (price == null) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        if (price <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero.");
        }
        return price;
    }

    private int requireContracts(Integer contracts) {
        if (contracts == null) {
            throw new IllegalArgumentException("Contract count is required.");
        }
        if (contracts < 1) {
            throw new IllegalArgumentException("Contract count must be at least 1.");
        }
        return contracts;
    }

    // Keeps stored dollars clean; raw double math yields values like 39.99999999.
    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // Supports fetching all trades for user
    @Transactional(readOnly = true)
    public List<Trade> getTradesForUser(User authenticatedUser, Long accountId) {
        if (accountId != null) {
            return tradeRepository.findAllByUserIdAndAccountIdOrderByTradeDateDesc(authenticatedUser.getId(),
                    accountId);
        }
        return tradeRepository.findByUserId(authenticatedUser.getId());
    }

    // Fetches a single trade but verifies the requester actually owns it before
    // handing it over.
    @Transactional(readOnly = true)
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
        Trade trade = getTradeById(tradeId, authenticatedUser);

        if (trade.getAccount() != null) {
            Account account = trade.getAccount();
            account.setCurrentBalance(round(account.getCurrentBalance() - trade.getProfitLoss()));
            accountRepository.save(account);
        }

        tradeRepository.delete(trade);
    }
}