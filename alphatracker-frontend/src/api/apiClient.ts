import axios from 'axios';

// Create a specialized Axios instance pointing to Spring Boot server
const apiClient = axios.create({
    baseURL: 'http://localhost:8080/api/v1',
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
            config.headers.Authorization = 'Bearer ${token}';
        }

        return config;
    },
    (error) => {
        // Handle any client side transmission error
        return Promise.reject(error)
    }
);
