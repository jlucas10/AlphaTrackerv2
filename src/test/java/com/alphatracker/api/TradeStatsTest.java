package com.alphatracker.api;

import com.alphatracker.api.analytics.DayStats;
import com.alphatracker.api.analytics.DisciplineStats;
import com.alphatracker.api.analytics.StatGroup;
import com.alphatracker.api.analytics.StatSummary;
import com.alphatracker.api.analytics.TradeStats;
import com.alphatracker.api.trade.Trade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Pure unit tests - TradeStats has no Spring or DB dependency, which is the
// point: the win / loss / neutral definition is verified in isolation.
public class TradeStatsTest {

    private static final LocalDateTime DAY1 = LocalDateTime.of(2026, 9, 18, 9, 30);
    private static final LocalDateTime DAY2 = LocalDateTime.of(2026, 9, 19, 9, 30);

    private static long nextId = 1;

    private static Trade trade(double pnl, LocalDateTime when) {
        return Trade.builder().id(nextId++).ticker("MNQ").profitLoss(pnl).tradeDate(when)
                .setupTags(new ArrayList<>()).build();
    }

    private static Trade trade(double pnl) {
        return trade(pnl, DAY1);
    }

    private static Trade tagged(double pnl, String ticker, Integer rating, String... tags) {
        return Trade.builder().id(nextId++).ticker(ticker).profitLoss(pnl).tradeDate(DAY1)
                .executionRating(rating).setupTags(new ArrayList<>(List.of(tags))).build();
    }

    private static Trade planTrade(double pnl, Boolean followedPlan) {
        return Trade.builder().id(nextId++).ticker("MNQ").profitLoss(pnl).tradeDate(DAY1)
                .followedPlan(followedPlan).setupTags(new ArrayList<>()).build();
    }

    @Test
    @DisplayName("> 0 is a win, < 0 a loss, exactly 0 neutral - and neutral is not averaged into either side")
    void classifiesWinLossNeutral() {
        StatSummary s = TradeStats.summarize(List.of(trade(100.0), trade(0.0), trade(-50.0)));

        assertEquals(3, s.tradeCount());
        assertEquals(1, s.wins());
        assertEquals(1, s.losses());
        assertEquals(1, s.neutral());
        assertEquals(50.0, s.totalPnl());
        assertEquals(100.0, s.avgWin());
        assertEquals(-50.0, s.avgLoss());
    }

    @Test
    @DisplayName("win rate excludes neutral from its denominator; neutralRate is reported separately")
    void winRateExcludesNeutralButNeutralRateIsReported() {
        StatSummary s = TradeStats.summarize(List.of(trade(10.0), trade(10.0), trade(-10.0), trade(0.0)));

        assertEquals(0.6667, s.winRate());   // 2 wins / (2 wins + 1 loss)
        assertEquals(0.25, s.neutralRate()); // 1 neutral / 4 trades
    }

    @Test
    @DisplayName("no trades -> null rates and averages, never a fake 0% win rate")
    void emptyHasNullRates() {
        StatSummary s = TradeStats.summarize(List.of());

        assertEquals(0, s.tradeCount());
        assertNull(s.winRate());
        assertNull(s.neutralRate());
        assertNull(s.avgWin());
        assertNull(s.avgLoss());
    }

    @Test
    @DisplayName("a day's trades are netted, then classified as a win, loss or neutral day")
    void daysAreNettedThenClassified() {
        DayStats d = TradeStats.summarizeDays(List.of(
                trade(100.0, DAY1), trade(-40.0, DAY1),     // +60 -> win day
                trade(-100.0, DAY2), trade(40.0, DAY2)));   // -60 -> loss day

        assertEquals(2, d.tradingDays());
        assertEquals(1, d.winDays());
        assertEquals(1, d.lossDays());
        assertEquals(0, d.neutralDays());
    }

    @Test
    @DisplayName("offsets that net to 0 are a neutral day, even with float noise (0.1 + 0.2 - 0.3)")
    void centsMathMakesNeutralDay() {
        DayStats d = TradeStats.summarizeDays(List.of(
                trade(0.1), trade(0.2), trade(-0.3),   // doubles would give 5.5e-17 -> a bogus win day
                trade(50.0, DAY2)));

        assertEquals(1, d.neutralDays());
        assertEquals(1, d.winDays());
        assertEquals(0.5, d.neutralDayRate());
    }

    @Test
    @DisplayName("a trade at exactly midnight belongs to the new day, not netted into the old one")
    void midnightTradeBelongsToNextDay() {
        LocalDateTime midnight = LocalDateTime.of(2026, 9, 19, 0, 0);
        DayStats d = TradeStats.summarizeDays(List.of(
                trade(10.0, midnight.minusMinutes(1)), trade(-10.0, midnight)));

        assertEquals(2, d.tradingDays());
        assertEquals(0, d.neutralDays());
    }

    @Test
    @DisplayName("discipline splits by followedPlan; null flags are 'unspecified' and excluded from the rate")
    void splitsByFollowedPlan() {
        DisciplineStats d = TradeStats.discipline(List.of(
                planTrade(100.0, true), planTrade(60.0, true), planTrade(-20.0, true),
                planTrade(-200.0, false), planTrade(5.0, null)));

        assertEquals(0.75, d.planFollowedRate()); // 3 followed / (3 followed + 1 broke)
        assertEquals(140.0, d.followedPlan().totalPnl());
        assertEquals(-200.0, d.brokePlan().totalPnl());
        assertEquals(1, d.unspecified().tradeCount());
    }

    @Test
    @DisplayName("byInstrument groups per ticker, biggest bucket first, ties alphabetical")
    void byInstrumentGroupsAndSorts() {
        List<StatGroup> groups = TradeStats.byInstrument(List.of(
                tagged(10.0, "NQ", null), tagged(20.0, "MNQ", null), tagged(-5.0, "MNQ", null),
                tagged(1.0, "ES", null)));

        assertEquals(List.of("MNQ", "ES", "NQ"), groups.stream().map(StatGroup::key).toList());
        assertEquals(2, groups.get(0).stats().tradeCount());
    }

    @Test
    @DisplayName("a multi-tag trade counts in each tag's bucket (groups needn't sum to the total); repeats count once")
    void multiTagTradeCountsInEachBucket() {
        List<StatGroup> groups = TradeStats.bySetup(List.of(
                tagged(100.0, "MNQ", null, "FVG", "A+", "FVG"),
                tagged(-40.0, "MNQ", null, "FVG")));

        StatGroup fvg = groups.stream().filter(g -> g.key().equals("FVG")).findFirst().orElseThrow();
        StatGroup aPlus = groups.stream().filter(g -> g.key().equals("A+")).findFirst().orElseThrow();
        assertEquals(2, fvg.stats().tradeCount());
        assertEquals(1, aPlus.stats().tradeCount());
    }

    @Test
    @DisplayName("no tags -> (untagged); no rating -> unrated")
    void untaggedAndUnratedBuckets() {
        List<Trade> trades = List.of(tagged(10.0, "MNQ", null), tagged(-10.0, "MNQ", 4));

        assertEquals(TradeStats.UNTAGGED, TradeStats.bySetup(trades).get(0).key());
        List<String> ratingKeys = TradeStats.byRating(trades).stream().map(StatGroup::key).toList();
        assertTrue(ratingKeys.contains(TradeStats.UNRATED));
        assertTrue(ratingKeys.contains("4"));
    }
}
