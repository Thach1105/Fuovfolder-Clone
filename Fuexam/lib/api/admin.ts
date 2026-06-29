import { API_V1 } from "@/lib/constants/api";
import { apiFetch } from "@/lib/api/client";
import type { AdminOverviewResponse, AdminUserPageResponse } from "@/types/api";

export function getAdminOverview() {
  return apiFetch<AdminOverviewResponse>(`${API_V1}/admin/overview`);
}

export function listAdminUsers(page = 0, size = 20) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  return apiFetch<AdminUserPageResponse>(`${API_V1}/admin/users?${params}`);
}
