import React, { useEffect } from 'react';
import type { Attachment } from '../../types/Attachment';

interface AttachmentLightboxProps {
  attachment: Attachment;
  objectUrl: string;
  onClose: () => void;
}

// Full-size view of a screenshot already fetched by AttachmentThumbnail - no
// second network request, just reuses the same object URL at full size.
export const AttachmentLightbox: React.FC<AttachmentLightboxProps> = ({ attachment, objectUrl, onClose }) => {
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-[60] flex items-center justify-center bg-black/85 p-4"
      onClick={onClose}
    >
      <button
        type="button"
        onClick={onClose}
        className="absolute top-4 right-4 text-white/70 hover:text-white text-2xl leading-none"
        aria-label="Close"
      >
        ✕
      </button>
      <div className="max-w-4xl max-h-full flex flex-col items-center gap-3" onClick={(e) => e.stopPropagation()}>
        <img
          src={objectUrl}
          alt={attachment.caption ?? 'Trade screenshot'}
          className="max-w-full max-h-[80vh] rounded-lg object-contain"
        />
        {attachment.caption && <p className="text-sm text-white/80 text-center">{attachment.caption}</p>}
      </div>
    </div>
  );
};
