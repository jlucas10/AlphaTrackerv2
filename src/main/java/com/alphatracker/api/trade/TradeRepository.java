package com.alphatracker.api.trade;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {
    // Spring Data JPA will automatically analyze
    // this method name and generate the SQL query behind the scenes:
    // "SELECT * FROM trade WHERE user_id = ?"
    List<Trade> findByUserId(Long userId);

    // Ownership-checked single lookup, mirroring AccountRepository.findByIdAndUserId.
    // Used by TradeAttachmentService so an upload can't be attached to a trade
    // the requester doesn't own, without duplicating that check by hand.
    Optional<Trade> findByIdAndUserId(Long id, Long userId);

    List<Trade> findAllByUserIdAndAccountIdOrderByTradeDateDesc(Long userId, Long accountId);

    // Chronological order because the drawdown engine replays balance forward
    // in time to find the high-water mark.
    List<Trade> findAllByAccountIdOrderByTradeDateAsc(Long accountId);

    // "Unassigned" trades: logged before the trader had a primary account, or
    // logged without picking one. Used to preview and then execute a backfill
    // onto whichever account becomes primary.
    List<Trade> findAllByUserIdAndAccountIsNull(Long userId);

    long countByUserIdAndAccountIsNull(Long userId);
}
