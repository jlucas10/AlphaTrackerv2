import axios, { type InternalAxiosRequestConfig } from 'axios';

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

// Look in local storage for the JWT we saved during login/registration and, if there is one,
// inject it into the Authorization header. Exported so the assistant client (api/assistant.ts)
// attaches the token the same way, from one place, instead of repeating the storage key.
export function attachToken(config: InternalAxiosRequestConfig): InternalAxiosRequestConfig {
    const token = localStorage.getItem('token');

    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
}

// Configure global request interceptor
apiClient.interceptors.request.use(
    attachToken,
    (error) => {
        // Handle any client side transmission error
        return Promise.reject(error)
    }
);

export default apiClient;
