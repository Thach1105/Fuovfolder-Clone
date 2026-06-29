import { API_V1 } from "@/lib/constants/api";
import { apiFetch } from "@/lib/api/client";

export interface NotificationItem {
  id: string;
  type: string;
  title: string;
  body: string | null;
  data: Record<string, unknown>;
  read: boolean;
  createdAt: string;
}

export interface NotificationPage {
  items: NotificationItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface UnreadCount {
  count: number;
}

export interface NotificationPreferenceItem {
  type: string;
  channel: string;
  enabled: boolean;
}

export interface NotificationPreferences {
  preferences: NotificationPreferenceItem[];
}

export function listNotifications(opts?: {
  unreadOnly?: boolean;
  page?: number;
  size?: number;
}) {
  const params = new URLSearchParams({
    page: String(opts?.page ?? 0),
    size: String(opts?.size ?? 20),
    unreadOnly: String(opts?.unreadOnly ?? false),
  });
  return apiFetch<NotificationPage>(`${API_V1}/users/me/notifications?${params}`);
}

export function getUnreadNotificationCount() {
  return apiFetch<UnreadCount>(`${API_V1}/users/me/notifications/unread-count`);
}

export function markNotificationRead(notificationId: string) {
  return apiFetch<NotificationItem>(
    `${API_V1}/users/me/notifications/${notificationId}/read`,
    { method: "PATCH" },
  );
}

export function markAllNotificationsRead() {
  return apiFetch<{ updated: number }>(`${API_V1}/users/me/notifications/mark-all-read`, {
    method: "POST",
  });
}

export function getNotificationPreferences() {
  return apiFetch<NotificationPreferences>(`${API_V1}/users/me/notification-preferences`);
}

export function updateNotificationPreferences(body: NotificationPreferences) {
  return apiFetch<NotificationPreferences>(`${API_V1}/users/me/notification-preferences`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export const NOTIFICATION_POLL_MS = 10_000;

export function notificationThreadHref(data: Record<string, unknown>): string | null {
  const threadId = data.threadId;
  return typeof threadId === "string" && threadId ? `/threads/${threadId}` : null;
}
