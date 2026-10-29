import apiClient from './apiClient';
import type { CurrentUser } from '../types/User';

// GET /api/v1/users/me: the signed-in user, identified by the JWT apiClient attaches.
export async function fetchCurrentUser(): Promise<CurrentUser> {
  const res = await apiClient.get<CurrentUser>('/users/me');
  return res.data;
}
