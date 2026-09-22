import React from 'react';
import { useSearchParams } from 'react-router-dom';
import { format, parseISO } from 'date-fns';
import { useTrades } from '../hooks/useTrades';
import { Sidebar } from '../components/layout/Sidebar';
import { JournalCalendar } from '../components/journal/JournalCalendar';
import { JournalDayPanel } from '../components/journal/JournalDayPanel';

// Unscoped by account (useTrades(undefined)) - the journal is a review
// surface across a trader's whole history, not a single prop account's
// balance sheet, unlike the dashboard.
//
// The selected day lives in the URL (?date=YYYY-MM-DD) rather than local
// component state, so DayDetailModal's pencil icon can deep-link straight
// into an open day panel, and refreshing the page keeps it open.
const JournalView: React.FC = () => {
  const { trades, loading, error } = useTrades();
  const [searchParams, setSearchParams] = useSearchParams();

  const selectedDate = searchParams.get('date');

  const openDay = (day: Date) => {
    setSearchParams({ date: format(day, 'yyyy-MM-dd') });
  };

  const closeDay = () => {
    setSearchParams({});
  };

  return (
    <div className="flex h-screen w-screen bg-gray-50 text-gray-800 font-sans overflow-hidden">
      <Sidebar active="journal" />

      <main className="flex-1 overflow-y-auto p-8 space-y-6">
        <div>
          <h1 className="text-2xl font-black text-gray-900">Trading Journal</h1>
          <p className="text-xs font-bold text-gray-400 uppercase tracking-wider mt-1">
            Click a day to add notes, screenshots, and reflections
          </p>
        </div>

        {loading && (
          <div className="bg-white p-4 rounded-2xl border border-gray-100 shadow-xs text-sm text-gray-400 font-semibold">
            Loading journal...
          </div>
        )}

        {error && !loading && (
          <div className="bg-red-50 p-4 rounded-2xl border border-red-100 shadow-xs text-sm text-red-500 font-semibold">
            Failed to load journal: {error}
          </div>
        )}

        <JournalCalendar
          trades={trades}
          onSelectDay={openDay}
          selectedDay={selectedDate ? parseISO(selectedDate) : null}
        />

        {selectedDate && <JournalDayPanel date={selectedDate} onClose={closeDay} />}
      </main>
    </div>
  );
};

export default JournalView;
