import { useState, useEffect, useCallback } from "react";
import apiClient from "../api/apiClient";
import { Account, CreateAccountPayload } from "../types/Account"

export const useAccount = () => {
    const [accounts, setAccounts] = useState<Account[]>([]);
    const [selectedAccountId, setSelectedAccountId] = useState<number | null>(null);
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    const fetchAccounts = useCallback(async () => {
        try {
            setLoading(true);
            setError(null);
            const response = await apiClient.get<Account[]>('/accounts');
            setAccounts(response.data);
            
            // Auto-select the first account if none is selected yet
            if (response.data.length > 0 && selectedAccountId === null) {
                setSelectedAccountId(response.data[0].id);
            }
        } catch (err: any) {
            setError(err.response?.data?.message || 'Failed to fetch trading accounts');
        } finally {
            setLoading(false);
        }
        
       
    }, [selectedAccountId]);

}