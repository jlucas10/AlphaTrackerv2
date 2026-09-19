import React, { useCallback, useRef, useState } from 'react';
import { ALLOWED_ATTACHMENT_CONTENT_TYPES, MAX_ATTACHMENT_SIZE_BYTES } from '../../types/Attachment';

interface AttachmentDropzoneProps {
  onFilesSelected: (files: File[]) => void;
  disabled?: boolean;
}

function validate(file: File): string | null {
  if (!(ALLOWED_ATTACHMENT_CONTENT_TYPES as readonly string[]).includes(file.type)) {
    return `${file.name}: unsupported file type (${file.type || 'unknown'}).`;
  }
  if (file.size > MAX_ATTACHMENT_SIZE_BYTES) {
    return `${file.name}: exceeds the 10MB upload limit.`;
  }
  return null;
}

// Presentational only - it never calls the upload API itself. TradeEntryModal
// stages files locally and uploads them after the trade is created (there's
// no tradeId yet during creation); the Journal view uploads immediately since
// its trade already exists. Keeping this component upload-agnostic lets both
// screens reuse it without forcing one flow onto the other.
export const AttachmentDropzone: React.FC<AttachmentDropzoneProps> = ({ onFilesSelected, disabled }) => {
  const [isDragging, setIsDragging] = useState(false);
  const [validationError, setValidationError] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const acceptFiles = useCallback(
    (fileList: FileList | File[]) => {
      const files = Array.from(fileList);
      if (files.length === 0) return;

      const firstError = files.map(validate).find((e) => e !== null);
      if (firstError) {
        setValidationError(firstError);
        return;
      }
      setValidationError(null);
      onFilesSelected(files);
    },
    [onFilesSelected],
  );

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(false);
    if (disabled) return;
    if (e.dataTransfer.files?.length) acceptFiles(e.dataTransfer.files);
  };

  // Clipboard paste (Cmd+V) - the most common path for a screenshot tool.
  // Scoped to this element via onPaste rather than a window listener, so
  // pasting into an unrelated text field elsewhere on the page never triggers it.
  const handlePaste = (e: React.ClipboardEvent<HTMLDivElement>) => {
    if (disabled) return;
    const files = Array.from(e.clipboardData.items)
      .filter((item) => item.kind === 'file')
      .map((item) => item.getAsFile())
      .filter((f): f is File => f !== null);
    if (files.length > 0) acceptFiles(files);
  };

  return (
    <div>
      <div
        tabIndex={0}
        onDragOver={(e) => {
          e.preventDefault();
          if (!disabled) setIsDragging(true);
        }}
        onDragLeave={() => setIsDragging(false)}
        onDrop={handleDrop}
        onPaste={handlePaste}
        onClick={() => !disabled && inputRef.current?.click()}
        className={`cursor-pointer border-2 border-dashed rounded-lg px-4 py-6 text-center transition ${
          isDragging ? 'border-emerald-500 bg-emerald-950/20' : 'border-neutral-800 bg-neutral-950'
        } ${disabled ? 'opacity-50 cursor-not-allowed' : ''}`}
      >
        <p className="text-xs font-mono text-neutral-400">
          Drag & drop a screenshot, paste (Cmd+V), or click to browse
        </p>
        <p className="text-[10px] font-mono text-neutral-600 mt-1">PNG, JPEG, WEBP, GIF — up to 10MB</p>
        <input
          ref={inputRef}
          type="file"
          accept={ALLOWED_ATTACHMENT_CONTENT_TYPES.join(',')}
          multiple
          className="hidden"
          disabled={disabled}
          onChange={(e) => {
            if (e.target.files?.length) acceptFiles(e.target.files);
            e.target.value = ''; // allow re-selecting the same file later
          }}
        />
      </div>
      {validationError && <p className="mt-2 text-xs font-mono text-rose-400">{validationError}</p>}
    </div>
  );
};
