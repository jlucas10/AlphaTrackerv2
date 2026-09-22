import apiClient from './apiClient';
import type { JournalDay } from '../types/Journal';

export async function getJournalDay(date: string): Promise<JournalDay> {
  const res = await apiClient.get<JournalDay>(`/journal/${date}`);
  return res.data;
}

export async function upsertJournalDay(date: string, notes: string, htfBias: string): Promise<JournalDay> {
  const res = await apiClient.put<JournalDay>(`/journal/${date}`, { notes, htfBias });
  return res.data;
}
