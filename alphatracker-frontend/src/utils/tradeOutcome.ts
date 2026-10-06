// The win / loss / neutral rule, in one place. This mirrors the backend's
// TradeStats (see CONTEXT.md, "Win / Loss Definition") so the dashboard and
// the assistant never disagree about what counts as a win:
//
//   win     = profit  > 0
//   loss    = profit  < 0
//   neutral = profit == 0   (back at the start point - NOT a win, NOT a loss)
//
// The same rule applies to a whole day's net. Neutral is never hidden: a
// trader with lots of neutral trades/days is giving up profit or has a leaking
// system, so the UI shows it instead of folding it into wins or losses.

export type Outcome = 'win' | 'loss' | 'neutral';

export function classifyPnl(pnl: number): Outcome {
  if (pnl > 0) return 'win';
  if (pnl < 0) return 'loss';
  return 'neutral';
}

// Sums dollar amounts exactly, in whole cents. Plain float addition turns
// offsetting trades like +0.10, +0.20, -0.30 into 5.5e-17 instead of 0, which
// would classify a neutral day as a win day. Backend sums in cents too.
export function sumPnl(values: number[]): number {
  return values.reduce((cents, v) => cents + Math.round(v * 100), 0) / 100;
}
