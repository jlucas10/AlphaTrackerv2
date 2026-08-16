import { useCallback, useEffect, useRef, useState } from 'react';
import apiClient from '../api/apiClient';
import type { Trade } from '../types/Trade';

interface UseTradesResult {
  trades: Trade[];
  loading: boolean;    // true only for the very first fetch
  refreshing: boolean; // true for background refetches, so the dashboard never blanks
  error: string | null;
  refetch: () => Promise<void>;
  deleteTrade: (id: number) => Promise<void>;
}

// Prefers the backend's JSON message (GlobalExceptionHandler returns { message }),
// then falls back to Axios's own transport-level message.
function extractMessage(err: unknown): string {
  const axiosErr = err as { response?: { data?: { message?: string } }; message?: string };
  return axiosErr?.response?.data?.message ?? axiosErr?.message ?? 'Failed to load trades';
}

export function useTrades(): UseTradesResult {
  const [trades, setTrades] = useState<Trade[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isMountedRef = useRef(true);
  // Monotonic id: only the newest in-flight request may write state, so a slow
  // earlier refetch can never overwrite fresher data.
  const requestIdRef = useRef(0);
  const hasLoadedRef = useRef(false);

  const load = useCallback(async () => {
    const requestId = ++requestIdRef.current;
    const isFirstLoad = !hasLoadedRef.current;

    if (isFirstLoad) {
      setLoading(true);
    } else {
      setRefreshing(true);
    }
    // Clear any previous failure, otherwise one transient error sticks forever.
    setError(null);

    try {
      const res = await apiClient.get<Trade[]>('/trades');
      if (!isMountedRef.current || requestId !== requestIdRef.current) return;
      setTrades(res.data);
      hasLoadedRef.current = true;
    } catch (err) {
      if (!isMountedRef.current || requestId !== requestIdRef.current) return;
      setError(extractMessage(err));
    } finally {
      if (isMountedRef.current && requestId === requestIdRef.current) {
        setLoading(false);
        setRefreshing(false);
      }
    }
  }, []);

  // Deleting lives here rather than in the table so every /trades call shares the
  // same refetch and error handling. The server re-checks ownership, so a failure
  // here is surfaced rather than swallowed.
  const deleteTrade = useCallback(
    async (id: number) => {
      try {
        await apiClient.delete(`/trades/${id}`);
      } catch (err) {
        if (isMountedRef.current) {
          setError(extractMessage(err));
        }
        throw err; // let the caller clear its own pending state
      }
      await load();
    },
    [load],
  );

  useEffect(() => {
    // Re-arm on mount: StrictMode's double-mount runs the cleanup below once,
    // and the hook would otherwise stay permanently flagged as unmounted.
    isMountedRef.current = true;
    load();

    return () => {
      isMountedRef.current = false;
    };
  }, [load]);

  return { trades, loading, refreshing, error, refetch: load, deleteTrade };
}
