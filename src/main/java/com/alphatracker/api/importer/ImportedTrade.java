package com.alphatracker.api.importer;

import java.time.LocalDateTime;

import com.alphatracker.api.trade.Instrument;

// One completed trade, normalized away from any broker's format. This is all a source adapter produces; the
// money (P&L) is NOT decided here. It is derived later by the same TradeService path as a hand-logged trade.
//
//   line         row number in the uploaded file (header is line 1), so messages can point at the row
//   externalId   the broker's own trade id; with the source it identifies the trade for duplicate detection
//   direction    "LONG" or "SHORT", the same values TradeRequest uses
//   entryTime    in ImportRules.JOURNAL_ZONE. The trade's date in the journal is the date of THIS timestamp
//   exitTime     same zone; kept for display and ordering, it never moves the trade to another day
//   sourcePnl    what the broker reported (null if the file has no such column). Not stored as the trade's P&L;
//                it is only compared with our derived number so a mismatch can be shown as a warning
public record ImportedTrade(
        int line,
        String externalId,
        Instrument instrument,
        String direction,
        double entryPrice,
        double exitPrice,
        int contracts,
        LocalDateTime entryTime,
        LocalDateTime exitTime,
        Double sourcePnl) {
}
