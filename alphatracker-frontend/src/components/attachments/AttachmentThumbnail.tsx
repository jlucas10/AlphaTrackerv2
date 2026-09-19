import React, { useEffect, useState } from 'react';
import apiClient from '../../api/apiClient';
import type { Attachment } from '../../types/Attachment';

interface AttachmentThumbnailProps {
  attachment: Attachment;
  onRemove?: (id: number) => void;
}

// The retrieval endpoint requires a JWT and re-checks ownership on every call
// (see TradeAttachmentController), but a plain <img src="..."> can't carry an
// Authorization header. So the bytes are fetched through apiClient (which
// does attach it) as a blob, and an object URL is created for the <img> tag
// to point at instead - this is the standard workaround for auth-gated images.
export const AttachmentThumbnail: React.FC<AttachmentThumbnailProps> = ({ attachment, onRemove }) => {
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    let localUrl: string | null = null;

    apiClient
      .get(`/attachments/${attachment.id}/file`, { responseType: 'blob' })
      .then((res) => {
        if (cancelled) return;
        localUrl = URL.createObjectURL(res.data);
        setObjectUrl(localUrl);
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      });

    return () => {
      cancelled = true;
      // Object URLs are never garbage collected automatically - skipping this
      // leaks memory every time a thumbnail unmounts (e.g. closing a modal).
      if (localUrl) URL.revokeObjectURL(localUrl);
    };
  }, [attachment.id]);

  return (
    <div className="relative group w-20 h-20 rounded-lg overflow-hidden border border-neutral-800 bg-neutral-950 flex items-center justify-center shrink-0">
      {failed && <span className="text-[10px] text-rose-400 font-mono">Failed</span>}
      {!failed && !objectUrl && <span className="text-[10px] text-neutral-500 font-mono">...</span>}
      {objectUrl && (
        <img
          src={objectUrl}
          alt={attachment.caption ?? 'Trade screenshot'}
          className="w-full h-full object-cover"
        />
      )}
      {onRemove && (
        <button
          type="button"
          onClick={() => onRemove(attachment.id)}
          className="absolute top-0.5 right-0.5 hidden group-hover:flex items-center justify-center w-5 h-5 rounded-full bg-black/70 text-white text-xs"
        >
          ✕
        </button>
      )}
    </div>
  );
};
