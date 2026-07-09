import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import apiClient from '../api/apiClient';

const AuthView: React.FC = () => {
  const { login } = useAuth();
  const navigate = useNavigate();

  // Toggle state to switch between Login and Register modes
  const [isRegistering, setIsRegistering] = useState<boolean>(false);
  
  // Form input states
  const [email, setEmail] = useState<string>('');
  const [password, setPassword] = useState<string>('');
  const [firstName, setFirstName] = useState<string>('');
  const [lastName, setLastName] = useState<string>('');
  
  // Error handling state
  const [error, setError] = useState<string>('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    // Determine the exact endpoint based on mode
    const endpoint = isRegistering ? '/auth/register' : '/auth/authenticate';
    
    // Construct the correct request payload
    const payload = isRegistering 
      ? { email, password, firstName, lastName, role: 'USER' }
      : { email, password };

    try {
      // Dispatch the secure network request using our custom apiClient
      const response = await apiClient.post(endpoint, payload);
      
      // Extract the JWT token returned by your Spring Boot backend
      const token = response.data.token;

      if (token) {
        login(token);       // Save the token globally via Context
        navigate('/dashboard'); // Push the user safely into the active dashboard!
      }
    } catch (err: any) {
      console.error(err);
      setError(err.response?.data?.message || 'Authentication failed. Check your backend server connections.');
    }
  };

  return (
    <div className="flex h-screen items-center justify-center bg-gray-50 font-sans">
      <div className="w-full max-w-md bg-white p-8 rounded-xl shadow-sm border border-gray-100">
        <div className="text-center mb-8">
          <h2 className="text-3xl font-black tracking-tight text-gray-900">AlphaTracker</h2>
          <p className="text-sm text-gray-500 mt-2">
            {isRegistering ? 'Create your institutional trading profile' : 'Sign in to access your ledger'}
          </p>
        </div>

        {error && (
          <div className="mb-4 p-3 bg-red-50 text-red-600 text-sm font-semibold rounded-lg border border-red-100">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          {isRegistering && (
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-bold text-gray-600 uppercase mb-1">First Name</label>
                <input 
                  type="text" required value={firstName} onChange={(e) => setFirstName(e.target.value)}
                  className="w-full p-2.5 bg-gray-50 border border-gray-200 rounded-lg text-sm focus:outline-none focus:border-black"
                />
              </div>
              <div>
                <label className="block text-xs font-bold text-gray-600 uppercase mb-1">Last Name</label>
                <input 
                  type="text" required value={lastName} onChange={(e) => setLastName(e.target.value)}
                  className="w-full p-2.5 bg-gray-50 border border-gray-200 rounded-lg text-sm focus:outline-none focus:border-black"
                />
              </div>
            </div>
          )}

          <div>
            <label className="block text-xs font-bold text-gray-600 uppercase mb-1">Email Address</label>
            <input 
              type="email" required value={email} onChange={(e) => setEmail(e.target.value)}
              className="w-full p-2.5 bg-gray-50 border border-gray-200 rounded-lg text-sm focus:outline-none focus:border-black"
              placeholder="developer@alphatracker.com"
            />
          </div>

          <div>
            <label className="block text-xs font-bold text-gray-600 uppercase mb-1">Password</label>
            <input 
              type="password" required value={password} onChange={(e) => setPassword(e.target.value)}
              className="w-full p-2.5 bg-gray-50 border border-gray-200 rounded-lg text-sm focus:outline-none focus:border-black"
            />
          </div>

          <button 
            type="submit"
            className="w-full py-3 bg-black hover:bg-gray-900 text-white font-bold text-sm rounded-lg transition-all shadow-sm"
          >
            {isRegistering ? 'Register Account' : 'Sign In'}
          </button>
        </form>

        <div className="mt-6 text-center text-sm">
          <button 
            type="button" 
            onClick={() => { setIsRegistering(!isRegistering); setError(''); }}
            className="text-gray-600 hover:text-black font-semibold underline"
          >
            {isRegistering ? 'Already have an account? Sign In' : "Don't have an account? Sign Up"}
          </button>
        </div>
      </div>
    </div>
  );
};

export default AuthView;