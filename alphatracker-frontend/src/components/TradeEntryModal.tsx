import React, { useRef, useState } from "react";
import apiClient from '../api/apiClient';
import { deleteAttachment, uploadAttachment } from '../api/attachments';
import { usePasteScreenshot } from '../hooks/usePasteScreenshot';
import type { Account } from '../types/Account';
import type { Attachment } from '../types/Attachment';
import { AttachmentDropzone, type AttachmentDropzoneHandle } from './attachments/AttachmentDropzone';
import { AttachmentThumbnail } from './attachments/AttachmentThumbnail';

// Define imports and component interface
interface TradeEntryModalProps {
    isOpen: boolean;
    onClose: () => void;
    // Returns a promise when the parent refetches, so submit can await it.
    onTradeAdded: () => void | Promise<void>;
    accounts?: Account[];
    defaultAccountId?: number | null;

}

// Mirrors the backend Instrument enum. Presented as a dropdown rather than a
// free-text field so an unsupported ticker is impossible to submit — the server
// rejects unknown tickers outright, and this keeps that error off the screen.
const INSTRUMENTS = [
    'ES', 'MES', 'NQ', 'MNQ', 'YM', 'MYM', 'RTY', 'M2K', 'CL', 'MCL', 'GC', 'MGC',
] as const;


