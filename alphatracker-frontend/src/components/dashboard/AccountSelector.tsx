import React from "react";
import type { Account } from '../../types/Account';

interface AccountSelectorProps {
    accounts: Account[];
    selectedAccountId: number | null;
    onSelectAccount: (accountId: number | null) => void;
    onOpenCreateModal?: () => void;
}

export const AccountSelector: React.FC<AccountSelectorProps> = ({
    accounts,
    selectedAccountId,
    onSelectAccount,
    onOpenCreateModal,
}) => {
    return (
      <div className="flex items-center gap-2">
        <div className="relative">
          <select
            value={selectedAccountId ?? ''}
            onChange={(e) => {
              const val = e.target.value;
              onSelectAccount(val === '' ? null : Number(val));
            }}
            className="bg-gray-50 border border-gray-200 text-gray-900 text-xs font-bold rounded-lg px-3 py-2 pr-8 focus:outline-none focus:border-black transition-colors cursor-pointer appearance-none"
          >
            <option value="">All Accounts (Aggregated)</option>
            {accounts.map((acc) => (
              <option key={acc.id} value={acc.id}>
                {acc.name} — {acc.firm} ({acc.accountType})
              </option>
            ))}
          </select>
          <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center px-2 text-gray-400">
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 9l-7 7-7-7" />
            </svg>
          </div>
        </div>
  
        {onOpenCreateModal && (
          <button
            type="button"
            onClick={onOpenCreateModal}
            className="text-xs font-bold bg-gray-100 hover:bg-gray-200 text-gray-600 px-2.5 py-2 rounded-lg transition-colors"
            title="Add New Account"
          >
            + Account
          </button>
        )}
      </div>
    );
  };