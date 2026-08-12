import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useTrades } from '../hooks/useTrades';
import { TradeEntryModal } from '../components/TradeEntryModal';
import { computeAvgWinLoss, computeWinRate } from '../utils/pnlAggregations';
import CalendarMatrix from '../components/dashboard/CalendarMatrix';
import EquityCurveChart from '../components/dashboard/EquityCurveChart';

const DashboardView: React.FC = () => {
  const { logout } = useAuth();
  const { trades, loading, refreshing, error, refetch } = useTrades();

  // State for Trade Entry Modal
  const [isModalOpen, setIsModalOpen] = useState<boolean>(false);

  // TODO Sprint 2: replace with real Account/discipline data once the Account entity exists
  const disciplineScore = 94;
  const availableCapital = 0;

  const { winRate, totalTrades } = computeWinRate(trades);
  const { avgWin, avgLoss } = computeAvgWinLoss(trades);

  return (
    <div className="flex h-screen w-screen bg-gray-50 text-gray-800 font-sans overflow-hidden">
      
      {/* ================= SIDEBAR NAVIGATION ================= */}
      <aside className="w-64 bg-white border-r border-gray-100 flex flex-col justify-between p-6">
        <div>
          {/* Branding Logo */}
          <div className="flex items-center gap-3 mb-10 px-2">
            <div className="grid grid-cols-2 gap-1 w-5 h-5">
              <div className="bg-black rounded-sm"></div>
              <div className="bg-black rounded-sm"></div>
              <div className="bg-black rounded-sm"></div>
              <div className="bg-black rounded-sm"></div>
            </div>
            <span className="font-black text-xl tracking-tight text-gray-900">AlphaTracker</span>
          </div>

          {/* Navigation Links */}
          <nav className="space-y-1">
            <button className="w-full flex items-center gap-3 px-4 py-3 bg-slate-900 text-white rounded-xl font-bold text-sm transition-all shadow-sm">
              <span>📊</span> Dashboard
            </button>
            <button className="w-full flex items-center gap-3 px-4 py-3 text-gray-500 hover:text-black font-semibold text-sm transition-all rounded-xl hover:bg-gray-50">
              <span>📈</span> Investing
            </button>
            <button className="w-full flex items-center gap-3 px-4 py-3 text-gray-500 hover:text-black font-semibold text-sm transition-all rounded-xl hover:bg-gray-50">
              <span>💳</span> Accounts
            </button>
            <button className="w-full flex items-center gap-3 px-4 py-3 text-gray-500 hover:text-black font-semibold text-sm transition-all rounded-xl hover:bg-gray-50">
              <span>📓</span> Trading Journal
            </button>
          </nav>
        </div>

        {/* User Profile Footer Section */}
        <div className="border-t border-gray-100 pt-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-full bg-slate-700 flex items-center justify-center text-white font-bold text-sm">
              J
            </div>
            <div>
              <p className="text-sm font-bold text-gray-900">Josiah</p>
              <button onClick={logout} className="text-xs text-red-500 hover:underline font-semibold">
                Sign Out
              </button>
            </div>
          </div>
        </div>
      </aside>

      {/* ================= MAIN CONTENT AREA ================= */}
      <main className="flex-1 overflow-y-auto p-8 space-y-6">

        {loading && (
          <div className="bg-white p-4 rounded-2xl border border-gray-100 shadow-sm text-sm text-gray-400 font-semibold">
            Loading trades...
          </div>
        )}

        {/* Background refetch after logging a trade: the numbers below stay on
            screen and update in place rather than collapsing into a loader. */}
        {refreshing && (
          <div className="text-xs font-bold text-gray-400 uppercase tracking-wider">
            Updating...
          </div>
        )}

        {error && !loading && (
          <div className="bg-red-50 p-4 rounded-2xl border border-red-100 shadow-sm text-sm text-red-500 font-semibold">
            Failed to load trades: {error}
          </div>
        )}

        {/* TOP ROW: Active Account Banner */}
        <div className="bg-white p-6 rounded-2xl border border-gray-100 shadow-sm flex justify-between items-center">
          <div className="flex gap-10">
            <div>
              <p className="text-xs font-bold text-gray-400 uppercase tracking-wider">Active Account</p>
              <p className="text-sm font-semibold text-gray-400 mt-1">No funded account selected</p>
            </div>
            <div className="border-l border-gray-100 pl-10">
              <p className="text-xs font-bold text-gray-400 uppercase tracking-wider">Discipline Score</p>
              <p className="text-2xl font-black text-emerald-500 mt-0.5">{disciplineScore}%</p>
            </div>
          </div>

          <div className="flex items-center gap-6">
            <button
              onClick={() => setIsModalOpen(true)}
              className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-sm rounded-xl transition-all shadow-sm"
            >
              + Log Trade
            </button>

            <div className="text-right">
              <p className="text-3xl font-black text-gray-900">${availableCapital}</p>
              <p className="text-xs font-bold text-gray-400 uppercase tracking-wider mt-0.5">Available Capital</p>
            </div>
          </div>
        </div>

        {/* MIDDLE ROW: Performance Analytics Grid */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          
          {/* Win Rate Ring Card */}
          <div className="bg-white p-6 rounded-2xl border border-gray-100 shadow-sm flex flex-col items-center justify-between h-64 text-center">
            <p className="w-full text-left text-xs font-bold text-gray-400 uppercase tracking-wider">Win Rate %</p>
            <div className="relative w-32 h-32 flex items-center justify-center rounded-full border-8 border-red-100">
              <span className="text-2xl font-black text-gray-900">{winRate}%</span>
            </div>
            <p className="text-xs font-bold text-gray-400 uppercase tracking-wider">Total Trades: {totalTrades}</p>
          </div>

          {/* Average Win / Loss Metrics Card */}
          <div className="bg-white p-6 rounded-2xl border border-gray-100 shadow-sm flex flex-col justify-between h-64">
            <div>
              <p className="text-xs font-bold text-gray-400 uppercase tracking-wider">Avg Win</p>
              <p className="text-2xl font-black text-emerald-500 mt-1">${avgWin.toFixed(0)}</p>
            </div>
            <div className="border-t border-gray-100 pt-4">
              <p className="text-xs font-bold text-gray-400 uppercase tracking-wider">Avg Loss</p>
              <p className="text-2xl font-black text-red-500 mt-1">-${Math.abs(avgLoss).toFixed(0)}</p>
            </div>
          </div>

          {/* Equity Curve Card */}
          <div className="bg-white p-6 rounded-2xl border border-gray-100 shadow-sm flex flex-col justify-between h-64">
            <p className="text-xs font-bold text-gray-400 uppercase tracking-wider">Equity Curve</p>
            <EquityCurveChart trades={trades} />
          </div>

        </div>

        {/* BOTTOM ROW: Calendar Matrix Container */}
        <CalendarMatrix trades={trades} />

        {/* TRADE ENTRY MODAL */}
        <TradeEntryModal
          isOpen={isModalOpen}
          onClose={() => setIsModalOpen(false)}
          onTradeAdded={refetch}
        />

      </main>
    </div>
  );
};

export default DashboardView;