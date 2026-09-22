package com.alphatracker.api.trade;

import java.time.LocalDateTime;
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
    Optional<Trade> findByIdAndUserId(Long id, Long userId);

    List<Trade> findAllByUserIdAndAccountIdOrderByTradeDateDesc(Long userId, Long accountId);

    // Backs the journal day bundle - GreaterThanEqual/LessThan (not Between,
    // which is inclusive on both ends) so a trade logged at exactly midnight
    // the next day lands in tomorrow's results only, never double-counted
    // into today's too.
    List<Trade> findAllByUserIdAndTradeDateGreaterThanEqualAndTradeDateLessThanOrderByTradeDateAsc(
            Long userId, LocalDateTime start, LocalDateTime startOfNextDay);

    // Chronological order because the drawdown engine replays balance forward
    // in time to find the high-water mark.
    List<Trade> findAllByAccountIdOrderByTradeDateAsc(Long accountId);

    // "Unassigned" trades: logged before the trader had a primary account, or
    // logged without picking one. Used to preview and then execute a backfill
    // onto whichever account becomes primary.
    List<Trade> findAllByUserIdAndAccountIsNull(Long userId);

    long countByUserIdAndAccountIsNull(Long userId);
}
