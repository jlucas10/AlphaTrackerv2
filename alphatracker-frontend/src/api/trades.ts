import apiClient from './apiClient';
import type { Trade } from '../types/Trade';

export interface TradeUpdatePayload {
  executionRating?: number | null;
  setupTags?: string[];
}

// PATCH /api/v1/trades/{id} - deliberately narrow, see TradeUpdateRequest on
// the backend for why prices/contracts/P&L stay immutable after creation.
export async function updateTrade(tradeId: number, payload: TradeUpdatePayload): Promise<Trade> {
  const res = await apiClient.patch<Trade>(`/trades/${tradeId}`, payload);
  return res.data;
}
