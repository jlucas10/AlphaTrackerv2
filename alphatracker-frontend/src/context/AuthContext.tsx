import React, {createContext, useContext, useState, useEffect} from "react";
import { fetchCurrentUser } from '../api/user';
import type { CurrentUser } from '../types/User';

interface AuthContextType {
    token: string | null;
    // The signed-in user's name, fetched once after sign-in. null until it arrives (or if the lookup fails),
    // so callers must cope with its absence.
    user: CurrentUser | null;
    isAuthenticated: boolean;
    login: (token: string) => void;
    logout: () => void;
    loading: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [token, setToken] = useState<string | null>(null);
    const [loading, setLoading] = useState<boolean>(true);
    // The fetched profile is stored WITH the token it was fetched for. `user` below is only exposed while
    // that token is still the current one, so signing out (or switching accounts) can never show the
    // previous person's name, and there is no need to "reset" this state with a second render.
    const [profile, setProfile] = useState<{ token: string; user: CurrentUser } | null>(null);

    useEffect(() => {
      const savedToken = localStorage.getItem('token');
      if (savedToken) {
        setToken(savedToken);
      }
      setLoading(false);
    }, []);

    useEffect(() => {
      if (!token) return;
      let cancelled = false;
      fetchCurrentUser()
        .then((fetched) => {
          if (!cancelled) setProfile({ token, user: fetched });
        })
        .catch(() => {
          // Greeting by name is a nicety. If the lookup fails (offline, expired token) the sidebar falls
          // back to a neutral label, and any real auth problem surfaces on the calls that matter.
        });
      return () => {
        cancelled = true; // a newer token (or unmount) makes this answer stale
      };
    }, [token]);

    const user = profile && profile.token === token ? profile.user : null;
  
    const login = (newToken: string) => {
      localStorage.setItem('token', newToken);
      setToken(newToken);
    };
  
    const logout = () => {
      localStorage.removeItem('token');
      setToken(null);
    };
  
    const isAuthenticated = !!token;
  
    return (
      <AuthContext.Provider value={{ token, user, isAuthenticated, login, logout, loading }}>
        {children}
      </AuthContext.Provider>
    );
  };
  
  export const useAuth = () => {
    const context = useContext(AuthContext);
    if (!context) {
      throw new Error('useAuth must be used within an AuthProvider');
    }
    return context;
  };