import React, { useState } from 'react';
import { useTrades } from '../hooks/useTrades';
import { useAccount } from '../hooks/useAccounts';
import { Sidebar } from '../components/layout/Sidebar';
import { AccountSelector } from '../components/dashboard/AccountSelector';
import { DrawdownGauge } from '../components/dashboard/DrawdownGauge';
import { BackfillBanner } from '../components/dashboard/BackfillBanner';
import { CreateAccountModal } from '../components/dashboard/CreateAccountModal';
import { ImportTradesModal } from '../components/dashboard/ImportTradesModal';
import { TradeEntryModal } from '../components/TradeEntryModal';
import { computeAvgWinLoss, computeWinRate } from '../utils/pnlAggregations';
import { fundedPrefillFrom, type AccountAction } from '../utils/accountActions';
import type { CreateAccountPayload } from '../types/Account';
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
    updateAccountStatus,
    unassignedTradeCount,
    backfillUnassignedTrades,
  } = useAccount();

  // Trades Scoped to Active Account
  const { trades, loading, refreshing, error, refetch, deleteTrade } = useTrades(selectedAccountId);

  // Modal States
  const [isTradeModalOpen, setIsTradeModalOpen] = useState<boolean>(false);
  // Mounted only while open, so every opening starts at step 1 with nothing left over from the last import.
  const [isImportOpen, setIsImportOpen] = useState<boolean>(false);
  // null = closed. One piece of state instead of a boolean plus loose prefill state: a plain "+ Account"
  // opens it empty, a pass opens it prefilled. The modal is mounted only while this is set, so every
  // opening starts from fresh values.
  const [createAccountForm, setCreateAccountForm] = useState<{
    prefill?: CreateAccountPayload;
    defaultMakePrimary: boolean;
  } | null>(null);

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

  const { winRate, totalTrades, decidedTrades, neutralTrades } = computeWinRate(trades);
  const { avgWin, avgLoss } = computeAvgWinLoss(trades);

  const primaryAccount = accounts.find((a) => a.isPrimary) ?? null;

  // A funded account already created from the selected evaluation (decides whether to offer "Set up funded").
  const hasSuccessor = selectedAccount
    ? accounts.some((a) => a.promotedFromAccountId === selectedAccount.id)
    : false;

  const handleAccountAction = async (action: AccountAction) => {
    if (!selectedAccount) return;
    switch (action) {
      case 'PASS': {
        // Read before the PATCH: closing the primary un-sets it, and the funded form's "make primary"
        // box should start ticked only if this evaluation WAS the primary account.
        const wasPrimary = selectedAccount.isPrimary;
        // Saved first, the form second: passing is the trader's decision whatever they do with the form,
        // and Reopen undoes it. If they close the form, "Set up funded account" brings it back.
        const passed = await updateAccountStatus(selectedAccount.id, 'PASSED');
        setCreateAccountForm({ prefill: fundedPrefillFrom(passed), defaultMakePrimary: wasPrimary });
        break;
      }
      case 'SETUP_FUNDED':
        // The evaluation is already closed and no longer primary; tick the box only if nothing else is.
        setCreateAccountForm({ prefill: fundedPrefillFrom(selectedAccount), defaultMakePrimary: primaryAccount === null });
        break;
      case 'FAIL':
        await updateAccountStatus(selectedAccount.id, 'FAILED');
        break;
      case 'REOPEN':
        await updateAccountStatus(selectedAccount.id, 'ACTIVE');
        break;
    }
  };

  return (
    <div className="flex h-screen w-screen bg-gray-50 text-gray-800 font-sans overflow-hidden">
      
      <Sidebar active="dashboard" />

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
                onOpenCreateModal={() => setCreateAccountForm({ defaultMakePrimary: false })}
                onSetPrimary={(accountId) => setPrimaryAccount(accountId)}
              />
            </div>
            <div className="border-l border-gray-100 pl-8">
              <p className="text-xs font-bold text-gray-400 uppercase tracking-wider">Discipline Score</p>
              <p className="text-2xl font-black text-emerald-500 mt-0.5">{disciplineScore}%</p>
            </div>
          </div>

          <div className="flex items-center gap-6">
            <div className="flex items-center gap-2">
              <button
                onClick={() => setIsImportOpen(true)}
                className="px-4 py-2.5 bg-white hover:bg-gray-50 text-gray-700 border border-gray-200 font-bold text-sm rounded-xl transition-all shadow-xs cursor-pointer"
              >
                Import trades
              </button>
              <button
                onClick={() => setIsTradeModalOpen(true)}
                className="px-4 py-2.5 bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-sm rounded-xl transition-all shadow-xs cursor-pointer"
              >
                + Log Trade
              </button>
            </div>

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
          <DrawdownGauge account={selectedAccount} hasSuccessor={hasSuccessor} onAction={handleAccountAction} />
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
            <WinRateRing winRate={winRate} decidedTrades={decidedTrades} />
            <p className="text-xs font-bold text-gray-400 uppercase tracking-wider">Total Trades: {totalTrades}</p>
            {/* Neutral (exactly $0) trades are excluded from the win rate but
                never hidden - lots of them means profit is being given up. */}
            {neutralTrades > 0 && (
              <p className="text-[10px] font-bold text-amber-600 uppercase tracking-wider">
                {neutralTrades} neutral ($0)
              </p>
            )}
          </div>

          {/* Average Win / Loss Metrics Card */}
          <div className="bg-white p-6 rounded-2xl border border-gray-100 shadow-xs flex flex-col justify-between h-64">
            <div>
              <p className="text-xs font-bold text-gray-400 uppercase tracking-wider">Avg Win</p>
              <p className="text-2xl font-black text-emerald-500 mt-1">
                {avgWin === null ? '—' : `$${avgWin.toFixed(0)}`}
              </p>
            </div>
            <div className="border-t border-gray-100 pt-4">
              <p className="text-xs font-bold text-gray-400 uppercase tracking-wider">Avg Loss</p>
              <p className="text-2xl font-black text-red-500 mt-1">
                {avgLoss === null ? '—' : `-$${Math.abs(avgLoss).toFixed(0)}`}
              </p>
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

        {isImportOpen && (
          <ImportTradesModal
            onClose={() => setIsImportOpen(false)}
            accounts={accounts}
            defaultAccountId={selectedAccountId}
            onImported={async () => {
              await refetch();
              await refetchAccounts();
            }}
          />
        )}

        {createAccountForm && (
          <CreateAccountModal
            isOpen
            onClose={() => setCreateAccountForm(null)}
            prefill={createAccountForm.prefill}
            defaultMakePrimary={createAccountForm.defaultMakePrimary}
            onSubmit={async (payload, makePrimary) => {
              const created = await createAccount(payload);
              if (makePrimary) {
                // The account already exists at this point. If only this follow-up fails, don't report the
                // whole form as failed: resubmitting would create a duplicate. "☆ Set Primary" still works.
                try {
                  await setPrimaryAccount(created.id);
                } catch {
                  /* non-critical, see above */
                }
              }
            }}
          />
        )}

      </main>
    </div>
  );
};

export default DashboardView;