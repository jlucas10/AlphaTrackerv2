import { useEffect } from 'react';
import type { RefObject } from 'react';
import type { AttachmentDropzoneHandle } from '../components/attachments/AttachmentDropzone';

// Lets Cmd+V paste a screenshot anywhere inside an open modal/panel, not just
// while the small dropzone box itself has keyboard focus - requiring an
// extra click into a tiny target before the fast path works defeats the
// point of a paste shortcut. Listens on the whole document rather than a
// specific element for exactly that reason.
//
// Skips entirely when an input/textarea/contentEditable currently has focus,
// so pasting text into the notes field behaves like normal text paste
// instead of being hijacked as a screenshot upload.
export function usePasteScreenshot(dropzoneRef: RefObject<AttachmentDropzoneHandle | null>, enabled: boolean) {
  useEffect(() => {
    if (!enabled) return;

    const handlePaste = (e: ClipboardEvent) => {
      const target = e.target as HTMLElement | null;
      const isTextInput =
        target?.tagName === 'INPUT' || target?.tagName === 'TEXTAREA' || target?.isContentEditable;
      if (isTextInput) return;

      const items = e.clipboardData?.items;
      if (!items) return;

      const files = Array.from(items)
        .filter((item) => item.kind === 'file')
        .map((item) => item.getAsFile())
        .filter((f): f is File => f !== null);

      if (files.length > 0) {
        e.preventDefault();
        dropzoneRef.current?.acceptFiles(files);
      }
    };

    document.addEventListener('paste', handlePaste);
    return () => document.removeEventListener('paste', handlePaste);
  }, [enabled, dropzoneRef]);
}
