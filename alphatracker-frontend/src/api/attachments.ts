import apiClient from './apiClient';
import type { Attachment } from '../types/Attachment';

// Attachments are day-scoped (Sprint 3.5), not tied to an individual trade -
// `date` is "YYYY-MM-DD". Shared by useJournalDay and TradeEntryModal (which
// uploads against the trade's own date, independent of whether the trade
// itself has been saved yet) so there's exactly one spot that has to know how
// to talk to this endpoint correctly.
export async function uploadAttachment(date: string, file: File, caption?: string): Promise<Attachment> {
  const formData = new FormData();
  formData.append('file', file);
  if (caption) formData.append('caption', caption);

  // Overrides apiClient's default 'application/json' header. Left in place,
  // axios would send the FormData body with the wrong Content-Type (missing
  // the multipart boundary) and the server couldn't parse it - setting it to
  // undefined lets the browser generate the correct one itself.
  const res = await apiClient.post<Attachment>(`/journal/${date}/attachments`, formData, {
    headers: { 'Content-Type': undefined },
  });
  return res.data;
}

export async function getAttachmentsForDate(date: string): Promise<Attachment[]> {
  const res = await apiClient.get<Attachment[]>(`/journal/${date}/attachments`);
  return res.data;
}

export async function deleteAttachment(attachmentId: number): Promise<void> {
  await apiClient.delete(`/journal-attachments/${attachmentId}`);
}
