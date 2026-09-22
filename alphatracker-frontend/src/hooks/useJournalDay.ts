import { useCallback, useEffect, useRef, useState } from 'react';
import { getJournalDay, upsertJournalDay } from '../api/journal';
import { deleteAttachment, uploadAttachment } from '../api/attachments';
import type { JournalDay } from '../types/Journal';
import type { Attachment } from '../types/Attachment';
import type { Trade } from '../types/Trade';

interface UseJournalDayResult {
  day: JournalDay | null;
  loading: boolean;
  error: string | null;
  refetch: () => Promise<void>;
  saveNotes: (notes: string, htfBias: string) => Promise<void>;
  upload: (file: File, caption?: string) => Promise<Attachment>;
  remove: (attachmentId: number) => Promise<void>;
  // Patches one trade into day.trades after JournalTradeCard's own PATCH
  // succeeds, so the panel reflects the edit without a full bundle refetch.
  updateTradeInDay: (trade: Trade) => void;
}

function extractMessage(err: unknown): string {
  const axiosErr = err as { response?: { data?: { message?: string } }; message?: string };
  return axiosErr?.response?.data?.message ?? axiosErr?.message ?? 'Failed to load journal entry';
}

// One GET /api/v1/journal/{date} bundles notes, HTF bias, attachments, and
// that day's trades - date is nullable so a caller can mount this hook before
// a day is actually selected (e.g. the calendar view before any click).
export function useJournalDay(date: string | null | undefined): UseJournalDayResult {
  const [day, setDay] = useState<JournalDay | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const isMountedRef = useRef(true);

  const load = useCallback(async () => {
    if (!date) {
      setDay(null);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const data = await getJournalDay(date);
      if (isMountedRef.current) setDay(data);
    } catch (err) {
      if (isMountedRef.current) setError(extractMessage(err));
    } finally {
      if (isMountedRef.current) setLoading(false);
    }
  }, [date]);

  const saveNotes = useCallback(
    async (notes: string, htfBias: string) => {
      if (!date) throw new Error('Cannot save journal notes without a date.');
      const updated = await upsertJournalDay(date, notes, htfBias);
      setDay(updated);
    },
    [date],
  );

  const upload = useCallback(
    async (file: File, caption?: string): Promise<Attachment> => {
      if (!date) throw new Error('Cannot upload a screenshot without a date.');
      const attachment = await uploadAttachment(date, file, caption);
      setDay((prev) => (prev ? { ...prev, attachments: [...prev.attachments, attachment] } : prev));
      return attachment;
    },
    [date],
  );

  const remove = useCallback(async (attachmentId: number) => {
    await deleteAttachment(attachmentId);
    setDay((prev) =>
      prev ? { ...prev, attachments: prev.attachments.filter((a) => a.id !== attachmentId) } : prev,
    );
  }, []);

  const updateTradeInDay = useCallback((trade: Trade) => {
    setDay((prev) =>
      prev ? { ...prev, trades: prev.trades.map((t) => (t.id === trade.id ? trade : t)) } : prev,
    );
  }, []);

  useEffect(() => {
    isMountedRef.current = true;
    load();
    return () => {
      isMountedRef.current = false;
    };
  }, [load]);

  return { day, loading, error, refetch: load, saveNotes, upload, remove, updateTradeInDay };
}
