import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import AuthView from './pages/AuthView';
import DashboardView from './pages/DashboardView';

// // Temporary Mock Components for the Views (We will build the actual beautiful layouts next!)
// const LoginMock = () => (
//   <div className="flex h-screen flex-col items-center justify-center bg-gray-50">
//     <h2 className="text-2xl font-bold mb-4 text-gray-800">AlphaTracker Sign In</h2>
//     <p className="text-gray-500">Form components coming up next...</p>
//   </div>
// );

// const DashboardMock = () => (
//   <div className="flex h-screen flex-col items-center justify-center bg-gray-900 text-white">
//     <h2 className="text-3xl font-bold mb-2">AlphaTracker Premium Dashboard</h2>
//     <p className="text-gray-400">Trading Ledger, Metrics, and Forms go here.</p>
//   </div>
// );

const App: React.FC = () => {
  return (
    // Provide global authentication state to all routes
    <AuthProvider>
      <Router>
        <Routes>
          {/* Public Route: Anyone can see this screen */}
          <Route path="/login" element={<AuthView />} />

          {/* Protected Route: Wrapped inside our security gatekeeper */}
          <Route 
            path="/dashboard" 
            element={
              <ProtectedRoute>
                <DashboardView/>
              </ProtectedRoute>
            } 
          />

          {/* Catch-all: Redirect any random URL straight to the dashboard or login */}
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </Router>
    </AuthProvider>
  );
};

export default App;