import { useCallback, useEffect, useRef, useState } from 'react';
import apiClient from '../api/apiClient';
import type { Attachment } from '../types/Attachment';

interface UseAttachmentsResult {
  attachments: Attachment[];
  loading: boolean;
  error: string | null;
  refetch: () => Promise<void>;
  upload: (file: File, caption?: string) => Promise<Attachment>;
  remove: (attachmentId: number) => Promise<void>;
}

function extractMessage(err: unknown): string {
  const axiosErr = err as { response?: { data?: { message?: string } }; message?: string };
  return axiosErr?.response?.data?.message ?? axiosErr?.message ?? 'Failed to load attachments';
}

// tradeId is nullable so TradeEntryModal - which stages screenshots before a
// trade exists yet - can call this hook harmlessly with no id: load() no-ops
// to an empty list, and upload()/remove() throw rather than silently doing
// nothing, since calling them with no trade is a real bug, not a valid state.
export function useAttachments(tradeId: number | null | undefined): UseAttachmentsResult {
  const [attachments, setAttachments] = useState<Attachment[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const isMountedRef = useRef(true);

  const load = useCallback(async () => {
    if (!tradeId) {
      setAttachments([]);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<Attachment[]>(`/trades/${tradeId}/attachments`);
      if (isMountedRef.current) setAttachments(res.data);
    } catch (err) {
      if (isMountedRef.current) setError(extractMessage(err));
    } finally {
      if (isMountedRef.current) setLoading(false);
    }
  }, [tradeId]);

  const upload = useCallback(
    async (file: File, caption?: string): Promise<Attachment> => {
      if (!tradeId) {
        throw new Error('Cannot upload an attachment before the trade has been saved.');
      }
      const formData = new FormData();
      formData.append('file', file);
      if (caption) formData.append('caption', caption);

      // Overrides apiClient's default 'application/json' header. Left in
      // place, axios would send the FormData body with the wrong Content-Type
      // (missing the multipart boundary), and the server couldn't parse it -
      // setting it to undefined lets the browser generate the correct one.
      const res = await apiClient.post<Attachment>(`/trades/${tradeId}/attachments`, formData, {
        headers: { 'Content-Type': undefined },
      });
      setAttachments((prev) => [...prev, res.data]);
      return res.data;
    },
    [tradeId],
  );

  const remove = useCallback(async (attachmentId: number) => {
    await apiClient.delete(`/attachments/${attachmentId}`);
    setAttachments((prev) => prev.filter((a) => a.id !== attachmentId));
  }, []);

  useEffect(() => {
    isMountedRef.current = true;
    load();
    return () => {
      isMountedRef.current = false;
    };
  }, [load]);

  return { attachments, loading, error, refetch: load, upload, remove };
}
