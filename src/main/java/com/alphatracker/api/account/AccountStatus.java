package com.alphatracker.api.account;

// Where an account is in its life. An evaluation is ACTIVE until the trader marks it PASSED
// (they hit the profit target and their firm's other rules were met) or FAILED (the drawdown
// floor was breached). A funded account can only go ACTIVE -> FAILED.
//
// Also used as the replay's *suggestion* (AccountProgress.suggestedStatus): the app can see that
// a target was hit, but only the trader knows whether the firm's other conditions (minimum
// trading days, consistency rules) were met, so the suggestion is never applied automatically.
public enum AccountStatus {
    ACTIVE,
    PASSED,
    FAILED
}
