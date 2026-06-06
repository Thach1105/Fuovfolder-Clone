import { apiFetch } from "@/lib/api/client";
import type {
  AuthTokenResponse,
  AuthenticatedUserResponse,
  ForgotPasswordResponse,
  LoginRequest,
  RegisterRequest,
  RegisterResponse,
} from "@/types/api";

export function register(data: RegisterRequest) {
  return apiFetch<RegisterResponse>("/api/v1/auth/register", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export function verifyEmail(token: string) {
  return apiFetch<AuthenticatedUserResponse>("/api/v1/auth/email/verify", {
    method: "POST",
    body: JSON.stringify({ token }),
  });
}

export function forgotPassword(email: string) {
  return apiFetch<ForgotPasswordResponse>("/api/v1/auth/password/forgot", {
    method: "POST",
    body: JSON.stringify({ email }),
  });
}

export function resetPassword(token: string, password: string) {
  return apiFetch<void>("/api/v1/auth/password/reset", {
    method: "POST",
    body: JSON.stringify({ token, password }),
  });
}

export function login(data: LoginRequest) {
  return apiFetch<AuthTokenResponse>("/api/v1/auth/login", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export function logout() {
  return apiFetch<void>("/api/v1/auth/logout", { method: "POST" });
}

export function refreshSession() {
  return apiFetch<AuthTokenResponse>("/api/v1/auth/refresh", {
    method: "POST",
  });
}
