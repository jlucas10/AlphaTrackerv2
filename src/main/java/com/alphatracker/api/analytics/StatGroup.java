package com.alphatracker.api.analytics;

// A labelled bucket in a breakdown (e.g. key "MNQ", or "FVG fill").
public record StatGroup(String key, StatSummary stats) {
}
