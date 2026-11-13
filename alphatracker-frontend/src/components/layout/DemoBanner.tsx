import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

// Slim strip across the top, shown only while signed in as the shared demo account.
export const DemoBanner: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  if (!user?.demo) return null;

  // Leaving the demo means starting a real account, so end the demo session first and open the sign-up form.
  const signUp = () => {
    logout();
    navigate('/login?mode=register');
  };

  return (
    <div
      role="note"
      className="flex items-center justify-center gap-2 bg-amber-100 border-b border-amber-200 px-4 py-1.5 text-xs font-semibold text-amber-900"
    >
      <span>You're viewing a demo account. Data resets daily.</span>
      <button type="button" onClick={signUp} className="font-bold underline hover:text-amber-700">
        Sign up
      </button>
    </div>
  );
};
