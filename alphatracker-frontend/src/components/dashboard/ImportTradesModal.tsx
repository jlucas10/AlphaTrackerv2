import React, { useRef, useState } from 'react';
import { format, parseISO } from 'date-fns';
import { confirmImport, previewImport } from '../../api/imports';
import type { Account } from '../../types/Account';
import type {
  ImportConfirmResult,
  ImportPreview,
  ImportPreviewRow,
  ImportSource,
} from '../../types/Import';
import { answerAll, isImportable, toPlanAnswers, unansweredCount, type PlanChoices } from '../../utils/importChoices';
import { formatMoney, formatUsd } from '../../utils/formatters';
import { classifyPnl } from '../../utils/tradeOutcome';

interface ImportTradesModalProps {
  onClose: () => void;
  accounts: Account[];
  // Pre-ticked in the account list: the account the dashboard is showing.
  defaultAccountId: number | null;
  // Called once the trades are saved, so the dashboard refetches trades and account balances.
  onImported: () => Promise<void> | void;
}

type Step = 'select' | 'preview' | 'done';

// Only one broker today. A new one is a new entry here (and a new adapter on the server).
const SOURCES: { value: ImportSource; label: string; hint: string }[] = [
  { value: 'TOPSTEPX', label: 'TopstepX', hint: 'The trades export (.csv) from TopstepX.' },
];

const fieldClass =
  'w-full bg-neutral-950 border border-neutral-800 rounded-lg px-3 py-2 text-sm text-neutral-200 font-mono focus:outline-hidden focus:border-emerald-500';

const pnlColor = (pnl: number) => {
  const outcome = classifyPnl(pnl);
  return outcome === 'win' ? 'text-emerald-400' : outcome === 'loss' ? 'text-rose-400' : 'text-amber-400';
};

// "Oct 13, 07:45:11": the server already converted to the journal's zone, so no timezone maths happens here.
const formatWhen = (iso: string) => format(parseISO(iso), 'MMM d, HH:mm:ss');

