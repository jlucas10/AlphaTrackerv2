import apiClient from './apiClient';
import type { ImportConfirmResult, ImportPreview, ImportSource } from '../types/Import';

// Both endpoints take the SAME multipart upload (file + source + accountIds). Confirm re-reads the file on
// the server instead of trusting the preview, so the modal keeps the File and sends it again.
function buildForm(file: File, source: ImportSource, accountIds: number[]): FormData {
  const form = new FormData();
  form.append('file', file);
  form.append('source', source);
  // Repeated param, which Spring binds to a List<Long>.
  accountIds.forEach((id) => form.append('accountIds', String(id)));
  return form;
}

// 'Content-Type': undefined lets the browser write the multipart boundary itself; apiClient's default
// 'application/json' would break the upload (same reason as uploadAttachment in attachments.ts).
const multipart = { headers: { 'Content-Type': undefined } };

// Writes nothing: parse, validate, and say what WOULD happen.
export async function previewImport(file: File, source: ImportSource, accountIds: number[]): Promise<ImportPreview> {
  const res = await apiClient.post<ImportPreview>('/imports/preview', buildForm(file, source, accountIds), multipart);
  return res.data;
}

export interface PlanAnswers {
  yes: string[];   // broker trade ids the trader says followed their plan
  no: string[];    // ... and did not
}

// Writes the new trades. Any trade in neither list is saved with followedPlan unspecified (null).
export async function confirmImport(
  file: File,
  source: ImportSource,
  accountIds: number[],
  answers: PlanAnswers,
): Promise<ImportConfirmResult> {
  const form = buildForm(file, source, accountIds);
  answers.yes.forEach((id) => form.append('followedPlanYes', id));
  answers.no.forEach((id) => form.append('followedPlanNo', id));
  const res = await apiClient.post<ImportConfirmResult>('/imports/confirm', form, multipart);
  return res.data;
}
