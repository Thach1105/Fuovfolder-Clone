import { API_V1 } from "@/lib/constants/api";
import { apiFetch,  refreshAuthSession } from "@/lib/api/client";
import type {
  AuthTokenResponse,
  AuthenticatedUserResponse,
  CompletePendingProfileRequest,
  ForgotPasswordResponse,
  LoginRequest,
  RegisterRequest,
  RegisterResponse,
  SessionListResponse,
  UserProfileResponse,
} from "@/types/api";

export function register(data: RegisterRequest) {
  return apiFetch<RegisterResponse>(`${API_V1}/auth/register`, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export function verifyEmail(token: string) {
  return apiFetch<AuthenticatedUserResponse>(`${API_V1}/auth/email/verify`, {
    method: "POST",
    body: JSON.stringify({ token }),
  });
}

export function resendVerificationEmail(email: string) {
  return apiFetch<void>(`${API_V1}/auth/email/resend`, {
    method: "POST",
    body: JSON.stringify({ email }),
  });
}

export function forgotPassword(email: string) {
  return apiFetch<ForgotPasswordResponse>(`${API_V1}/auth/password/forgot`, {
    method: "POST",
    body: JSON.stringify({ email }),
  });
}

export function resetPassword(token: string, password: string) {
  return apiFetch<void>(`${API_V1}/auth/password/reset`, {
    method: "POST",
    body: JSON.stringify({ token, password }),
  });
}

export function login(data: LoginRequest) {
  return apiFetch<AuthTokenResponse>(`${API_V1}/auth/login`, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export function logout() {
  return apiFetch<void>(`${API_V1}/auth/logout`, { method: "POST" });
}

export function refreshSession() {
  return refreshAuthSession();
}

export function completePendingProfile(data: CompletePendingProfileRequest) {
  return apiFetch<UserProfileResponse | AuthenticatedUserResponse | void>(
    `${API_V1}/auth/complete-profile`,
    {
      method: "POST",
      body: JSON.stringify(data),
    },
  );
}

export function changePassword(currentPassword: string, newPassword: string) {
  return apiFetch<void>(`${API_V1}/users/me/password`, {
    method: "PATCH",
    body: JSON.stringify({ currentPassword, newPassword }),
  });
}

export function requestSetPassword() {
  return apiFetch<{ message: string }>(`${API_V1}/auth/password/set-request`, {
    method: "POST",
  });
}

export function setPassword(token: string, password: string) {
  return apiFetch<void>(`${API_V1}/auth/password/set`, {
    method: "POST",
    body: JSON.stringify({ token, password }),
  });
}

export function getActiveSessions() {
  return apiFetch<SessionListResponse>(`${API_V1}/auth/sessions`);
}

export function revokeSession(familyId: string) {
  return apiFetch<void>(`${API_V1}/auth/sessions/${familyId}`, {
    method: "DELETE",
  });
}

export function revokeOtherSessions() {
  return apiFetch<void>(`${API_V1}/auth/sessions`, { method: "DELETE" });
}
