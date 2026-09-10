import React from 'react';
import type { Account } from '../../types/Account';

interface DrawdownGaugeProps {
  account: Account | null;
}

export const DrawdownGauge: React.FC<DrawdownGaugeProps> = ({ account }) => {
  if (!account) {
    return (
      <div className="bg-neutral-900/60 border border-neutral-800/80 rounded-xl p-4 text-neutral-400 text-xs font-mono flex items-center justify-between">
        <span>No specific prop account selected.</span>
        <span className="text-neutral-500">Select an account to view trailing drawdown.</span>
      </div>
    );
  }

  const starting = account.startingBalance;
  const current = account.currentBalance;
  const maxDd = account.maxDrawdown;
  const pnl = current - starting;

  // Trailing limit moves up as account balance reaches new peaks
  const peakBalance = Math.max(starting, current);
  const drawdownFloor = peakBalance - maxDd;
  const cushion = Math.max(0, current - drawdownFloor);
  const cushionPercent = Math.min(100, Math.max(0, (cushion / maxDd) * 100));

  const isSafe = cushionPercent > 50;
  const isWarning = cushionPercent <= 50 && cushionPercent > 20;

  const barColor = isSafe ? 'bg-emerald-500' : isWarning ? 'bg-amber-500' : 'bg-rose-500';

  return (
    <div className="bg-neutral-900 border border-neutral-800 rounded-xl p-4 flex flex-col justify-between">
      <div className="flex items-center justify-between">
        <div>
          <div className="flex items-center gap-2">
            <span className="text-[10px] font-mono uppercase tracking-wider px-1.5 py-0.5 rounded bg-neutral-800 text-neutral-300">
              {account.firm}
            </span>
            <span className="text-[10px] font-mono uppercase tracking-wider text-neutral-400">
              {account.accountType}
            </span>
          </div>
          <h3 className="text-sm font-semibold text-neutral-100 mt-1 font-mono">{account.name}</h3>
        </div>
        <div className="text-right">
          <span className="text-[10px] uppercase text-neutral-400 font-mono">Net P/L</span>
          <p className={`text-sm font-mono font-bold ${pnl >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
            {pnl >= 0 ? `+$${pnl.toFixed(2)}` : `-$${Math.abs(pnl).toFixed(2)}`}
          </p>
        </div>
      </div>

      <div className="grid grid-cols-3 gap-2 py-2.5 border-y border-neutral-800/80 my-3 text-center">
        <div>
          <span className="text-[10px] text-neutral-400 uppercase font-mono">Balance</span>
          <p className="text-xs font-mono font-semibold text-neutral-200">
            ${current.toLocaleString('en-US', { minimumFractionDigits: 2 })}
          </p>
        </div>
        <div>
          <span className="text-[10px] text-neutral-400 uppercase font-mono">DD Floor</span>
          <p className="text-xs font-mono font-semibold text-neutral-400">
            ${drawdownFloor.toLocaleString('en-US', { minimumFractionDigits: 2 })}
          </p>
        </div>
        <div>
          <span className="text-[10px] text-neutral-400 uppercase font-mono">Buffer Left</span>
          <p className={`text-xs font-mono font-bold ${cushionPercent <= 20 ? 'text-rose-400' : 'text-emerald-400'}`}>
            ${cushion.toLocaleString('en-US', { minimumFractionDigits: 2 })}
          </p>
        </div>
      </div>

      <div>
        <div className="flex justify-between text-[11px] text-neutral-400 mb-1 font-mono">
          <span>Trailing Drawdown Room</span>
          <span>{cushionPercent.toFixed(1)}%</span>
        </div>
        <div className="w-full bg-neutral-800 h-1.5 rounded-full overflow-hidden">
          <div
            className={`h-full ${barColor} transition-all duration-500`}
            style={{ width: `${cushionPercent}%` }}
          />
        </div>
      </div>
    </div>
  );
};