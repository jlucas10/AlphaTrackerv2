package com.alphatracker.api.trade;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TradeAttachmentRepository extends JpaRepository<TradeAttachment, Long> {

    // Ownership baked directly into the query (trade_id -> trade.user_id) so a
    // caller physically cannot fetch another trader's attachment by guessing an
    // id - there's no code path here that returns a row without also proving
    // it belongs to userId.
    Optional<TradeAttachment> findByIdAndTrade_User_Id(Long id, Long userId);

    List<TradeAttachment> findAllByTrade_IdAndTrade_User_IdOrderByUploadedAtAsc(Long tradeId, Long userId);

    // long (not void) so callers can tell whether a row actually existed - a
    // delete of an id that doesn't belong to this user should read as "not
    // found," not silently succeed.
    long deleteByIdAndTrade_User_Id(Long id, Long userId);
}
