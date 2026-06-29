import { API_V1 } from "@/lib/constants/api";
import { apiFetch } from "@/lib/api/client";

export interface ReactionStatus {
  type: string;
  count: number;
  reacted: boolean;
}

export function getPostReactionStatus(postId: string, type = "like") {
  const params = new URLSearchParams({ type });
  return apiFetch<ReactionStatus>(`${API_V1}/posts/${postId}/reactions?${params}`);
}

export function addPostReaction(postId: string, type = "like") {
  return apiFetch<ReactionStatus>(`${API_V1}/posts/${postId}/reactions`, {
    method: "POST",
    body: JSON.stringify({ type }),
  });
}

export function removePostReaction(postId: string, type = "like") {
  return apiFetch<ReactionStatus>(`${API_V1}/posts/${postId}/reactions/${encodeURIComponent(type)}`, {
    method: "DELETE",
  });
}
