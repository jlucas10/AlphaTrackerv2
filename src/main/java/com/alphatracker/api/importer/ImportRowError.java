package com.alphatracker.api.importer;

// A row that cannot become a trade, with a message written for the trader, not for a developer.
// externalId is null when the id itself was the problem.
public record ImportRowError(int line, String externalId, String message) {
}
