import { useCallback, useEffect, useRef, useState } from 'react';
import { deleteAttachment as deleteAttachmentRequest, getAttachmentsForTrade, uploadAttachment as uploadAttachmentRequest } from '../api/attachments';
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
      const data = await getAttachmentsForTrade(tradeId);
      if (isMountedRef.current) setAttachments(data);
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
      const attachment = await uploadAttachmentRequest(tradeId, file, caption);
      setAttachments((prev) => [...prev, attachment]);
      return attachment;
    },
    [tradeId],
  );

  const remove = useCallback(async (attachmentId: number) => {
    await deleteAttachmentRequest(attachmentId);
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
