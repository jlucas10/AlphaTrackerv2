import React, { useEffect, useMemo, useState } from 'react';
import { format, parseISO } from 'date-fns';
import { useJournalDay } from '../../hooks/useJournalDay';
import { AttachmentDropzone } from '../attachments/AttachmentDropzone';
import { AttachmentThumbnail } from '../attachments/AttachmentThumbnail';
import { JournalTradeCard } from './JournalTradeCard';

interface JournalDayPanelProps {
  date: string; // "YYYY-MM-DD"
  onClose: () => void;
}

const HTF_BIAS_OPTIONS = ['Bullish', 'Bearish', 'Neutral'] as const;

// The editable counterpart to the dashboard's DayDetailModal (which stays
// read-only stats). Opened by clicking a day on the /journal calendar, or via
// the pencil icon on DayDetailModal deep-linking to /journal?date=....
export const JournalDayPanel: React.FC<JournalDayPanelProps> = ({ date, onClose }) => {
  const { day, loading, error, saveNotes, upload, remove, updateTradeInDay } = useJournalDay(date);

  const [notesDraft, setNotesDraft] = useState('');
  const [biasDraft, setBiasDraft] = useState('');
  const [savingNotes, setSavingNotes] = useState(false);
  const [notesSaved, setNotesSaved] = useState(false);

  const [uploadingCount, setUploadingCount] = useState(0);
  const [uploadError, setUploadError] = useState('');

  // Reset the draft whenever the loaded day changes (including from '' while
  // loading to the real values once the fetch resolves), so typing in one
  // day's notes can't bleed into another after a fast date change.
  useEffect(() => {
    setNotesDraft(day?.notes ?? '');
    setBiasDraft(day?.htfBias ?? '');
  }, [day?.notes, day?.htfBias]);

  const allTagsToday = useMemo(() => {
    const tags = new Set<string>();
    day?.trades.forEach((t) => t.setupTags?.forEach((tag) => tags.add(tag)));
    return Array.from(tags);
  }, [day?.trades]);

  const handleSaveNotes = async () => {
    setSavingNotes(true);
    setNotesSaved(false);
    try {
      await saveNotes(notesDraft, biasDraft);
      setNotesSaved(true);
    } finally {
      setSavingNotes(false);
    }
  };

  const handleFilesSelected = async (files: File[]) => {
    setUploadError('');
    setUploadingCount((c) => c + files.length);
    const results = await Promise.allSettled(files.map((file) => upload(file)));
    setUploadingCount((c) => c - files.length);
    const failedCount = results.filter((r) => r.status === 'rejected').length;
    if (failedCount > 0) {
      setUploadError(`${failedCount} of ${files.length} screenshot(s) failed to upload.`);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4"
      onClick={onClose}
    >
      <div
        className="w-full max-w-2xl max-h-[85vh] overflow-y-auto rounded-2xl bg-white border border-gray-100 shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between p-6 border-b border-gray-100">
          <h3 className="text-sm font-black text-gray-900 uppercase tracking-wider">
            {format(parseISO(date), 'EEEE, MMMM d, yyyy')}
          </h3>
          <button
            onClick={onClose}
            type="button"
            className="text-gray-300 hover:text-gray-900 font-bold text-xl transition-colors"
          >
            ✕
          </button>
        </div>

        <div className="p-6 space-y-6">
          {loading && <p className="text-sm text-gray-400 font-semibold">Loading...</p>}
          {error && <p className="text-sm text-red-500 font-semibold">{error}</p>}

          {!loading && (
            <>
              {/* Day-level reflection */}
              <div className="space-y-3">
                <div>
                  <label className="text-[10px] font-bold text-gray-400 uppercase tracking-wider block mb-1">
                    HTF Bias
                  </label>
                  <div className="flex gap-2">
                    {HTF_BIAS_OPTIONS.map((option) => (
                      <button
                        key={option}
                        type="button"
                        onClick={() => setBiasDraft(option)}
                        className={`px-3 py-1.5 rounded-lg text-xs font-bold transition ${
                          biasDraft === option
                            ? 'bg-slate-900 text-white'
                            : 'bg-gray-50 text-gray-500 hover:bg-gray-100'
                        }`}
                      >
                        {option}
                      </button>
                    ))}
                  </div>
                </div>

                <div>
                  <label className="text-[10px] font-bold text-gray-400 uppercase tracking-wider block mb-1">
                    Session Notes
                  </label>
                  <textarea
                    rows={4}
                    value={notesDraft}
                    onChange={(e) => {
                      setNotesDraft(e.target.value);
                      setNotesSaved(false);
                    }}
                    placeholder="How did the session go? Key levels, what worked, what didn't..."
                    className="w-full bg-gray-50 border border-gray-200 rounded-lg px-3 py-2 text-sm text-gray-700 focus:outline-none focus:border-emerald-400 resize-none"
                  />
                </div>

                <div className="flex items-center gap-3">
                  <button
                    type="button"
                    onClick={handleSaveNotes}
                    disabled={savingNotes}
                    className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold rounded-lg transition disabled:opacity-50"
                  >
                    {savingNotes ? 'Saving...' : 'Save Notes'}
                  </button>
                  {notesSaved && <span className="text-xs font-semibold text-emerald-600">Saved</span>}
                </div>
              </div>

              {/* Screenshots */}
              <div className="border-t border-gray-100 pt-4">
                <label className="text-[10px] font-bold text-gray-400 uppercase tracking-wider block mb-2">
                  Chart Screenshots
                </label>
                {day && day.attachments.length > 0 && (
                  <div className="flex flex-wrap gap-2 mb-3">
                    {day.attachments.map((attachment) => (
                      <AttachmentThumbnail
                        key={attachment.id}
                        attachment={attachment}
                        onRemove={remove}
                        theme="light"
                      />
                    ))}
                  </div>
                )}
                <AttachmentDropzone
                  theme="light"
                  disabled={uploadingCount > 0}
                  onFilesSelected={handleFilesSelected}
                />
                {uploadingCount > 0 && <p className="mt-2 text-xs text-gray-400 font-semibold">Uploading...</p>}
                {uploadError && <p className="mt-2 text-xs text-red-500 font-semibold">{uploadError}</p>}
              </div>

              {/* That day's trades */}
              <div className="border-t border-gray-100 pt-4">
                <label className="text-[10px] font-bold text-gray-400 uppercase tracking-wider block mb-2">
                  Executions ({day?.trades.length ?? 0})
                </label>
                {day && day.trades.length === 0 && (
                  <p className="text-xs text-gray-400 font-semibold">No trades logged this day.</p>
                )}
                <div className="space-y-3">
                  {day?.trades.map((trade) => (
                    <JournalTradeCard
                      key={trade.id}
                      trade={trade}
                      allTagsToday={allTagsToday}
                      onUpdated={updateTradeInDay}
                    />
                  ))}
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
};
