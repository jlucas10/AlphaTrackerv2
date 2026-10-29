// Who is signed in, as returned by GET /api/v1/users/me. Just enough to greet the user; mirrors the
// backend's UserResponse (never the full User record).
export interface CurrentUser {
  firstName: string;
  lastName: string | null;
  email: string;
}
