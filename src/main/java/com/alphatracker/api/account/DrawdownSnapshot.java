package com.alphatracker.api.account;

// Result of replaying an account's trade history under its drawdownMode.
// See AccountService.computeDrawdownSnapshot for the rules.
public record DrawdownSnapshot(Double highWaterMark, Double drawdownFloor) {
}
