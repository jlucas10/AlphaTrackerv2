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
import { getMonthlyTotal, getPnlForDay, groupTradesByDay } from '../../utils/pnlAggregations';
import CalendarDayCell from './CalendarDayCell';

interface CalendarMatrixProps {
  trades: Trade[];
}

const WEEKDAY_LABELS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

const CalendarMatrix: React.FC<CalendarMatrixProps> = ({ trades }) => {
  const [visibleMonth, setVisibleMonth] = useState<Date>(new Date());

  const dayTotals = useMemo(() => groupTradesByDay(trades), [trades]);

  const days = useMemo(() => {
    const gridStart = startOfWeek(startOfMonth(visibleMonth));
    const gridEnd = endOfWeek(endOfMonth(visibleMonth));
    return eachDayOfInterval({ start: gridStart, end: gridEnd });
  }, [visibleMonth]);

  const monthlyTotal = getMonthlyTotal(dayTotals, visibleMonth);

  return (
    <div className="bg-white p-6 rounded-2xl border border-gray-100 shadow-sm min-h-[300px]">
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
        <p
          className={`text-xs font-bold uppercase tracking-wider ${
            monthlyTotal >= 0 ? 'text-emerald-500' : 'text-red-500'
          }`}
        >
          Monthly P/L: {monthlyTotal >= 0 ? '+' : '-'}${Math.abs(monthlyTotal).toFixed(0)}
        </p>
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
          />
        ))}
      </div>
    </div>
  );
};

export default CalendarMatrix;
