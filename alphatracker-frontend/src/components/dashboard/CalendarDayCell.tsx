import React from 'react';
import { format } from 'date-fns';

interface CalendarDayCellProps {
  day: Date;
  pnl: number;
  inCurrentMonth: boolean;
  tradeCount: number;
  onSelect: (day: Date) => void;
  // Dashboard default: only days with executions open anything, since a
  // button that opens an empty dialog is worse than a cell that plainly
  // isn't clickable. The journal calendar sets this true - a day can hold
  // notes/screenshots with zero trades on it, so every day needs to open.
  alwaysInteractive?: boolean;
}

const CalendarDayCell: React.FC<CalendarDayCellProps> = ({
  day,
  pnl,
  inCurrentMonth,
  tradeCount,
  onSelect,
  alwaysInteractive = false,
}) => {
  const colorClass = !inCurrentMonth
    ? 'bg-gray-50 text-gray-300'
    : pnl > 0
    ? 'bg-emerald-50 text-emerald-700'
    : pnl < 0
    ? 'bg-red-50 text-red-700'
    : 'bg-gray-50 text-gray-500';

  const isInteractive = inCurrentMonth && (alwaysInteractive || tradeCount > 0);

  return (
    <button
      type="button"
      disabled={!isInteractive}
      onClick={() => onSelect(day)}
      // enabled:* so the hover lift never fires on the non-interactive cells.
      className={`rounded-lg p-2 h-16 w-full flex flex-col text-left transition-all ${colorClass} ${
        isInteractive
          ? 'cursor-pointer enabled:hover:ring-2 enabled:hover:ring-slate-900/10 enabled:hover:-translate-y-0.5'
          : 'cursor-default'
      }`}
      title={
        isInteractive
          ? tradeCount > 0
            ? `${tradeCount} trade${tradeCount === 1 ? '' : 's'} — click to view`
            : 'Click to add journal notes'
          : undefined
      }
    >
      <div className="flex items-center justify-between w-full">
        <span className="text-xs font-semibold">{format(day, 'd')}</span>
        {/* Count dot: shows there is detail behind the number without adding
            clutter - only when there's actually a count worth showing, even
            on a calendar where every day is clickable. */}
        {tradeCount > 0 && <span className="text-[9px] font-bold opacity-60">{tradeCount}</span>}
      </div>

      {inCurrentMonth && pnl !== 0 && (
        <span className="text-xs font-bold mt-auto">
          {pnl > 0 ? '+' : '-'}${Math.abs(pnl).toFixed(0)}
        </span>
      )}
    </button>
  );
};

export default CalendarDayCell;
