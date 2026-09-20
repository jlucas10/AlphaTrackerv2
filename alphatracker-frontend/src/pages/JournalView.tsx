import React from 'react';
import { useTrades } from '../hooks/useTrades';
import { Sidebar } from '../components/layout/Sidebar';
import { JournalEntryCard } from '../components/journal/JournalEntryCard';

// Deliberately unscoped by account (useTrades(undefined)) - the journal is a
// review surface across a trader's whole history, not a single prop account's
// balance sheet, unlike the dashboard.
const JournalView: React.FC = () => {
  const { trades, loading, error } = useTrades();

  const sortedTrades = trades
    .slice()
    .sort((a, b) => new Date(b.tradeDate).getTime() - new Date(a.tradeDate).getTime());

  return (
    <div className="flex h-screen w-screen bg-gray-50 text-gray-800 font-sans overflow-hidden">
      <Sidebar active="journal" />

      <main className="flex-1 overflow-y-auto p-8 space-y-6">
        <div>
          <h1 className="text-2xl font-black text-gray-900">Trading Journal</h1>
          <p className="text-xs font-bold text-gray-400 uppercase tracking-wider mt-1">
            Review executions, screenshots, and reflections
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

        {!loading && sortedTrades.length === 0 && (
          <div className="bg-white p-8 rounded-2xl border border-gray-100 shadow-xs text-center text-sm text-gray-400 font-semibold">
            No executions logged yet. Log a trade to start building your journal.
          </div>
        )}

        <div className="space-y-4">
          {sortedTrades.map((trade) => (
            <JournalEntryCard key={trade.id} trade={trade} />
          ))}
        </div>
      </main>
    </div>
  );
};

export default JournalView;
