package com.alphatracker.api.importer;

import java.time.LocalDate;
import java.util.List;

import com.alphatracker.api.account.AccountStatus;

// What POST /api/v1/imports/confirm returns: the summary the modal shows afterwards, plus where each account
// stands now. The account figures come from the SAME replay the dashboard uses, read after the write, so
// "balance and drawdown reflect the import" is something the response proves rather than assumes.
//
//   created   trades written, across all chosen accounts (a 20-row file into 2 accounts = 40)
//   skipped   (row, account) pairs left alone because that account already has the trade
//   failed    rows rejected by the parser; they appear in `errors` with their line and reason
public record ImportConfirmResponse(
        ImportSource source,
        int created,
        int skipped,
        int failed,
        List<AccountResult> accounts,
        List<ImportRowError> errors) {

    // suggestedStatus / breachedOn / targetReachedOn are the existing derived facts (never stored). A big
    // enough back-dated batch can tip an account to FAILED or PASSED; the trader still confirms that in the
    // existing status flow, the import does not change status by itself.
    public record AccountResult(
            Long id,
            String name,
            int created,
            int skipped,
            double currentBalance,
            double highWaterMark,
            double drawdownFloor,
            double profit,
            AccountStatus suggestedStatus,
            LocalDate breachedOn,
            LocalDate targetReachedOn) {
    }
}
