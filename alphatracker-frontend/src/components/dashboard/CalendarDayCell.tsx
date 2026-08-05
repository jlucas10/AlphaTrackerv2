import React from 'react';
import { format } from 'date-fns';

interface CalendarDayCellProps {
  day: Date;
  pnl: number;
  inCurrentMonth: boolean;
}

const CalendarDayCell: React.FC<CalendarDayCellProps> = ({ day, pnl, inCurrentMonth }) => {
  const colorClass = !inCurrentMonth
    ? 'bg-gray-50 text-gray-300'
    : pnl > 0
    ? 'bg-emerald-50 text-emerald-700'
    : pnl < 0
    ? 'bg-red-50 text-red-700'
    : 'bg-gray-50 text-gray-500';

  return (
    <div className={`rounded-lg p-2 h-16 flex flex-col ${colorClass}`}>
      <span className="text-xs font-semibold">{format(day, 'd')}</span>
      {inCurrentMonth && pnl !== 0 && (
        <span className="text-xs font-bold mt-auto">
          {pnl > 0 ? '+' : '-'}${Math.abs(pnl).toFixed(0)}
        </span>
      )}
    </div>
  );
};

export default CalendarDayCell;
