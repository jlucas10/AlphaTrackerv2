import React, { useEffect, useState } from 'react';

interface PendingAttachmentThumbnailProps {
  file: File;
  onRemove: () => void;
}

// For a staged File that hasn't been uploaded yet (TradeEntryModal, where no
// tradeId exists until the trade is saved). Unlike AttachmentThumbnail, this
// never touches the network - URL.createObjectURL(file) works directly on a
// local File with no auth involved.
export const PendingAttachmentThumbnail: React.FC<PendingAttachmentThumbnailProps> = ({ file, onRemove }) => {
  const [objectUrl, setObjectUrl] = useState<string | null>(null);

  useEffect(() => {
    const url = URL.createObjectURL(file);
    setObjectUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  return (
    <div className="relative group w-20 h-20 rounded-lg overflow-hidden border border-dashed border-emerald-700 bg-neutral-950 flex items-center justify-center shrink-0">
      {objectUrl && <img src={objectUrl} alt={file.name} className="w-full h-full object-cover" />}
      <button
        type="button"
        onClick={onRemove}
        className="absolute top-0.5 right-0.5 hidden group-hover:flex items-center justify-center w-5 h-5 rounded-full bg-black/70 text-white text-xs"
      >
        ✕
      </button>
    </div>
  );
};
