import React, { useState } from 'react';
import { format } from 'date-fns';
import type { Trade } from '../../types/Trade';
import { useAttachments } from '../../hooks/useAttachments';
import { AttachmentDropzone } from '../attachments/AttachmentDropzone';
import { AttachmentThumbnail } from '../attachments/AttachmentThumbnail';
import { formatUsd } from '../../utils/formatters';

interface JournalEntryCardProps {
  trade: Trade;
}

// Unlike TradeEntryModal, the trade here always already exists (it's being
// reviewed, not created), so uploads can go straight through useAttachments
// with no staging step - there's no chicken-and-egg tradeId problem.
export const JournalEntryCard: React.FC<JournalEntryCardProps> = ({ trade }) => {
  const { attachments, loading, error, upload, remove } = useAttachments(trade.id);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);

  const handleFilesSelected = async (files: File[]) => {
    setUploading(true);
    setUploadError(null);
    const results = await Promise.allSettled(files.map((file) => upload(file)));
    const failedCount = results.filter((r) => r.status === 'rejected').length;
    if (failedCount > 0) {
      setUploadError(`${failedCount} of ${files.length} screenshot(s) failed to upload.`);
    }
    setUploading(false);
  };

  return (
    <div className="bg-white p-6 rounded-2xl border border-gray-100 shadow-xs space-y-4">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-sm font-black text-gray-900">
            {trade.ticker} <span className="text-gray-400 font-semibold">· {trade.direction}</span>
          </p>
          <p className="text-xs text-gray-400 font-semibold mt-0.5">
            {format(new Date(trade.tradeDate), 'EEEE, MMMM d, yyyy · h:mm a')}
          </p>
        </div>
        <p className={`text-lg font-black ${trade.profitLoss >= 0 ? 'text-emerald-500' : 'text-red-500'}`}>
          {formatUsd(trade.profitLoss)}
        </p>
      </div>

      {trade.notes && <p className="text-xs text-gray-600 border-t border-gray-100 pt-3">{trade.notes}</p>}

      <div className="border-t border-gray-100 pt-3">
        <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-2">Chart Screenshots</p>

        {loading && <p className="text-xs text-gray-400 font-semibold">Loading screenshots...</p>}
        {error && <p className="text-xs text-red-500 font-semibold">{error}</p>}

        {attachments.length > 0 && (
          <div className="flex flex-wrap gap-2 mb-3">
            {attachments.map((attachment) => (
              <AttachmentThumbnail key={attachment.id} attachment={attachment} onRemove={remove} />
            ))}
          </div>
        )}

        <AttachmentDropzone onFilesSelected={handleFilesSelected} disabled={uploading} />
        {uploadError && <p className="mt-2 text-xs text-red-500 font-semibold">{uploadError}</p>}
      </div>
    </div>
  );
};
