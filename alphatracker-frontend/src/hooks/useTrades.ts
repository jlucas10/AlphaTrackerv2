import { useEffect, useState } from 'react';
import apiClient from '../api/apiClient';
import type { Trade } from '../types/Trade';

interface UseTradesResult {
  trades: Trade[];
  loading: boolean;
  error: string | null;
}

export function useTrades(): UseTradesResult {
  const [trades, setTrades] = useState<Trade[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    apiClient
      .get<Trade[]>('/trades')
      .then((res) => {
        if (!cancelled) {
          setTrades(res.data);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err.message ?? 'Failed to load trades');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  return { trades, loading, error };
}
