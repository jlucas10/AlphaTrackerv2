import { useState, useEffect, useCallback } from "react";
import apiClient from "../api/apiClient";
import type{ Account, BackfillResult, CreateAccountPayload } from "../types/Account"

export const useAccount = () => {
    const [accounts, setAccounts] = useState<Account[]>([]);
    const [selectedAccountId, setSelectedAccountId] = useState<number | null>(null);
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);
    const [unassignedTradeCount, setUnassignedTradeCount] = useState<number>(0);

    const fetchAccounts = useCallback(async () => {
        try {
            setLoading(true);
            setError(null);
            const response = await apiClient.get<Account[]>('/accounts');
            setAccounts(response.data);

            // Auto-select the primary account if none is selected yet, falling
            // back to the first account for a trader who hasn't set one.
            if (response.data.length > 0 && selectedAccountId === null) {
                const primary = response.data.find((a) => a.isPrimary);
                setSelectedAccountId((primary ?? response.data[0]).id);
            }
        } catch (err: any) {
            setError(err.response?.data?.message || 'Failed to fetch trading accounts');
        } finally {
            setLoading(false);
        }


    }, [selectedAccountId]);

    const fetchUnassignedTradeCount = useCallback(async () => {
        try {
            const response = await apiClient.get<number>('/accounts/unassigned-trades/count');
            setUnassignedTradeCount(response.data);
        } catch {
            // Non-critical: the backfill banner just stays hidden until the
            // next successful refetch, so this fails silently like a missed
            // background refresh rather than surfacing a dashboard-wide error.
        }
    }, []);

    // next is createAccount and selecting an account
    const createAccount = async(payload: CreateAccountPayload): Promise<Account> => {
        const response = await apiClient.post<Account>('/accounts', payload);
        const newAccount = response.data;
        setAccounts((prev) => [...prev, newAccount]);
        setSelectedAccountId(newAccount.id);
        return newAccount;
    };

    // Marks one account primary. The backend unsets any other primary account
    // for this user, so the local list is updated the same way rather than
    // re-fetching everything just to flip one flag.
    const setPrimaryAccount = async (accountId: number): Promise<Account> => {
        const response = await apiClient.patch<Account>(`/accounts/${accountId}/primary`);
        const updated = response.data;
        setAccounts((prev) => prev.map((a) => (a.id === updated.id ? updated : { ...a, isPrimary: false })));
        return updated;
    };

    // Reassigns every unassigned trade onto the primary account. This is the
    // explicit, confirmed step — nothing calls it automatically, since it
    // rewrites historical trades' account and changes a live balance.
    const backfillUnassignedTrades = async (): Promise<BackfillResult> => {
        const response = await apiClient.post<BackfillResult>('/accounts/backfill-unassigned');
        const result = response.data;
        setAccounts((prev) => prev.map((a) => (a.id === result.account.id ? result.account : a)));
        setUnassignedTradeCount(0);
        return result;
    };

    useEffect(() => {
        fetchAccounts();
        fetchUnassignedTradeCount();
    }, []);

    const selectedAccount = accounts.find((a) => a.id === selectedAccountId) || null;

    return {
        accounts,
        selectedAccountId,
        setSelectedAccountId,
        selectedAccount,
        loading,
        error,
        refetchAccounts: fetchAccounts,
        createAccount,
        setPrimaryAccount,
        unassignedTradeCount,
        refetchUnassignedTradeCount: fetchUnassignedTradeCount,
        backfillUnassignedTrades,
    };
};
