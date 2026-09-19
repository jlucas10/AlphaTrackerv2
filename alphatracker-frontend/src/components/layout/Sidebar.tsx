import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

interface SidebarProps {
  active: 'dashboard' | 'journal';
  onOpenAccounts?: () => void;
}

// Extracted from DashboardView so JournalView can share it rather than
// duplicating the nav shell - the "Trading Journal" button here used to be
// dead (no onClick at all); it now actually navigates.
export const Sidebar: React.FC<SidebarProps> = ({ active, onOpenAccounts }) => {
  const { logout } = useAuth();
  const navigate = useNavigate();

  const linkClass = (isActive: boolean) =>
    `w-full flex items-center gap-3 px-4 py-3 rounded-xl font-bold text-sm transition-all text-left ${
      isActive
        ? 'bg-slate-900 text-white shadow-xs'
        : 'text-gray-500 hover:text-black hover:bg-gray-50 font-semibold'
    }`;

  return (
    <aside className="w-64 bg-white border-r border-gray-100 flex flex-col justify-between p-6">
      <div>
        <div className="flex items-center gap-3 mb-10 px-2">
          <div className="grid grid-cols-2 gap-1 w-5 h-5">
            <div className="bg-black rounded-xs"></div>
            <div className="bg-black rounded-xs"></div>
            <div className="bg-black rounded-xs"></div>
            <div className="bg-black rounded-xs"></div>
          </div>
          <span className="font-black text-xl tracking-tight text-gray-900">AlphaTracker</span>
        </div>

        <nav className="space-y-1">
          <button onClick={() => navigate('/dashboard')} className={linkClass(active === 'dashboard')}>
            <span>📊</span> Dashboard
          </button>
          <button className={linkClass(false)}>
            <span>📈</span> Investing
          </button>
          {onOpenAccounts && (
            <button onClick={onOpenAccounts} className={linkClass(false)}>
              <span>💳</span> Accounts
            </button>
          )}
          <button onClick={() => navigate('/journal')} className={linkClass(active === 'journal')}>
            <span>📓</span> Trading Journal
          </button>
        </nav>
      </div>

      <div className="border-t border-gray-100 pt-4 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-full bg-slate-700 flex items-center justify-center text-white font-bold text-sm">
            J
          </div>
          <div>
            <p className="text-sm font-bold text-gray-900">Josiah</p>
            <button onClick={logout} className="text-xs text-red-500 hover:underline font-semibold">
              Sign Out
            </button>
          </div>
        </div>
      </div>
    </aside>
  );
};
