import React from 'react';
import type { AccountStatus } from '../../types/Account';
import { statusLabel } from '../../utils/accountOutcome';

// A small PASSED / FAILED tag. ACTIVE is the normal state and gets no tag, so only a finished account stands out.
export const AccountStatusChip: React.FC<{ status: AccountStatus }> = ({ status }) => {
  if (status === 'ACTIVE') return null;
  const style = status === 'PASSED' ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-600';
  return (
    <span className={`text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-full ${style}`}>
      {statusLabel(status)}
    </span>
  );
};
