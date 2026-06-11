import { apiFetch } from "@/lib/api/client";
import type { Post, PostPage } from "@/lib/api/forum";

export interface FlagItem {
  id: string;
  reporterUserId: string;
  targetType: string;
  targetId: string;
  reason: string;
  note: string | null;
  status: string;
  createdAt: string;
}

export interface FlagPage {
  items: FlagItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export function createContentFlag(payload: {
  targetType: string;
  targetId: string;
  reason: string;
  note?: string;
}) {
  return apiFetch<FlagItem>("/api/v1/flags", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function listModerationFlags(status = "open", page = 0, size = 20) {
  const params = new URLSearchParams({
    status,
    page: String(page),
    size: String(size),
  });
  return apiFetch<FlagPage>(`/api/v1/admin/moderation/flags?${params}`);
}

export function resolveModerationFlag(flagId: string, action: string, reason?: string) {
  return apiFetch<FlagItem>(`/api/v1/admin/moderation/flags/${flagId}/resolve`, {
    method: "POST",
    body: JSON.stringify({ action, reason }),
  });
}

export function listModerationQueue(page = 0, size = 20) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return apiFetch<PostPage>(`/api/v1/admin/moderation/queue?${params}`);
}

export function approveModerationPost(postId: string) {
  return apiFetch<void>(`/api/v1/admin/moderation/queue/${postId}/approve`, {
    method: "POST",
  });
}

export function rejectModerationPost(postId: string) {
  return apiFetch<void>(`/api/v1/admin/moderation/queue/${postId}/reject`, {
    method: "POST",
  });
}
