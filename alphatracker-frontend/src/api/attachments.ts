import apiClient from './apiClient';
import type { Attachment } from '../types/Attachment';

// Shared by useAttachments (attachments on an existing trade) and
// TradeEntryModal (which uploads only after trade creation succeeds, since
// there's no tradeId to attach to beforehand) - keeping the FormData/header
// handling in one place means there's exactly one spot that has to know how
// to talk to this endpoint correctly.
export async function uploadAttachment(tradeId: number, file: File, caption?: string): Promise<Attachment> {
  const formData = new FormData();
  formData.append('file', file);
  if (caption) formData.append('caption', caption);

  // Overrides apiClient's default 'application/json' header. Left in place,
  // axios would send the FormData body with the wrong Content-Type (missing
  // the multipart boundary) and the server couldn't parse it - setting it to
  // undefined lets the browser generate the correct one itself.
  const res = await apiClient.post<Attachment>(`/trades/${tradeId}/attachments`, formData, {
    headers: { 'Content-Type': undefined },
  });
  return res.data;
}

export async function getAttachmentsForTrade(tradeId: number): Promise<Attachment[]> {
  const res = await apiClient.get<Attachment[]>(`/trades/${tradeId}/attachments`);
  return res.data;
}

export async function deleteAttachment(attachmentId: number): Promise<void> {
  await apiClient.delete(`/attachments/${attachmentId}`);
}
