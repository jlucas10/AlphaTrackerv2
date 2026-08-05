package com.alphatracker.api.trade;

// The single source of truth for futures contract economics.
//
// The trader never types a dollar multiplier or a commission — they pick the
// contract they actually traded and the server derives the money from it.
// A 10-point move on MNQ is $20; the same move on NQ is $200. That difference
// belongs here, not in the UI and not in the trader's head.
//
// pointValue    : dollars of P/L per 1.00 of price movement, per contract (CME contract spec)
// roundTurnFee  : all-in commission for one entry + one exit, per contract
//
// The point values are fixed by the exchange. The fees are prop-firm defaults
// (Tradovate/Apex-style rates) — tune these to your own firm's published
// schedule and every historical calculation stays consistent.
public enum Instrument {

    // ---- Equity index ----
    ES(50.0, 4.28),    // E-mini S&P 500
    MES(5.0, 1.34),    // Micro E-mini S&P 500
    NQ(20.0, 4.28),    // E-mini Nasdaq-100
    MNQ(2.0, 1.34),    // Micro E-mini Nasdaq-100
    YM(5.0, 4.28),     // E-mini Dow
    MYM(0.5, 1.34),    // Micro E-mini Dow
    RTY(50.0, 4.28),   // E-mini Russell 2000
    M2K(5.0, 1.34),    // Micro E-mini Russell 2000

    // ---- Energy / metals ----
    CL(1000.0, 4.28),  // Crude Oil
    MCL(100.0, 1.34),  // Micro Crude Oil
    GC(100.0, 4.28),   // Gold
    MGC(10.0, 1.34);   // Micro Gold

    private final double pointValue;
    private final double roundTurnFee;

    Instrument(double pointValue, double roundTurnFee) {
        this.pointValue = pointValue;
        this.roundTurnFee = roundTurnFee;
    }

    public double getPointValue() {
        return pointValue;
    }

    public double getRoundTurnFee() {
        return roundTurnFee;
    }

    // Resolves a user-typed ticker ("mnq", " NQ ") to a known contract.
    // Deliberately strict: an unrecognised ticker is rejected rather than
    // defaulting to a 1.0 multiplier, because a silently wrong multiplier
    // corrupts the P/L on the calendar and equity curve forever.
    public static Instrument fromTicker(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("Ticker is required.");
        }
        String normalized = ticker.trim().toUpperCase();
        for (Instrument instrument : values()) {
            if (instrument.name().equals(normalized)) {
                return instrument;
            }
        }
        throw new IllegalArgumentException(
                "Unsupported ticker '" + ticker.trim() + "'. Supported contracts: " + supportedTickers());
    }

    public static String supportedTickers() {
        StringBuilder sb = new StringBuilder();
        for (Instrument instrument : values()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(instrument.name());
        }
        return sb.toString();
    }
}
