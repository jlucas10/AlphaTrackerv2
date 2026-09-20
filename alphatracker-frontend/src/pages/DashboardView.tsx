import React, { useState } from 'react';
import { useTrades } from '../hooks/useTrades';
import { useAccount } from '../hooks/useAccounts';
import { Sidebar } from '../components/layout/Sidebar';
import { AccountSelector } from '../components/dashboard/AccountSelector';
import { DrawdownGauge } from '../components/dashboard/DrawdownGauge';
import { BackfillBanner } from '../components/dashboard/BackfillBanner';
import { CreateAccountModal } from '../components/dashboard/CreateAccountModal';
import { TradeEntryModal } from '../components/TradeEntryModal';
import { computeAvgWinLoss, computeWinRate } from '../utils/pnlAggregations';
import CalendarMatrix from '../components/dashboard/CalendarMatrix';
import EquityCurveChart from '../components/dashboard/EquityCurveChart';
import WinRateRing from '../components/dashboard/WinRateRing';
import TradeTable from '../components/dashboard/TradeTable';

const DashboardView: React.FC = () => {
  // Account State Hook
  const {
    accounts,
    selectedAccountId,
    setSelectedAccountId,
    selectedAccount,
    createAccount,
    refetchAccounts,
    setPrimaryAccount,
    unassignedTradeCount,
    backfillUnassignedTrades,
  } = useAccount();

  // Trades Scoped to Active Account
  const { trades, loading, refreshing, error, refetch, deleteTrade } = useTrades(selectedAccountId);

  // Modal States
  const [isTradeModalOpen, setIsTradeModalOpen] = useState<boolean>(false);
  const [isCreateAccountOpen, setIsCreateAccountOpen] = useState<boolean>(false);

  // Calculate Discipline Score dynamically from followedPlan executions
  const disciplineScore =
    trades.length > 0
      ? Math.round((trades.filter((t) => t.followedPlan).length / trades.length) * 100)
      : 100;

  // Derive Available Capital: selected account current balance OR cumulative P/L
  const totalCumulativePnl = trades.reduce((sum, t) => sum + t.profitLoss, 0);
  const displayCapital = selectedAccount
    ? selectedAccount.currentBalance
    : totalCumulativePnl;

  const { winRate, totalTrades } = computeWinRate(trades);
  const { avgWin, avgLoss } = computeAvgWinLoss(trades);

  const primaryAccount = accounts.find((a) => a.isPrimary) ?? null;

  return (
    <div className="flex h-screen w-screen bg-gray-50 text-gray-800 font-sans overflow-hidden">
      
      <Sidebar active="dashboard" onOpenAccounts={() => setIsCreateAccountOpen(true)} />

      {/* ================= MAIN CONTENT AREA ================= */}
      <main className="flex-1 overflow-y-auto p-8 space-y-6">

        {loading && (
          <div className="bg-white p-4 rounded-2xl border border-gray-100 shadow-xs text-sm text-gray-400 font-semibold">
            Loading executions and prop accounts...
          </div>
        )}

        {refreshing && (
          <div className="text-xs font-bold text-gray-400 uppercase tracking-wider">
            Updating metrics...
          </div>
        )}

        {error && !loading && (
          <div className="bg-red-50 p-4 rounded-2xl border border-red-100 shadow-xs text-sm text-red-500 font-semibold">
            Failed to load trades: {error}
          </div>
        )}

        {/* TOP ROW: Active Account Banner & Action Controls */}
        <div className="bg-white p-6 rounded-2xl border border-gray-100 shadow-xs flex flex-wrap justify-between items-center gap-4">
          <div className="flex items-center gap-8">
            <div>
              <p className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-1.5">Active Account</p>
              <AccountSelector
                accounts={accounts}
                selectedAccountId={selectedAccountId}
                onSelectAccount={setSelectedAccountId}
                onOpenCreateModal={() => setIsCreateAccountOpen(true)}
                onSetPrimary={(accountId) => setPrimaryAccount(accountId)}
              />
            </div>
            <div className="border-l border-gray-100 pl-8">
              <p className="text-xs font-bold text-gray-400 uppercase tracking-wider">Discipline Score</p>
              <p className="text-2xl font-black text-emerald-500 mt-0.5">{disciplineScore}%</p>
            </div>
          </div>

          <div className="flex items-center gap-6">
            <button
              onClick={() => setIsTradeModalOpen(true)}
              className="px-4 py-2.5 bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-sm rounded-xl transition-all shadow-xs cursor-pointer"
            >
              + Log Trade
            </button>

            <div className="text-right">
              <p className="text-3xl font-black text-gray-900">
                ${displayCapital.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
              </p>
              <p className="text-xs font-bold text-gray-400 uppercase tracking-wider mt-0.5">
                {selectedAccount ? 'Account Balance' : 'Cumulative P/L'}
              </p>
            </div>
          </div>
        </div>

        {/* Drawdown Risk Engine Gauge (Rendered when a prop account is selected) */}
        {selectedAccount && (
          <DrawdownGauge account={selectedAccount} />
        )}

        {/* Backfill banner: only meaningful once a primary account exists to
            backfill onto, and only shown when there's actually something
            unassigned to move. */}
        {primaryAccount && (
          <BackfillBanner
            primaryAccount={primaryAccount}
            unassignedTradeCount={unassignedTradeCount}
            onBackfill={backfillUnassignedTrades}
            onBackfilled={async () => {
              await refetch();
              await refetchAccounts();
            }}
          />
        )}

        {/* MIDDLE ROW: Performance Analytics Grid */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          
          {/* Win Rate Ring Card */}
          <div className="bg-white p-6 rounded-2xl border border-gray-100 shadow-xs flex flex-col items-center justify-between h-64 text-center">
            <p className="w-full text-left text-xs font-bold text-gray-400 uppercase tracking-wider">Win Rate %</p>
            <WinRateRing winRate={winRate} totalTrades={totalTrades} />
            <p className="text-xs font-bold text-gray-400 uppercase tracking-wider">Total Trades: {totalTrades}</p>
          </div>

          {/* Average Win / Loss Metrics Card */}
          <div className="bg-white p-6 rounded-2xl border border-gray-100 shadow-xs flex flex-col justify-between h-64">
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
          <div className="bg-white p-6 rounded-2xl border border-gray-100 shadow-xs flex flex-col justify-between h-64">
            <p className="text-xs font-bold text-gray-400 uppercase tracking-wider">Equity Curve</p>
            <EquityCurveChart trades={trades} />
          </div>

        </div>

        {/* BOTTOM ROW: Calendar Matrix Container */}
        <CalendarMatrix trades={trades} onDeleteTrade={deleteTrade} />

        <TradeTable trades={trades} onDelete={deleteTrade} />

        <TradeEntryModal
          isOpen={isTradeModalOpen}
          onClose={() => setIsTradeModalOpen(false)}
          onTradeAdded={async () => {
            await refetch();
            await refetchAccounts();
          }}
          accounts={accounts}
          defaultAccountId={selectedAccountId}
        />

        <CreateAccountModal
          isOpen={isCreateAccountOpen}
          onClose={() => setIsCreateAccountOpen(false)}
          onSubmit={async (payload) => {
            await createAccount(payload);
          }}
        />

      </main>
    </div>
  );
};

export default DashboardView;