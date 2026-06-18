import React, {createContext, useContext, useState, useEffect} from "react";

interface AuthContextType {
    token: string | null;
    isAuthenticated: boolean;
    login: (token: string) => void;
    logout: () => void;
    loading: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [token, setToken] = useState<string | null>(null);
    const [loading, setLoading] = useState<boolean>(true);
  
    useEffect(() => {
      const savedToken = localStorage.getItem('token');
      if (savedToken) {
        setToken(savedToken);
      }
      setLoading(false);
    }, []);
  
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
      <AuthContext.Provider value={{ token, isAuthenticated, login, logout, loading }}>
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