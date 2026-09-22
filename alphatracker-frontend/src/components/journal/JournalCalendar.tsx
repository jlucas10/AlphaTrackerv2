import React, { useMemo, useState } from 'react';
import {
  addMonths,
  eachDayOfInterval,
  endOfMonth,
  endOfWeek,
  format,
  isSameMonth,
  startOfMonth,
  startOfWeek,
  subMonths,
} from 'date-fns';
import type { Trade } from '../../types/Trade';
import { getPnlForDay, getTradesForDay, groupTradeListsByDay, groupTradesByDay } from '../../utils/pnlAggregations';
import CalendarDayCell from '../dashboard/CalendarDayCell';

interface JournalCalendarProps {
  trades: Trade[];
  onSelectDay: (day: Date) => void;
  // Highlights the day currently open in the panel, if any - otherwise a
  // deep-linked day (from DayDetailModal's pencil icon) has no visual anchor
  // once the panel closes.
  selectedDay?: Date | null;
}

const WEEKDAY_LABELS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

// Same month-grid shape as the dashboard's CalendarMatrix, but every day is
// clickable (alwaysInteractive) since a journal day panel is meaningful even
// with zero trades on it, and the click handler is passed in rather than
// opening a fixed modal - the journal page owns which panel opens.
export const JournalCalendar: React.FC<JournalCalendarProps> = ({ trades, onSelectDay, selectedDay }) => {
  const [visibleMonth, setVisibleMonth] = useState<Date>(selectedDay ?? new Date());

  const dayTotals = useMemo(() => groupTradesByDay(trades), [trades]);
  const dayTrades = useMemo(() => groupTradeListsByDay(trades), [trades]);

  const days = useMemo(() => {
    const gridStart = startOfWeek(startOfMonth(visibleMonth));
    const gridEnd = endOfWeek(endOfMonth(visibleMonth));
    return eachDayOfInterval({ start: gridStart, end: gridEnd });
  }, [visibleMonth]);

  return (
    <div className="bg-white p-6 rounded-2xl border border-gray-100 shadow-sm">
      <div className="flex justify-between items-center mb-6">
        <div className="flex items-center gap-4">
          <button
            onClick={() => setVisibleMonth((m) => subMonths(m, 1))}
            className="text-gray-400 hover:text-black font-bold"
          >
            ◀
          </button>
          <h3 className="text-sm font-black text-gray-900 uppercase tracking-wider">
            {format(visibleMonth, 'MMMM yyyy')}
          </h3>
          <button
            onClick={() => setVisibleMonth((m) => addMonths(m, 1))}
            className="text-gray-400 hover:text-black font-bold"
          >
            ▶
          </button>
        </div>
      </div>

      <div className="grid grid-cols-7 gap-1 mb-2">
        {WEEKDAY_LABELS.map((label) => (
          <div key={label} className="text-center text-xs font-bold text-gray-400 uppercase">
            {label}
          </div>
        ))}
      </div>

      <div className="grid grid-cols-7 gap-1">
        {days.map((day) => (
          <CalendarDayCell
            key={day.toISOString()}
            day={day}
            pnl={getPnlForDay(dayTotals, day)}
            inCurrentMonth={isSameMonth(day, visibleMonth)}
            tradeCount={getTradesForDay(dayTrades, day).length}
            onSelect={onSelectDay}
            alwaysInteractive
          />
        ))}
      </div>
    </div>
  );
};
