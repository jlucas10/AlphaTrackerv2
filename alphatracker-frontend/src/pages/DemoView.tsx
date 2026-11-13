import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { demoErrorMessage, startDemoSession } from '../api/demo';

// /demo: the link a recruiter clicks. Asks the backend for a demo token, stores it exactly the way a normal
// login does (AuthContext.login), and drops them on the dashboard.
const DemoView: React.FC = () => {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  // React StrictMode runs effects twice in development; this keeps that to ONE request, so a page load
  // doesn't spend two of the visitor's rate-limit slots.
  const started = useRef(false);

  const start = useCallback(async () => {
    setError(null);
    try {
      const token = await startDemoSession();
      login(token);
      navigate('/dashboard', { replace: true });
    } catch (err) {
      console.error(err);
      setError(demoErrorMessage(err));
    }
    // login/navigate are stable enough for a one-shot call; listing them would re-fire the request.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    start();
  }, [start]);

  return (
    <div className="flex h-screen items-center justify-center bg-gray-50 font-sans px-4">
      <div className="w-full max-w-md bg-white p-8 rounded-xl shadow-sm border border-gray-100 text-center">
        <h2 className="text-3xl font-black tracking-tight text-gray-900">AlphaTracker</h2>

        {error ? (
          <div role="alert" className="mt-6 space-y-4">
            <p className="p-3 bg-red-50 text-red-600 text-sm font-semibold rounded-lg border border-red-100">
              {error}
            </p>
            <div className="flex items-center justify-center gap-3">
              <button
                type="button"
                onClick={start}
                className="px-4 py-2.5 bg-black hover:bg-gray-900 text-white font-bold text-sm rounded-lg transition-all shadow-sm"
              >
                Try again
              </button>
              <Link to="/login" className="text-sm font-semibold text-gray-600 hover:text-black underline">
                Back to sign in
              </Link>
            </div>
          </div>
        ) : (
          <div role="status" aria-live="polite" className="mt-6">
            <div className="mx-auto h-8 w-8 animate-spin rounded-full border-4 border-gray-200 border-t-black" />
            <p className="mt-4 text-sm font-semibold text-gray-500">Setting up your demo account...</p>
          </div>
        )}
      </div>
    </div>
  );
};

export default DemoView;