export const ImportTradesModal: React.FC<ImportTradesModalProps> = ({
  onClose,
  accounts,
  defaultAccountId,
  onImported,
}) => {
  const [step, setStep] = useState<Step>('select');
  const [source, setSource] = useState<ImportSource>('TOPSTEPX');
  const [accountIds, setAccountIds] = useState<number[]>(defaultAccountId != null ? [defaultAccountId] : []);
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [choices, setChoices] = useState<PlanChoices>({});
  const [result, setResult] = useState<ImportConfirmResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  const toggleAccount = (id: number) =>
    setAccountIds((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));

  const messageOf = (err: unknown, fallback: string) =>
    (err as { response?: { data?: { message?: string } } }).response?.data?.message ||
    (err as Error).message ||
    fallback;

  const handlePreview = async () => {
    if (!file || accountIds.length === 0) return;
    setError(null);
    setBusy(true);
    try {
      const data = await previewImport(file, source, accountIds);
      setPreview(data);
      setChoices({}); // nothing answered yet; an import never assumes "followed"
      setStep('preview');
    } catch (err) {
      setError(messageOf(err, 'Could not read that file.'));
    } finally {
      setBusy(false);
    }
  };

  const handleConfirm = async () => {
    if (!file || !preview) return;
    setError(null);
    setBusy(true);
    try {
      const data = await confirmImport(file, source, accountIds, toPlanAnswers(preview.rows, choices));
      setResult(data);
      setStep('done');
      await onImported();
    } catch (err) {
      setError(messageOf(err, 'The import failed. Nothing was saved.'));
    } finally {
      setBusy(false);
    }
  };

  const backToSelect = () => {
    setPreview(null);
    setChoices({});
    setError(null);
    setStep('select');
  };

  const accountName = (id: number) => preview?.accounts.find((a) => a.id === id)?.name ?? `#${id}`;

  const renderSelect = () => (
    <div className="mt-4 space-y-4">
      <div>
        <label className="block text-xs font-mono text-neutral-400 mb-1">Source</label>
        <select value={source} onChange={(e) => setSource(e.target.value as ImportSource)} className={fieldClass}>
          {SOURCES.map((s) => (
            <option key={s.value} value={s.value}>
              {s.label}
            </option>
          ))}
        </select>
        <p className="mt-1 text-[10px] font-mono text-neutral-500">{SOURCES.find((s) => s.value === source)?.hint}</p>
      </div>

      <div>
        <label className="block text-xs font-mono text-neutral-400 mb-1">Import into</label>
        {accounts.length === 0 ? (
          <p className="text-xs font-mono text-amber-400">Create a prop account first, then import into it.</p>
        ) : (
          <div className="space-y-1.5">
            {accounts.map((a) => (
              <label
                key={a.id}
                className="flex items-center gap-2 text-xs font-mono text-neutral-300 cursor-pointer bg-neutral-950 border border-neutral-800 rounded-lg px-3 py-2"
              >
                <input
                  type="checkbox"
                  checked={accountIds.includes(a.id)}
                  onChange={() => toggleAccount(a.id)}
                  className="accent-emerald-500"
                />
                <span className="flex-1">
                  {a.name} <span className="text-neutral-500">· {a.firm}</span>
                </span>
                {a.status !== 'ACTIVE' && (
                  <span className="text-[10px] uppercase tracking-wider text-amber-400">{a.status}</span>
                )}
              </label>
            ))}
          </div>
        )}
        <p className="mt-1 text-[10px] font-mono text-neutral-500 leading-snug">
          Copy-trading? Tick every account the trades were taken on; each one gets its own copy. Trades already imported
          into an account are skipped for that account only.
        </p>
      </div>

      <div>
        <label className="block text-xs font-mono text-neutral-400 mb-1">File</label>
        <input
          ref={fileInput}
          type="file"
          accept=".csv,text/csv"
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          className="block w-full text-xs font-mono text-neutral-300 file:mr-3 file:px-3 file:py-2 file:rounded-lg file:border-0 file:bg-neutral-800 file:text-neutral-200 file:text-xs file:font-mono hover:file:bg-neutral-700"
        />
        <p className="mt-1 text-[10px] font-mono text-neutral-500">CSV only, up to 1 MB and 2,000 trades.</p>
      </div>

      <div className="flex justify-end gap-3 pt-3 border-t border-neutral-800">
        <button type="button" onClick={onClose} className="px-4 py-2 text-xs font-mono text-neutral-400 hover:text-neutral-200">
          Cancel
        </button>
        <button
          type="button"
          onClick={handlePreview}
          disabled={busy || !file || accountIds.length === 0}
          className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-mono font-medium rounded-lg transition disabled:opacity-50"
        >
          {busy ? 'Reading...' : 'Preview'}
        </button>
      </div>
    </div>
  );

  const rowTone = (row: ImportPreviewRow) =>
    row.status === 'ERROR' ? 'bg-rose-950/30' : row.status === 'DUPLICATE' ? 'opacity-50' : '';

  const statusBadge = (row: ImportPreviewRow) => {
    const style =
      row.status === 'NEW'
        ? 'bg-emerald-950 text-emerald-400'
        : row.status === 'DUPLICATE'
          ? 'bg-neutral-800 text-neutral-400'
          : 'bg-rose-950 text-rose-400';
    const label = row.status === 'NEW' ? 'New' : row.status === 'DUPLICATE' ? 'Already imported' : 'Error';
    return <span className={`text-[10px] uppercase tracking-wider px-2 py-0.5 rounded-full ${style}`}>{label}</span>;
  };

  // With several accounts a "New" row can still be a duplicate in one of them; say which.
  const accountChips = (row: ImportPreviewRow) =>
    preview && preview.accounts.length > 1 ? (
      <div className="mt-1 flex flex-wrap gap-1">
        {row.accounts.map((c) => (
          <span
            key={c.accountId}
            className={`text-[10px] px-1.5 py-0.5 rounded ${c.status === 'NEW' ? 'bg-emerald-950 text-emerald-400' : 'bg-neutral-800 text-neutral-500'}`}
          >
            {accountName(c.accountId)}: {c.status === 'NEW' ? 'new' : 'skip'}
          </span>
        ))}
      </div>
    ) : null;

  const planButtons = (row: ImportPreviewRow) => {
    const id = row.externalId as string;
    const current = choices[id] ?? null;
    const btn = (value: boolean, label: string, active: string) => (
      <button
        type="button"
        onClick={() => setChoices((prev) => ({ ...prev, [id]: current === value ? null : value }))}
        className={`px-2 py-1 text-[10px] font-mono rounded ${current === value ? active : 'bg-neutral-800 text-neutral-400 hover:text-neutral-200'}`}
      >
        {label}
      </button>
    );
    return (
      <div className="flex gap-1">
        {btn(true, 'Yes', 'bg-emerald-600 text-white')}
        {btn(false, 'No', 'bg-rose-600 text-white')}
      </div>
    );
  };

  const renderPreview = () => {
    if (!preview) return null;
    const { summary } = preview;
    const open = unansweredCount(preview.rows, choices);
    const hasImportable = preview.rows.some(isImportable);
    const accountWarnings = preview.accounts.filter((a) => a.warning);

    return (
      <div className="mt-4 space-y-4">
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs font-mono">
          <span className="text-emerald-400">{summary.ready} ready</span>
          <span className="text-neutral-400">{summary.duplicates} already imported</span>
          <span className={summary.errors > 0 ? 'text-rose-400' : 'text-neutral-500'}>{summary.errors} with errors</span>
          <span className="text-neutral-200 font-medium">
            → {summary.tradesToCreate} trade{summary.tradesToCreate === 1 ? '' : 's'} will be created
            {preview.accounts.length > 1 ? ` across ${preview.accounts.length} accounts` : ` in ${preview.accounts[0]?.name}`}
          </span>
        </div>

        {accountWarnings.map((a) => (
          <div key={a.id} className="p-3 bg-amber-950/40 border border-amber-800 rounded-lg text-amber-300 text-xs font-mono">
            {a.name}: {a.warning}
          </div>
        ))}

        {hasImportable && (
          <div className="flex flex-wrap items-center gap-2 text-xs font-mono text-neutral-400">
            <span>Did you follow your plan? Set all:</span>
            <button
              type="button"
              onClick={() => setChoices(answerAll(preview.rows, true))}
              className="px-2 py-1 rounded bg-neutral-800 hover:bg-neutral-700 text-neutral-200"
            >
              Yes
            </button>
            <button
              type="button"
              onClick={() => setChoices(answerAll(preview.rows, false))}
              className="px-2 py-1 rounded bg-neutral-800 hover:bg-neutral-700 text-neutral-200"
            >
              No
            </button>
            <button
              type="button"
              onClick={() => setChoices(answerAll(preview.rows, null))}
              className="px-2 py-1 rounded bg-neutral-800 hover:bg-neutral-700 text-neutral-200"
            >
              Clear
            </button>
            <span className="text-neutral-500">
              {open === 0 ? 'All answered.' : `${open} left blank will be saved as "not specified".`}
            </span>
          </div>
        )}

        <div className="max-h-[45vh] overflow-auto border border-neutral-800 rounded-lg">
          <table className="w-full text-xs font-mono text-neutral-300">
            <thead className="sticky top-0 bg-neutral-900 text-neutral-500 text-left">
              <tr>
                <th className="px-3 py-2 font-normal">Line</th>
                <th className="px-3 py-2 font-normal">Opened (Pacific)</th>
                <th className="px-3 py-2 font-normal">Trade</th>
                <th className="px-3 py-2 font-normal text-right">Entry → Exit</th>
                <th className="px-3 py-2 font-normal text-right">P&amp;L</th>
                <th className="px-3 py-2 font-normal">Status</th>
                <th className="px-3 py-2 font-normal">Followed plan?</th>
              </tr>
            </thead>
            <tbody>
              {preview.rows.map((row) => (
                <tr key={row.line} className={`border-t border-neutral-800 align-top ${rowTone(row)}`}>
                  <td className="px-3 py-2 text-neutral-500">{row.line}</td>
                  {row.trade ? (
                    <>
                      <td className="px-3 py-2 whitespace-nowrap">{formatWhen(row.trade.tradeDate)}</td>
                      <td className="px-3 py-2 whitespace-nowrap">
                        {row.trade.ticker}{' '}
                        <span className={row.trade.direction === 'LONG' ? 'text-emerald-400' : 'text-rose-400'}>
                          {row.trade.direction}
                        </span>{' '}
                        ×{row.trade.contracts}
                      </td>
                      <td className="px-3 py-2 text-right whitespace-nowrap">
                        {row.trade.entryPrice} → {row.trade.exitPrice}
                      </td>
                      <td className={`px-3 py-2 text-right whitespace-nowrap ${pnlColor(row.trade.profitLoss)}`}>
                        {formatUsd(row.trade.profitLoss)}
                      </td>
                    </>
                  ) : (
                    <td colSpan={4} className="px-3 py-2 text-rose-300">
                      {row.errors.join(' ')}
                    </td>
                  )}
                  <td className="px-3 py-2">
                    {statusBadge(row)}
                    {accountChips(row)}
                    {row.warnings.map((w, i) => (
                      <p key={i} className="mt-1 text-[10px] text-amber-400 max-w-[16rem] whitespace-normal">
                        {w}
                      </p>
                    ))}
                  </td>
                  <td className="px-3 py-2">{isImportable(row) ? planButtons(row) : null}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="flex justify-between items-center pt-3 border-t border-neutral-800">
          <button type="button" onClick={backToSelect} disabled={busy} className="px-4 py-2 text-xs font-mono text-neutral-400 hover:text-neutral-200">
            ← Back
          </button>
          <button
            type="button"
            onClick={handleConfirm}
            disabled={busy || summary.tradesToCreate === 0}
            className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-mono font-medium rounded-lg transition disabled:opacity-50"
          >
            {busy
              ? 'Importing...'
              : summary.tradesToCreate === 0
                ? 'Nothing new to import'
                : `Import ${summary.tradesToCreate} trade${summary.tradesToCreate === 1 ? '' : 's'}`}
          </button>
        </div>
      </div>
    );
  };

  const renderDone = () => {
    if (!result) return null;
    return (
      <div className="mt-4 space-y-4">
        <div className="grid grid-cols-3 gap-3 text-center">
          <div className="bg-neutral-950 border border-neutral-800 rounded-lg py-3">
            <p className="text-2xl font-bold text-emerald-400 font-mono">{result.created}</p>
            <p className="text-[10px] uppercase tracking-wider text-neutral-500 font-mono">Created</p>
          </div>
          <div className="bg-neutral-950 border border-neutral-800 rounded-lg py-3">
            <p className="text-2xl font-bold text-neutral-300 font-mono">{result.skipped}</p>
            <p className="text-[10px] uppercase tracking-wider text-neutral-500 font-mono">Skipped (already there)</p>
          </div>
          <div className="bg-neutral-950 border border-neutral-800 rounded-lg py-3">
            <p className={`text-2xl font-bold font-mono ${result.failed > 0 ? 'text-rose-400' : 'text-neutral-300'}`}>
              {result.failed}
            </p>
            <p className="text-[10px] uppercase tracking-wider text-neutral-500 font-mono">Failed</p>
          </div>
        </div>

        <div className="space-y-2">
          {result.accounts.map((a) => (
            <div key={a.id} className="bg-neutral-950 border border-neutral-800 rounded-lg px-3 py-2 text-xs font-mono text-neutral-300">
              <p className="text-neutral-200">
                {a.name}: {a.created} created, {a.skipped} skipped
              </p>
              <p className="text-neutral-500">
                Balance {formatMoney(a.currentBalance)} · drawdown floor {formatMoney(a.drawdownFloor)}
              </p>
              {a.suggestedStatus === 'FAILED' && (
                <p className="mt-1 text-rose-400">
                  These trades put this account at its drawdown floor{a.breachedOn ? ` (${a.breachedOn})` : ''}. Nothing was
                  changed; review the account on the dashboard.
                </p>
              )}
              {a.suggestedStatus === 'PASSED' && (
                <p className="mt-1 text-emerald-400">
                  These trades reach the profit target{a.targetReachedOn ? ` (${a.targetReachedOn})` : ''}. Review the account
                  on the dashboard to mark it passed.
                </p>
              )}
            </div>
          ))}
        </div>

        {result.errors.length > 0 && (
          <div className="p-3 bg-rose-950/40 border border-rose-800 rounded-lg text-xs font-mono text-rose-300 space-y-1">
            <p className="text-rose-200">Rows that were not imported:</p>
            {result.errors.map((e) => (
              <p key={e.line}>
                Line {e.line}: {e.message}
              </p>
            ))}
          </div>
        )}

        <div className="flex justify-end pt-3 border-t border-neutral-800">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-mono font-medium rounded-lg transition"
          >
            Done
          </button>
        </div>
      </div>
    );
  };

  return (
    // Same dialog shell and click-outside-to-close as CreateAccountModal, except a click outside is ignored
    // while a request is in flight (closing mid-import would hide whether it worked).
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-xs p-4"
      onClick={() => !busy && onClose()}
    >
      <div
        className={`bg-neutral-900 border border-neutral-800 rounded-xl w-full max-h-[90vh] overflow-y-auto p-6 shadow-2xl ${
          step === 'preview' ? 'max-w-5xl' : 'max-w-lg'
        }`}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between pb-3 border-b border-neutral-800">
          <h2 className="text-base font-bold text-white font-mono">
            Import Trades
            <span className="ml-2 text-xs font-normal text-neutral-500">
              {step === 'select' ? '1 of 3 · choose file' : step === 'preview' ? '2 of 3 · review' : '3 of 3 · done'}
            </span>
          </h2>
          <button
            onClick={onClose}
            disabled={busy}
            className="text-neutral-400 hover:text-neutral-200 transition text-sm font-mono disabled:opacity-50"
          >
            ✕
          </button>
        </div>

        {error && (
          <div className="mt-4 p-3 bg-rose-950/60 border border-rose-800 rounded-lg text-rose-300 text-xs font-mono">
            {error}
          </div>
        )}

        {step === 'select' && renderSelect()}
        {step === 'preview' && renderPreview()}
        {step === 'done' && renderDone()}
      </div>
    </div>
  );
};
