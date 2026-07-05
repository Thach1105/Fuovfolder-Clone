import { apiFetch } from "@/lib/api/client";
import type { AdminOverviewResponse, AdminUserPageResponse, SessionListResponse } from "@/types/api";

export function getAdminOverview() {
  return apiFetch<AdminOverviewResponse>("/api/v1/admin/overview");
}

export function listAdminUsers(page = 0, size = 20) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  return apiFetch<AdminUserPageResponse>(`/api/v1/admin/users?${params}`);
}

export function deleteAdminUser(userId: string) {
  return apiFetch<void>(`/api/v1/admin/users/${userId}`, { method: "DELETE" });
}

export function getUserSessions(userId: string) {
  return apiFetch<SessionListResponse>(`/api/v1/admin/users/${userId}/sessions`);
}

export function revokeUserSession(userId: string, familyId: string) {
  return apiFetch<void>(`/api/v1/admin/users/${userId}/sessions/${familyId}`, {
    method: "DELETE",
  });
}

export function revokeAllUserSessions(userId: string) {
  return apiFetch<void>(`/api/v1/admin/users/${userId}/sessions`, {
    method: "DELETE",
  });
}

export function setUserDeviceLimit(userId: string, maxDevices: number | null) {
  return apiFetch<{ maxDevices: number | null }>(
    `/api/v1/admin/users/${userId}/device-limit`,
    {
      method: "PUT",
      body: JSON.stringify({ maxDevices }),
    }
  );
}
