import { apiFetch } from "@/lib/api/client";
import type { AnnouncementActiveResponse, AnnouncementResponse } from "@/types/api";

export async function listAnnouncements(status?: string): Promise<AnnouncementResponse[]> {
  const params = status ? `?status=${encodeURIComponent(status)}` : "";
  return apiFetch<AnnouncementResponse[]>(`/api/v1/admin/announcements${params}`);
}

export async function getAnnouncement(id: string): Promise<AnnouncementResponse> {
  return apiFetch<AnnouncementResponse>(`/api/v1/admin/announcements/${id}`);
}

export async function createAnnouncement(
  body: {
    title: string;
    contentHtml: string;
    backgroundColor: string;
    linkUrl?: string;
    linkLabel?: string;
    priority: number;
    scrollSpeed: number;
    stepSeconds: number;
    startAt: string;
    endAt: string;
  },
  status: string,
): Promise<AnnouncementResponse> {
  return apiFetch<AnnouncementResponse>(
    `/api/v1/admin/announcements?status=${encodeURIComponent(status)}`,
    { method: "POST", body: JSON.stringify(body) },
  );
}

export async function updateAnnouncement(
  id: string,
  body: {
    title: string;
    contentHtml: string;
    backgroundColor: string;
    linkUrl?: string;
    linkLabel?: string;
    priority: number;
    scrollSpeed: number;
    stepSeconds: number;
    startAt: string;
    endAt: string;
  },
): Promise<AnnouncementResponse> {
  return apiFetch<AnnouncementResponse>(`/api/v1/admin/announcements/${id}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export async function deleteAnnouncement(id: string): Promise<void> {
  await apiFetch<void>(`/api/v1/admin/announcements/${id}`, { method: "DELETE" });
}

export async function activateAnnouncement(id: string): Promise<AnnouncementResponse> {
  return apiFetch<AnnouncementResponse>(`/api/v1/admin/announcements/${id}/activate`, {
    method: "POST",
  });
}

export async function deactivateAnnouncement(id: string): Promise<AnnouncementResponse> {
  return apiFetch<AnnouncementResponse>(`/api/v1/admin/announcements/${id}/deactivate`, {
    method: "POST",
  });
}

export async function previewAnnouncement(id: string): Promise<AnnouncementActiveResponse> {
  return apiFetch<AnnouncementActiveResponse>(`/api/v1/admin/announcements/${id}/preview`);
}
