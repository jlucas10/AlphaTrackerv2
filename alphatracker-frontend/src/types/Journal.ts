import type { Attachment } from './Attachment';
import type { Trade } from './Trade';

// Mirrors JournalDayResponse - everything the journal day panel needs from
// one GET/PUT /api/v1/journal/{date} call.
export interface JournalDay {
  entryDate: string; // "YYYY-MM-DD"
  notes: string | null;
  htfBias: string | null;
  updatedAt: string | null;
  attachments: Attachment[];
  trades: Trade[];
}