// datetime-local wants "YYYY-MM-DDTHH:mm" in LOCAL time. toISOString() would
// convert to UTC and could file a late-evening trade under the following day on
// the calendar matrix, so the local parts are formatted by hand.
function nowForInput(): string {
    const d = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

// Initialize form and UI state
export const TradeEntryModal: React.FC<TradeEntryModalProps> = ({
    isOpen,
    onClose,
    onTradeAdded,
    accounts = [],
    defaultAccountId = null,
}) => {
    // Form state fields aligned with the backend TradeRequest DTO.
    const [accountId, setAccountId] = useState<number | undefined>(
      defaultAccountId ?? (accounts.length > 0 ? accounts[0].id : undefined)
    );
    // Commission is deliberately absent: the server derives it from the ticker's
    // round-turn fee, so there is nothing here for the trader to mistype.
    const [ticker, setTicker] = useState<string>('MNQ');
    const [direction, setDirection] = useState<'LONG' | 'SHORT'>('LONG');
    const [entryPrice, setEntryPrice] = useState<string>('');
    const [exitPrice, setExitPrice] = useState<string>('');
    const [contracts, setContracts] = useState<string>('1');
    const [tradeDate, setTradeDate] = useState<string>(nowForInput);
    const [followedPlan, setFollowedPlan] = useState<boolean>(true);
    const [notes, setNotes] = useState<string>('');

    // Screenshots upload immediately against the trade's date (journal
    // attachments are day-scoped, not tied to a specific trade - see Sprint
    // 3.5 in CONTEXT.md), so there's no need to wait for the trade to be
    // saved or stage anything locally.
    const [attachments, setAttachments] = useState<Attachment[]>([]);
    const [uploadingCount, setUploadingCount] = useState<number>(0);
    const [uploadError, setUploadError] = useState<string>('');

    const [loading, setLoading] = useState<boolean>(false);
    const [error, setError] = useState<string>('');

    const tradeDateOnly = tradeDate.slice(0, 10); // "YYYY-MM-DDTHH:mm" -> "YYYY-MM-DD"

    const handleFilesSelected = async (files: File[]) => {
        setUploadError('');
        setUploadingCount((c) => c + files.length);
        const results = await Promise.allSettled(files.map((file) => uploadAttachment(tradeDateOnly, file)));
        setUploadingCount((c) => c - files.length);

        const succeeded = results.filter((r): r is PromiseFulfilledResult<Attachment> => r.status === 'fulfilled');
        setAttachments((prev) => [...prev, ...succeeded.map((r) => r.value)]);

        const failedCount = results.length - succeeded.length;
        if (failedCount > 0) {
            setUploadError(`${failedCount} of ${files.length} screenshot(s) failed to upload.`);
        }
    };

    const handleRemoveAttachment = async (id: number) => {
        await deleteAttachment(id);
        setAttachments((prev) => prev.filter((a) => a.id !== id));
    };

    // Lets Cmd+V paste a screenshot anywhere in the open modal (not just while
    // the small dropzone box has focus) - see usePasteScreenshot. Has to run
    // unconditionally (before the `if (!isOpen) return null` below) since it's
    // a hook, but the `enabled: isOpen` argument keeps it inert while closed.
    const dropzoneRef = useRef<AttachmentDropzoneHandle>(null);
    usePasteScreenshot(dropzoneRef, isOpen);

    // The modal stays mounted while closed (the early return below renders null),
    // so state has to be cleared explicitly or the next open shows stale values.
    const resetForm = () => {
        setAccountId(defaultAccountId ?? (accounts.length > 0 ? accounts[0].id : undefined));
        setTicker('MNQ');
        setDirection('LONG');
        setEntryPrice('');
        setExitPrice('');
        setContracts('1');
        setTradeDate(nowForInput());
        setFollowedPlan(true);
        setNotes('');
        setAttachments([]);
        setUploadError('');
        setError('');
    };

    const handleClose = () => {
        resetForm();
        onClose();
    };

    if (!isOpen) return null;

    const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    // Keys match TradeRequest exactly. No commission and no profitLoss: both are
    // derived server-side from the instrument's point value and round-turn fee.
    const payload = {
      ticker,
      direction,
      entryPrice: parseFloat(entryPrice),
      exitPrice: parseFloat(exitPrice),
      contracts: parseInt(contracts, 10),
      followedPlan,
      notes,
      // "2026-08-14T09:30" — parses this straight into LocalDateTime.
      tradeDate,
      accountId: accountId || undefined, // Attach accountId to execution
    };

    try {
      await apiClient.post('/trades', payload);
      // Await the parent's refetch so the calendar and equity curve already show
      // this trade by the time the modal disappears, rather than briefly showing
      // stale totals.
      await onTradeAdded();
      setLoading(false);
      resetForm();
      onClose();
    } catch (err: any) {
      console.error(err);
      setLoading(false);
      setError(
        err.response?.data?.message || 'Failed to log trade. Check backend connectivity.'
      );
    }
  };

  // Matches CreateAccountModal's dark surface so the two data-entry dialogs
  // read as one consistent "entry form" style, distinct from the light
  // dashboard cards behind them.
  return (
        // Clicking the backdrop closes (via handleClose, same as the ✕ button
        // and Cancel, so the form resets either way); stopPropagation on the
        // card keeps a click inside the dialog from bubbling up and closing it.
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-xs p-4"
            onClick={handleClose}
        >
            <div
                className="w-full max-w-lg bg-neutral-900 border border-neutral-800 rounded-xl p-6 shadow-2xl"
                onClick={(e) => e.stopPropagation()}
            >
                <div className="flex items-center justify-between pb-3 border-b border-neutral-800">
                    <h2 className="text-base font-bold text-white font-mono">Log Execution</h2>
                    <button
                        onClick={handleClose}
                        type="button"
                        className="text-neutral-400 hover:text-neutral-200 transition text-xl font-mono"
                    >
                        ✕
                    </button>
                </div>

                {error && (
                    <div className="mt-4 p-3 bg-rose-950/60 border border-rose-800 rounded-lg text-rose-300 text-xs font-mono">
                        {error}
                    </div>
                )}

                <form onSubmit={handleSubmit} className="mt-4 space-y-4">
                    {/* Prop Account Selector (shown when accounts exist) */}
                    {accounts.length > 0 && (
                        <div>
                            <label className="block text-xs font-mono text-neutral-400 mb-1">
                                Trading Account
                            </label>
                            <select
                                value={accountId ?? ''}
                                onChange={(e) => setAccountId(e.target.value ? Number(e.target.value) : undefined)}
                                className="w-full bg-neutral-950 border border-neutral-800 rounded-lg px-3 py-2 text-sm text-neutral-200 font-mono focus:outline-hidden focus:border-emerald-500"
                            >
                                <option value="">None (Unassigned)</option>
                                {accounts.map((acc) => (
                                    <option key={acc.id} value={acc.id}>
                                        {acc.name} — {acc.firm} ({acc.accountType})
                                    </option>
                                ))}
                            </select>
                        </div>
                    )}

                    {/* Ticker & Direction */}
                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-xs font-mono text-neutral-400 mb-1">
                                Instrument
                            </label>
                            <select
                                value={ticker}
                                onChange={(e) => setTicker(e.target.value)}
                                className="w-full bg-neutral-950 border border-neutral-800 rounded-lg px-3 py-2 text-sm text-neutral-200 font-mono focus:outline-hidden focus:border-emerald-500"
                            >
                                {INSTRUMENTS.map((code) => (
                                    <option key={code} value={code}>
                                        {code}
                                    </option>
                                ))}
                            </select>
                        </div>

                        <div>
                            <label className="block text-xs font-mono text-neutral-400 mb-1">
                                Direction
                            </label>
                            <select
                                value={direction}
                                onChange={(e) => setDirection(e.target.value as 'LONG' | 'SHORT')}
                                className="w-full bg-neutral-950 border border-neutral-800 rounded-lg px-3 py-2 text-sm text-neutral-200 font-mono focus:outline-hidden focus:border-emerald-500"
                            >
                                <option value="LONG">LONG 📈</option>
                                <option value="SHORT">SHORT 📉</option>
                            </select>
                        </div>
                    </div>

                    {/* Entry & Exit Prices */}
                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-xs font-mono text-neutral-400 mb-1">
                                Entry Price
                            </label>
                            <input
                                type="number"
                                step="any"
                                required
                                value={entryPrice}
                                onChange={(e) => setEntryPrice(e.target.value)}
                                placeholder="20150.25"
                                className="w-full bg-neutral-950 border border-neutral-800 rounded-lg px-3 py-2 text-sm text-neutral-200 font-mono focus:outline-hidden focus:border-emerald-500"
                            />
                        </div>

                        <div>
                            <label className="block text-xs font-mono text-neutral-400 mb-1">
                                Exit Price
                            </label>
                            <input
                                type="number"
                                step="any"
                                required
                                value={exitPrice}
                                onChange={(e) => setExitPrice(e.target.value)}
                                placeholder="20185.00"
                                className="w-full bg-neutral-950 border border-neutral-800 rounded-lg px-3 py-2 text-sm text-neutral-200 font-mono focus:outline-hidden focus:border-emerald-500"
                            />
                        </div>
                    </div>

                    {/* Contracts & Timestamp */}
                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-xs font-mono text-neutral-400 mb-1">
                                Contract Size
                            </label>
                            <input
                                type="number"
                                min="1"
                                required
                                value={contracts}
                                onChange={(e) => setContracts(e.target.value)}
                                className="w-full bg-neutral-950 border border-neutral-800 rounded-lg px-3 py-2 text-sm text-neutral-200 font-mono focus:outline-hidden focus:border-emerald-500"
                            />
                        </div>

                        <div>
                            <label className="block text-xs font-mono text-neutral-400 mb-1">
                                Date & Time
                            </label>
                            <input
                                type="datetime-local"
                                required
                                value={tradeDate}
                                onChange={(e) => setTradeDate(e.target.value)}
                                className="w-full bg-neutral-950 border border-neutral-800 rounded-lg px-3 py-2 text-sm text-neutral-200 font-mono focus:outline-hidden focus:border-emerald-500"
                            />
                        </div>
                    </div>

                    {/* Discipline Flag */}
                    <div className="flex items-center gap-3 pt-2">
                        <input
                            type="checkbox"
                            id="followedPlan"
                            checked={followedPlan}
                            onChange={(e) => setFollowedPlan(e.target.checked)}
                            className="h-4 w-4 rounded border-neutral-700 bg-neutral-950 text-emerald-600 focus:ring-0"
                        />
                        <label htmlFor="followedPlan" className="text-xs font-mono text-neutral-400">
                            Followed Trading Rules & Risk Plan
                        </label>
                    </div>

                    {/* Trade Notes */}
                    <div>
                        <label className="block text-xs font-mono text-neutral-400 mb-1">
                            Execution Notes / Setup Details
                        </label>
                        <textarea
                            rows={3}
                            value={notes}
                            onChange={(e) => setNotes(e.target.value)}
                            placeholder="Key levels, catalyst, entry strategy..."
                            className="w-full bg-neutral-950 border border-neutral-800 rounded-lg px-3 py-2 text-sm text-neutral-200 font-mono focus:outline-hidden focus:border-emerald-500 resize-none"
                        />
                    </div>

                    {/* Screenshots - upload immediately against the trade's date, since
                        journal attachments are day-scoped rather than tied to this
                        specific trade record. */}
                    <div>
                        <label className="block text-xs font-mono text-neutral-400 mb-1">
                            Chart Screenshots
                        </label>
                        <AttachmentDropzone
                            ref={dropzoneRef}
                            disabled={uploadingCount > 0}
                            onFilesSelected={handleFilesSelected}
                        />
                        {uploadingCount > 0 && (
                            <p className="mt-2 text-xs font-mono text-neutral-500">Uploading...</p>
                        )}
                        {uploadError && (
                            <p className="mt-2 text-xs font-mono text-rose-400">{uploadError}</p>
                        )}
                        {attachments.length > 0 && (
                            <div className="mt-3 flex flex-wrap gap-2">
                                {attachments.map((attachment) => (
                                    <AttachmentThumbnail
                                        key={attachment.id}
                                        attachment={attachment}
                                        onRemove={handleRemoveAttachment}
                                    />
                                ))}
                            </div>
                        )}
                    </div>

                    <div className="flex justify-end gap-3 pt-3 border-t border-neutral-800">
                        <button
                            type="button"
                            onClick={handleClose}
                            className="px-4 py-2 text-xs font-mono text-neutral-400 hover:text-neutral-200"
                        >
                            Cancel
                        </button>
                        <button
                            type="submit"
                            disabled={loading}
                            className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-mono font-medium rounded-lg transition disabled:opacity-50"
                        >
                            {loading ? 'Posting...' : 'Save Trade'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
};


