package com.alphatracker.api.analytics;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import com.alphatracker.api.trade.Trade;

// Every analytics number comes from this class, and it is the only place the
// win/loss rule lives (see CONTEXT.md, "Win / Loss Definition"):
//
//   win     = profitLoss >  0
//   loss    = profitLoss <  0
//   neutral = profitLoss == 0   (back at the start point - NOT a win, NOT a loss)
//
// winRate = wins / (wins + losses): neutral results are left out of the
// denominator. They are never hidden, though - neutralCount / neutralRate are
// always reported, because lots of neutral trades or days is itself a
// warning sign (profit given up, or a system that isn't working).
//
// Deliberately a pure, static, Spring-free calculator over already-loaded
// trades. That makes the definition unit-testable in milliseconds and keeps
// one code path for summary, breakdowns, days and discipline, instead of
// re-deriving "what is a win" inside several SQL queries. profitLoss is stored
// rounded to cents, so the == 0 comparison for a single trade is exact;
// nothing here recomputes money.
public final class TradeStats {

    public static final String UNTAGGED = "(untagged)";
    public static final String UNRATED = "unrated";

    private TradeStats() {
    }

    public static StatSummary summarize(List<Trade> trades) {
        int wins = 0;
        int losses = 0;
        int neutral = 0;
        double winSum = 0;
        double lossSum = 0;
        for (Trade t : trades) {
            double pnl = t.getProfitLoss();
            if (pnl > 0) {
                wins++;
                winSum += pnl;
            } else if (pnl < 0) {
                losses++;
                lossSum += pnl;
            } else {
                neutral++;
            }
        }
        int count = wins + losses + neutral;
        int decided = wins + losses;
        return new StatSummary(
                count,
                wins,
                losses,
                neutral,
                decided == 0 ? null : roundTo((double) wins / decided, 4),
                count == 0 ? null : roundTo((double) neutral / count, 4),
                round2(winSum + lossSum),
                wins == 0 ? null : round2(winSum / wins),
                losses == 0 ? null : round2(lossSum / losses));
    }

    // Nets each calendar day's trades, then classifies the day. The net is
    // summed in whole cents (long) rather than doubles so that offsetting
    // trades (+0.10, +0.20, -0.30) land on exactly 0 instead of 5.5e-17 and
    // get misclassified as a win day.
    public static DayStats summarizeDays(List<Trade> trades) {
        Map<LocalDate, Long> centsByDay = new TreeMap<>();
        for (Trade t : trades) {
            centsByDay.merge(t.getTradeDate().toLocalDate(), Math.round(t.getProfitLoss() * 100.0), Long::sum);
        }
        int winDays = 0;
        int lossDays = 0;
        int neutralDays = 0;
        for (long cents : centsByDay.values()) {
            if (cents > 0) {
                winDays++;
            } else if (cents < 0) {
                lossDays++;
            } else {
                neutralDays++;
            }
        }
        int days = centsByDay.size();
        return new DayStats(days, winDays, lossDays, neutralDays,
                days == 0 ? null : roundTo((double) neutralDays / days, 4));
    }

    public static DisciplineStats discipline(List<Trade> trades) {
        List<Trade> followed = new ArrayList<>();
        List<Trade> broke = new ArrayList<>();
        List<Trade> unspecified = new ArrayList<>();
        for (Trade t : trades) {
            if (t.getFollowedPlan() == null) {
                unspecified.add(t);
            } else if (t.getFollowedPlan()) {
                followed.add(t);
            } else {
                broke.add(t);
            }
        }
        int flagged = followed.size() + broke.size();
        return new DisciplineStats(
                flagged == 0 ? null : roundTo((double) followed.size() / flagged, 4),
                summarize(followed),
                summarize(broke),
                summarize(unspecified));
    }

    public static List<StatGroup> byInstrument(List<Trade> trades) {
        return groupBy(trades, t -> List.of(t.getTicker()));
    }

    // A trade with several tags lands in each tag's bucket, so these groups
    // intentionally do NOT sum to the overall trade count. Tags repeated on
    // one trade count once. No tags -> "(untagged)".
    public static List<StatGroup> bySetup(List<Trade> trades) {
        return groupBy(trades, t -> {
            if (t.getSetupTags() == null || t.getSetupTags().isEmpty()) {
                return List.of(UNTAGGED);
            }
            return new LinkedHashSet<>(t.getSetupTags());
        });
    }

    public static List<StatGroup> byRating(List<Trade> trades) {
        return groupBy(trades, t -> List.of(
                t.getExecutionRating() == null ? UNRATED : String.valueOf(t.getExecutionRating())));
    }

    // Biggest buckets first (the interesting ones for a "which setup do I
    // trade most" question), alphabetical on ties so output is deterministic.
    static List<StatGroup> groupBy(List<Trade> trades, Function<Trade, Collection<String>> keys) {
        Map<String, List<Trade>> buckets = new LinkedHashMap<>();
        for (Trade t : trades) {
            for (String key : keys.apply(t)) {
                buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
            }
        }
        return buckets.entrySet().stream()
                .map(e -> new StatGroup(e.getKey(), summarize(e.getValue())))
                .sorted(Comparator
                        .comparingInt((StatGroup g) -> g.stats().tradeCount()).reversed()
                        .thenComparing(StatGroup::key))
                .toList();
    }

    private static double round2(double value) {
        return roundTo(value, 2);
    }

    private static double roundTo(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
}
