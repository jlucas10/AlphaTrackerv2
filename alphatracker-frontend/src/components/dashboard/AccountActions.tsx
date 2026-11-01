import React, { useState } from 'react';
import type { Account } from '../../types/Account';
import { availableActions, type AccountAction } from '../../utils/accountActions';

interface AccountActionsProps {
  account: Account;
  hasSuccessor: boolean;
  // The parent does the real work (PATCH, opening the funded form); this only reports the click and
  // shows a failure. A thrown error is turned into the message under the buttons.
  onAction: (action: AccountAction) => Promise<void>;
}

export const AccountActions: React.FC<AccountActionsProps> = ({ account, hasSuccessor, onAction }) => {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = async (action: AccountAction) => {
    setError(null);
    setBusy(true);
    try {
      await onAction(action);
    } catch (err) {
      // An axios error carries the server's message in response.data.message; anything else just has .message.
      const e = err as { response?: { data?: { message?: string } }; message?: string };
      setError(e.response?.data?.message || e.message || 'Could not update the account');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mt-3 pt-3 border-t border-gray-100">
      <div className="flex flex-wrap gap-2">
        {availableActions(account, hasSuccessor).map(({ action, label, suggested }) => (
          <button
            key={action}
            type="button"
            disabled={busy}
            onClick={() => run(action)}
            // The suggested button is filled; the rest are quiet, so nothing pushes a choice on the trader.
            className={`text-xs font-bold px-3 py-1.5 rounded-lg transition-colors disabled:opacity-50 ${
              suggested
                ? action === 'FAIL'
                  ? 'bg-red-600 hover:bg-red-500 text-white'
                  : 'bg-emerald-600 hover:bg-emerald-500 text-white'
                : 'bg-gray-100 hover:bg-gray-200 text-gray-600'
            }`}
          >
            {label}
          </button>
        ))}
      </div>
      {error && <p className="mt-2 text-xs font-semibold text-red-600">{error}</p>}
    </div>
  );
};
