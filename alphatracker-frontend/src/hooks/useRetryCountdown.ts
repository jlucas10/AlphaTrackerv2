import { useEffect, useState } from 'react';
import type { AssistantError } from '../utils/assistantChat';

// Whole seconds until sending is allowed again after a rate limit; 0 when there is nothing to wait for.
export function useRetryCountdown(error: AssistantError | null): number {
  const [now, setNow] = useState(0); // 0 = "no tick yet"
  const retryAt = error?.retryAt;

  useEffect(() => {
    if (retryAt === undefined) return;
    const id = window.setInterval(() => {
      const t = Date.now();
      setNow(t);
      if (t >= retryAt) window.clearInterval(id); // finished: stop waking up four times a second
    }, 250);
    return () => window.clearInterval(id);
  }, [retryAt]);

  if (!error || retryAt === undefined) return 0;

  // `now` can be stale or 0 until the first tick. The error's own creation time is a safe lower bound
  // (time can't be earlier than that), so the very first frame shows the full wait, not a nonsense number.
  const effectiveNow = Math.max(now, error.createdAt);
  return Math.max(0, Math.ceil((retryAt - effectiveNow) / 1000));
}
