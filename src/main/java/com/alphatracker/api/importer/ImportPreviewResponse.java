package com.alphatracker.api.importer;

import java.time.LocalDateTime;
import java.util.List;

// What POST /api/v1/imports/preview returns: everything the modal needs to show, and nothing is saved.
//
// Rows are in file order and include the rejected ones (status ERROR), so the trader sees exactly which
// line failed and why. For a row that parsed, `accounts` says per chosen account whether the trade is NEW
// there or a DUPLICATE (already imported). A row's overall status is DUPLICATE only if it is a duplicate in
// EVERY chosen account; otherwise it is NEW (something will be created).
public record ImportPreviewResponse(
        ImportSource source,
        List<AccountInfo> accounts,
        Summary summary,
        List<Row> rows) {

    public enum RowStatus { NEW, DUPLICATE, ERROR }

    public enum AccountCheckStatus { NEW, DUPLICATE }

    // `warning` is set when the account is already PASSED/FAILED: the import still saves (the same rule as
    // logging by hand), the trader is just told first.
    public record AccountInfo(Long id, String name, String status, String warning) {
    }

    // ready = rows with something to create; tradesToCreate = (row, account) pairs, so a 20-row file into
    // 2 accounts with no duplicates is ready 20, tradesToCreate 40.
    public record Summary(int total, int ready, int duplicates, int errors, int tradesToCreate) {
    }

    public record Row(
            int line,
            String externalId,
            RowStatus status,
            TradePreview trade,
            List<AccountCheck> accounts,
            List<String> warnings,
            List<String> errors) {
    }

    // The trade as it WOULD be saved. profitLoss is derived by TradeService.derivePnl (the same code as a
    // hand-logged trade); sourcePnl is what the file claimed, shown only so a difference is visible.
    public record TradePreview(
            String ticker,
            String direction,
            double entryPrice,
            double exitPrice,
            int contracts,
            LocalDateTime tradeDate,
            LocalDateTime exitTime,
            double profitLoss,
            Double sourcePnl) {
    }

    public record AccountCheck(Long accountId, AccountCheckStatus status) {
    }
}
