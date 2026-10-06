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

    private static Trade trade(double pnl) {
        return trade(pnl, DAY1);
    }

    private static Trade trade(double pnl, LocalDateTime when) {
        return Trade.builder().id(nextId++).ticker("MNQ").profitLoss(pnl).tradeDate(when)
                .setupTags(new ArrayList<>()).build();
    }

    private static Trade trade(double pnl, String ticker, Integer rating, String... tags) {
        return Trade.builder().id(nextId++).ticker(ticker).profitLoss(pnl).tradeDate(DAY1)
                .executionRating(rating).setupTags(new ArrayList<>(List.of(tags))).build();
    }

    private static Trade planTrade(double pnl, Boolean followedPlan) {
        return Trade.builder().id(nextId++).ticker("MNQ").profitLoss(pnl).tradeDate(DAY1)
                .followedPlan(followedPlan).setupTags(new ArrayList<>()).build();
    }

    // ---- summarize: win / loss / neutral ----

    @Test
    @DisplayName("> 0 is a win, < 0 a loss, exactly 0 is neutral - not a win and not a loss")
    void classifiesWinLossNeutral() {
        StatSummary s = TradeStats.summarize(List.of(trade(100.0), trade(0.0), trade(-50.0)));

        assertEquals(3, s.tradeCount());
        assertEquals(1, s.wins());
        assertEquals(1, s.losses());
        assertEquals(1, s.neutral());
        assertEquals(50.0, s.totalPnl());
        assertEquals(100.0, s.avgWin());   // the neutral trade is not averaged into wins
        assertEquals(-50.0, s.avgLoss());  // ...or into losses
    }

    @Test
    @DisplayName("win rate excludes neutral trades from its denominator; neutralRate is reported separately")
    void winRateExcludesNeutralButNeutralRateIsReported() {
        StatSummary s = TradeStats.summarize(List.of(
                trade(10.0), trade(10.0), trade(-10.0), trade(0.0)));

        assertEquals(0.6667, s.winRate());     // 2 wins / (2 wins + 1 loss)
        assertEquals(0.25, s.neutralRate());   // 1 neutral / 4 trades
        assertEquals(4, s.tradeCount());
    }

    @Test
    @DisplayName("only neutral trades -> win rate is null (nothing decided) but neutralRate is 1.0")
    void onlyNeutralTrades() {
        StatSummary s = TradeStats.summarize(List.of(trade(0.0), trade(0.0)));

        assertNull(s.winRate());
        assertEquals(1.0, s.neutralRate());
        assertNull(s.avgWin());
        assertNull(s.avgLoss());
    }

    @Test
    @DisplayName("no trades -> zero counts and null rates/averages, not a fake 0% win rate")
    void emptyHasNullRates() {
        StatSummary s = TradeStats.summarize(List.of());

        assertEquals(0, s.tradeCount());
        assertEquals(0.0, s.totalPnl());
        assertNull(s.winRate());
        assertNull(s.neutralRate());
        assertNull(s.avgWin());
        assertNull(s.avgLoss());
    }

    @Test
    @DisplayName("all wins -> avgLoss is null; all losses -> avgWin is null")
    void oneSidedHasNullOtherAverage() {
        StatSummary wins = TradeStats.summarize(List.of(trade(10.0), trade(30.0)));
        assertEquals(1.0, wins.winRate());
        assertNull(wins.avgLoss());

        StatSummary losses = TradeStats.summarize(List.of(trade(-10.0), trade(-30.0)));
        assertEquals(0.0, losses.winRate());
        assertNull(losses.avgWin());
        assertEquals(-20.0, losses.avgLoss());
    }

    @Test
    @DisplayName("sums are rounded to cents so float noise never reaches the API")
    void roundsToCents() {
        StatSummary s = TradeStats.summarize(List.of(trade(0.1), trade(0.2), trade(0.3)));

        assertEquals(0.6, s.totalPnl()); // raw double addition gives 0.6000000000000001
    }

    // ---- summarizeDays ----

    @Test
    @DisplayName("a day's trades are netted: +100 and -40 is a win day, -100 and +40 a loss day")
    void daysAreNettedThenClassified() {
        DayStats d = TradeStats.summarizeDays(List.of(
                trade(100.0, DAY1), trade(-40.0, DAY1),
                trade(-100.0, DAY2), trade(40.0, DAY2)));

        assertEquals(2, d.tradingDays());
        assertEquals(1, d.winDays());
        assertEquals(1, d.lossDays());
        assertEquals(0, d.neutralDays());
    }

    @Test
    @DisplayName("offsetting trades that net to exactly 0 make a neutral day, not a win day")
    void offsettingTradesMakeNeutralDay() {
        DayStats d = TradeStats.summarizeDays(List.of(trade(150.0, DAY1), trade(-150.0, DAY1)));

        assertEquals(1, d.tradingDays());
        assertEquals(1, d.neutralDays());
        assertEquals(0, d.winDays());
        assertEquals(1.0, d.neutralDayRate());
    }

    @Test
    @DisplayName("float-noisy offsets (0.1 + 0.2 - 0.3) still net to a neutral day")
    void centsMathAvoidsFloatNoise() {
        DayStats d = TradeStats.summarizeDays(List.of(trade(0.1), trade(0.2), trade(-0.3)));

        assertEquals(1, d.neutralDays()); // doubles would give 5.5e-17 > 0 -> a bogus win day
        assertEquals(0, d.winDays());
    }

    @Test
    @DisplayName("neutralDayRate = neutral days / trading days; empty input has no rate")
    void neutralDayRate() {
        DayStats d = TradeStats.summarizeDays(List.of(
                trade(0.0, DAY1), trade(50.0, DAY2)));

        assertEquals(0.5, d.neutralDayRate());
        assertNull(TradeStats.summarizeDays(List.of()).neutralDayRate());
        assertEquals(0, TradeStats.summarizeDays(List.of()).tradingDays());
    }

    @Test
    @DisplayName("a trade at exactly midnight belongs to the new day")
    void midnightTradeBelongsToNextDay() {
        LocalDateTime midnight = LocalDateTime.of(2026, 9, 19, 0, 0);
        DayStats d = TradeStats.summarizeDays(List.of(
                trade(10.0, midnight.minusMinutes(1)), trade(-10.0, midnight)));

        assertEquals(2, d.tradingDays()); // not netted together into one neutral day
        assertEquals(1, d.winDays());
        assertEquals(1, d.lossDays());
    }

    // ---- discipline ----

    @Test
    @DisplayName("discipline splits trades by followedPlan with separate stats and a followed rate")
    void splitsByFollowedPlan() {
        DisciplineStats d = TradeStats.discipline(List.of(
                planTrade(100.0, true), planTrade(60.0, true), planTrade(-20.0, true),
                planTrade(-200.0, false)));

        assertEquals(0.75, d.planFollowedRate());
        assertEquals(3, d.followedPlan().tradeCount());
        assertEquals(140.0, d.followedPlan().totalPnl());
        assertEquals(1, d.brokePlan().tradeCount());
        assertEquals(-200.0, d.brokePlan().totalPnl());
        assertEquals(0, d.unspecified().tradeCount());
    }

    @Test
    @DisplayName("null followedPlan goes to 'unspecified' and is excluded from the followed rate")
    void nullFlagIsUnspecified() {
        DisciplineStats d = TradeStats.discipline(List.of(
                planTrade(10.0, true), planTrade(-10.0, null)));

        assertEquals(1.0, d.planFollowedRate()); // 1 followed / (1 followed + 0 broke)
        assertEquals(1, d.unspecified().tradeCount());
    }

    @Test
    @DisplayName("no flagged trades -> null followed rate")
    void noFlaggedTrades() {
        assertNull(TradeStats.discipline(List.of()).planFollowedRate());
        assertNull(TradeStats.discipline(List.of(planTrade(5.0, null))).planFollowedRate());
    }

    // ---- breakdowns ----

    @Test
    @DisplayName("byInstrument groups per ticker, biggest bucket first")
    void byInstrumentGroupsAndSorts() {
        List<StatGroup> groups = TradeStats.byInstrument(List.of(
                trade(10.0, "NQ", null), trade(20.0, "MNQ", null), trade(-5.0, "MNQ", null)));

        assertEquals("MNQ", groups.get(0).key());
        assertEquals(2, groups.get(0).stats().tradeCount());
        assertEquals("NQ", groups.get(1).key());
    }

    @Test
    @DisplayName("equal-sized groups are ordered alphabetically so output is deterministic")
    void tiesSortByKey() {
        List<StatGroup> groups = TradeStats.byInstrument(List.of(
                trade(1.0, "NQ", null), trade(1.0, "ES", null)));

        assertEquals(List.of("ES", "NQ"), groups.stream().map(StatGroup::key).toList());
    }

    @Test
    @DisplayName("a trade with two tags counts in both buckets; groups need not sum to the total")
    void multiTagTradeCountsInEachBucket() {
        List<StatGroup> groups = TradeStats.bySetup(List.of(
                trade(100.0, "MNQ", null, "FVG", "A+"),
                trade(-40.0, "MNQ", null, "FVG")));

        StatGroup fvg = groups.stream().filter(g -> g.key().equals("FVG")).findFirst().orElseThrow();
        StatGroup aPlus = groups.stream().filter(g -> g.key().equals("A+")).findFirst().orElseThrow();
        assertEquals(2, fvg.stats().tradeCount());
        assertEquals(1, aPlus.stats().tradeCount());
    }

    @Test
    @DisplayName("a tag repeated on one trade is counted once")
    void duplicateTagCountedOnce() {
        List<StatGroup> groups = TradeStats.bySetup(List.of(trade(10.0, "MNQ", null, "FVG", "FVG")));

        assertEquals(1, groups.get(0).stats().tradeCount());
    }

    @Test
    @DisplayName("trades with no tags fall into (untagged), with no rating into unrated")
    void untaggedAndUnratedBuckets() {
        List<Trade> trades = List.of(trade(10.0, "MNQ", null), trade(-10.0, "MNQ", 4));

        assertEquals(TradeStats.UNTAGGED, TradeStats.bySetup(trades).get(0).key());
        List<String> ratingKeys = TradeStats.byRating(trades).stream().map(StatGroup::key).toList();
        assertTrue(ratingKeys.contains(TradeStats.UNRATED));
        assertTrue(ratingKeys.contains("4"));
    }

    @Test
    @DisplayName("breakdown buckets carry neutral counts too, so a setup that scratches a lot is visible")
    void breakdownBucketsReportNeutral() {
        List<StatGroup> groups = TradeStats.bySetup(List.of(
                trade(0.0, "MNQ", null, "FVG"), trade(0.0, "MNQ", null, "FVG"), trade(30.0, "MNQ", null, "FVG")));

        StatSummary fvg = groups.get(0).stats();
        assertEquals(2, fvg.neutral());
        assertEquals(0.6667, fvg.neutralRate());
    }
}
