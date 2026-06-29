import { apiFetch } from "@/lib/api/client";
import type { BroadcastConfigResponse } from "@/types/api";

export function listBroadcastConfigs() {
  return apiFetch<BroadcastConfigResponse[]>("/api/v1/admin/broadcasts/configs");
}

export function getBroadcastConfig(eventType: string) {
  return apiFetch<BroadcastConfigResponse>(`/api/v1/admin/broadcasts/configs/${eventType}`);
}

export function upsertBroadcastConfig(
  eventType: string,
  body: { config: Record<string, unknown>; enabled: boolean },
) {
  return apiFetch<BroadcastConfigResponse>(`/api/v1/admin/broadcasts/configs/${eventType}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export function toggleBroadcastConfig(eventType: string) {
  return apiFetch<BroadcastConfigResponse>(`/api/v1/admin/broadcasts/configs/${eventType}/toggle`, {
    method: "PATCH",
  });
}
