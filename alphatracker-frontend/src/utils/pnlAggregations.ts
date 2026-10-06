import { format, parseISO, compareAsc } from 'date-fns';
import type { Trade } from '../types/Trade';
import { classifyPnl, sumPnl } from './tradeOutcome';

export interface EquityPoint {
  date: string;
  cumulativePnl: number;
}

const DAY_KEY_FORMAT = 'yyyy-MM-dd';

export function groupTradesByDay(trades: Trade[]): Map<string, number> {
  const dayPnls = new Map<string, number[]>();
  for (const trade of trades) {
    const key = format(parseISO(trade.tradeDate), DAY_KEY_FORMAT);
    const existing = dayPnls.get(key);
    if (existing) {
      existing.push(trade.profitLoss);
    } else {
      dayPnls.set(key, [trade.profitLoss]);
    }
  }
  // Summed in whole cents (sumPnl) so offsetting trades land on exactly 0 - a
  // neutral day - instead of float noise that would colour it as a win day.
  const dayTotals = new Map<string, number>();
  for (const [key, pnls] of dayPnls) {
    dayTotals.set(key, sumPnl(pnls));
  }
  return dayTotals;
}

export function getPnlForDay(dayTotals: Map<string, number>, day: Date): number {
  return dayTotals.get(format(day, DAY_KEY_FORMAT)) ?? 0;
}

// Companion to groupTradesByDay. That function sums each day down to a single
// number, which is all the calendar colouring needs but discards the trades
// themselves — so a day cell had no way to show what produced its total.
// This keeps the underlying executions, keyed identically so both maps agree.
export function groupTradeListsByDay(trades: Trade[]): Map<string, Trade[]> {
  const dayTrades = new Map<string, Trade[]>();
  for (const trade of trades) {
    const key = format(parseISO(trade.tradeDate), DAY_KEY_FORMAT);
    const existing = dayTrades.get(key);
    if (existing) {
      existing.push(trade);
    } else {
      dayTrades.set(key, [trade]);
    }
  }
  return dayTrades;
}

export function getTradesForDay(dayTrades: Map<string, Trade[]>, day: Date): Trade[] {
  return dayTrades.get(format(day, DAY_KEY_FORMAT)) ?? [];
}

export function getMonthlyTotal(dayTotals: Map<string, number>, monthDate: Date): number {
  const monthPrefix = format(monthDate, 'yyyy-MM');
  const monthPnls: number[] = [];
  for (const [key, pnl] of dayTotals) {
    if (key.startsWith(monthPrefix)) {
      monthPnls.push(pnl);
    }
  }
  return sumPnl(monthPnls);
}

export interface WinRateStats {
  // 0-100, rounded. wins / (wins + losses): neutral trades are NOT in the
  // denominator (see tradeOutcome.ts). 0 when nothing was decided - check
  // decidedTrades before presenting it as a real "0%".
  winRate: number;
  totalTrades: number;   // every trade, neutral included
  decidedTrades: number; // wins + losses
  neutralTrades: number; // exactly 0 - shown to the user, never hidden
}

export function computeWinRate(trades: Trade[]): WinRateStats {
  let wins = 0;
  let losses = 0;
  let neutralTrades = 0;
  for (const t of trades) {
    const outcome = classifyPnl(t.profitLoss);
    if (outcome === 'win') wins++;
    else if (outcome === 'loss') losses++;
    else neutralTrades++;
  }
  const decidedTrades = wins + losses;
  return {
    winRate: decidedTrades === 0 ? 0 : Math.round((wins / decidedTrades) * 100),
    totalTrades: trades.length,
    decidedTrades,
    neutralTrades,
  };
}

// null (not 0) when there are no wins / no losses: "no data" must not read as
// "$0 average loss".
export function computeAvgWinLoss(trades: Trade[]): { avgWin: number | null; avgLoss: number | null } {
  const wins = trades.filter((t) => classifyPnl(t.profitLoss) === 'win').map((t) => t.profitLoss);
  const losses = trades.filter((t) => classifyPnl(t.profitLoss) === 'loss').map((t) => t.profitLoss);

  const avgWin = wins.length === 0 ? null : sumPnl(wins) / wins.length;
  const avgLoss = losses.length === 0 ? null : sumPnl(losses) / losses.length;

  return { avgWin, avgLoss };
}

export function computeEquityCurve(trades: Trade[]): EquityPoint[] {
  const sorted = [...trades].sort((a, b) =>
    compareAsc(parseISO(a.tradeDate), parseISO(b.tradeDate))
  );

  let running = 0;
  return sorted.map((trade) => {
    running += trade.profitLoss;
    return { date: format(parseISO(trade.tradeDate), 'MMM d'), cumulativePnl: running };
  });
}
