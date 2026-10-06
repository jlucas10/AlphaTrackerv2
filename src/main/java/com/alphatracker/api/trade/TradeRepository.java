package com.alphatracker.api.trade;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // Backs the analytics endpoints. Same half-open window as above - `>= from`
    // and `< toExclusive`, never Between - so a trade at exactly midnight the
    // next day is excluded here and included in tomorrow's results.
    //
    // Two queries instead of one with "(:accountId is null or ...)": binding a
    // null parameter in that pattern can fail on Postgres ("could not determine
    // data type of parameter"), and two explicit queries read more plainly
    // anyway. The bounds are required (non-null); the service substitutes wide
    // defaults when the caller gives no from/to.
    //
    // left join fetch on setupTags: it's a lazy @ElementCollection, and the
    // analytics code reads it for every trade, which would otherwise be one
    // extra query per trade (N+1). `distinct` collapses the row-per-tag
    // duplicates the join produces.
    @Query("""
            select distinct t from Trade t left join fetch t.setupTags
            where t.user.id = :userId
              and t.tradeDate >= :from and t.tradeDate < :toExclusive
            order by t.tradeDate asc, t.id asc
            """)
    List<Trade> findForAnalytics(@Param("userId") Long userId,
            @Param("from") LocalDateTime from, @Param("toExclusive") LocalDateTime toExclusive);

    // Same, restricted to one account. Filtering on user_id as well as
    // account_id means someone else's accountId simply matches nothing.
    @Query("""
            select distinct t from Trade t left join fetch t.setupTags
            where t.user.id = :userId and t.account.id = :accountId
              and t.tradeDate >= :from and t.tradeDate < :toExclusive
            order by t.tradeDate asc, t.id asc
            """)
    List<Trade> findForAnalyticsByAccount(@Param("userId") Long userId, @Param("accountId") Long accountId,
            @Param("from") LocalDateTime from, @Param("toExclusive") LocalDateTime toExclusive);

    // Chronological order because the drawdown engine replays balance forward
    // in time to find the high-water mark.
    List<Trade> findAllByAccountIdOrderByTradeDateAsc(Long accountId);

    // "Unassigned" trades: logged before the trader had a primary account, or
    // logged without picking one. Used to preview and then execute a backfill
    // onto whichever account becomes primary.
    List<Trade> findAllByUserIdAndAccountIsNull(Long userId);

    long countByUserIdAndAccountIsNull(Long userId);
}
