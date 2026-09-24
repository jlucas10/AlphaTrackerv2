import React, { forwardRef, useCallback, useImperativeHandle, useRef, useState } from 'react';
import { ALLOWED_ATTACHMENT_CONTENT_TYPES, MAX_ATTACHMENT_SIZE_BYTES } from '../../types/Attachment';

interface AttachmentDropzoneProps {
  onFilesSelected: (files: File[]) => void;
  disabled?: boolean;
  // 'dark' (default) matches TradeEntryModal's dark surface. 'light' matches
  // the dashboard/journal's white-card look - without this, the dropzone
  // hardcoded dark styling and rendered as a solid black box on light cards
  // (the bug seen on the first /journal pass).
  theme?: 'dark' | 'light';
}

// Exposed via ref so a parent modal/panel can feed it pasted files from a
// document-wide paste listener (see usePasteScreenshot) - Cmd+V works
// anywhere in the open modal that way, not just while this specific box has
// focus, while still reusing this component's one validation + error-display
// path instead of duplicating it.
export interface AttachmentDropzoneHandle {
  acceptFiles: (files: File[]) => void;
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
export const AttachmentDropzone = forwardRef<AttachmentDropzoneHandle, AttachmentDropzoneProps>(
  ({ onFilesSelected, disabled, theme = 'dark' }, ref) => {
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

  useImperativeHandle(ref, () => ({ acceptFiles }), [acceptFiles]);

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(false);
    if (disabled) return;
    if (e.dataTransfer.files?.length) acceptFiles(e.dataTransfer.files);
  };

  const isDark = theme === 'dark';
  const idleClasses = isDark ? 'border-neutral-800 bg-neutral-950' : 'border-gray-200 bg-gray-50';
  const draggingClasses = isDark
    ? 'border-emerald-500 bg-emerald-950/20'
    : 'border-emerald-400 bg-emerald-50';
  const primaryTextClass = isDark ? 'text-neutral-400' : 'text-gray-500';
  const secondaryTextClass = isDark ? 'text-neutral-600' : 'text-gray-400';
  const fontClass = isDark ? 'font-mono' : 'font-semibold';

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
        onClick={() => !disabled && inputRef.current?.click()}
        className={`cursor-pointer border-2 border-dashed rounded-lg px-4 py-6 text-center transition ${
          isDragging ? draggingClasses : idleClasses
        } ${disabled ? 'opacity-50 cursor-not-allowed' : ''}`}
      >
        <p className={`text-xs ${fontClass} ${primaryTextClass}`}>
          Drag & drop a screenshot, paste (Cmd+V), or click to browse
        </p>
        <p className={`text-[10px] ${fontClass} ${secondaryTextClass} mt-1`}>PNG, JPEG, WEBP, GIF — up to 10MB</p>
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
      {validationError && (
        <p className={`mt-2 text-xs ${fontClass} ${isDark ? 'text-rose-400' : 'text-red-500'}`}>
          {validationError}
        </p>
      )}
    </div>
  );
  },
);

AttachmentDropzone.displayName = 'AttachmentDropzone';
