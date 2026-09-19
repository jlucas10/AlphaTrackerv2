export interface Attachment {
  id: number;
  tradeId: number;
  attachmentType: string;
  contentType: string;
  sizeBytes: number;
  caption: string | null;
  uploadedAt: string;
  // Server-absolute path (e.g. "/api/v1/attachments/10/file"). Not used
  // directly for fetching - apiClient's baseURL already includes "/api/v1",
  // so components build their own request path from `id` instead (see
  // AttachmentThumbnail). Kept here mainly for display/debugging.
  url: string;
}

// Mirrors TradeAttachmentService's server-side rules exactly, so a rejected
// file is caught here with instant feedback instead of round-tripping to the
// server first to learn the same thing.
export const MAX_ATTACHMENT_SIZE_BYTES = 10 * 1024 * 1024;
export const ALLOWED_ATTACHMENT_CONTENT_TYPES = [
  'image/png',
  'image/jpeg',
  'image/webp',
  'image/gif',
] as const;
