package com.alphatracker.api.account;

// Result of reassigning previously-unassigned trades onto the primary account.
public record BackfillResult(int tradesBackfilled, AccountResponse account) {
}
