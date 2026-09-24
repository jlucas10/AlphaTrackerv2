import axios from 'axios';

// VITE_API_BASE_URL is baked in at build time (Vite only exposes env vars
// prefixed with VITE_ to client code - see vite.config.ts). Vercel sets this
// to the live Railway backend URL; the fallback keeps `npm run dev` pointing
// at localhost with zero setup, same pattern as the backend's own env vars.
const apiClient = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Configure global request interceptor 
apiClient.interceptors.request.use(
    (config) => {
        // look in local storage for JWT token we saved during login/registration
        const token = localStorage.getItem('token');

        // if token exists, inject directly into authorization header
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }

        return config;
    },
    (error) => {
        // Handle any client side transmission error
        return Promise.reject(error)
    }
);

export default apiClient;