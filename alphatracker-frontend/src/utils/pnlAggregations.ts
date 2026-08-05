import { format, parseISO, compareAsc } from 'date-fns';
import type { Trade } from '../types/Trade';

export interface EquityPoint {
  date: string;
  cumulativePnl: number;
}

const DAY_KEY_FORMAT = 'yyyy-MM-dd';

export function groupTradesByDay(trades: Trade[]): Map<string, number> {
  const dayTotals = new Map<string, number>();
  for (const trade of trades) {
    const key = format(parseISO(trade.tradeDate), DAY_KEY_FORMAT);
    dayTotals.set(key, (dayTotals.get(key) ?? 0) + trade.profitLoss);
  }
  return dayTotals;
}

export function getPnlForDay(dayTotals: Map<string, number>, day: Date): number {
  return dayTotals.get(format(day, DAY_KEY_FORMAT)) ?? 0;
}

export function getMonthlyTotal(dayTotals: Map<string, number>, monthDate: Date): number {
  const monthPrefix = format(monthDate, 'yyyy-MM');
  let total = 0;
  for (const [key, pnl] of dayTotals) {
    if (key.startsWith(monthPrefix)) {
      total += pnl;
    }
  }
  return total;
}

export function computeWinRate(trades: Trade[]): { winRate: number; totalTrades: number } {
  const totalTrades = trades.length;
  if (totalTrades === 0) {
    return { winRate: 0, totalTrades: 0 };
  }
  const wins = trades.filter((t) => t.profitLoss > 0).length;
  return { winRate: Math.round((wins / totalTrades) * 100), totalTrades };
}

export function computeAvgWinLoss(trades: Trade[]): { avgWin: number; avgLoss: number } {
  const wins = trades.filter((t) => t.profitLoss > 0).map((t) => t.profitLoss);
  const losses = trades.filter((t) => t.profitLoss < 0).map((t) => t.profitLoss);

  const avgWin = wins.length === 0 ? 0 : wins.reduce((sum, v) => sum + v, 0) / wins.length;
  const avgLoss = losses.length === 0 ? 0 : losses.reduce((sum, v) => sum + v, 0) / losses.length;

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
