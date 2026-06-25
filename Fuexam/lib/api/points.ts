import { apiFetch } from "@/lib/api/client";

export interface PointsBalance {
  balance: number;
}

export interface PointsLedgerEntry {
  id: string;
  delta: number;
  reason: string;
  sourceType: string;
  sourceId: string | null;
  createdAt: string;
}

export interface PointsLedgerPage {
  items: PointsLedgerEntry[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export const POINTS_BALANCE_REFRESH_EVENT = "fuexam:points-refresh";

export function requestPointsBalanceRefresh() {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new Event(POINTS_BALANCE_REFRESH_EVENT));
}

export function getPointsBalance() {
  return apiFetch<PointsBalance>("/api/v1/me/points/balance");
}

export function getPointsLedger(page = 0, size = 20) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return apiFetch<PointsLedgerPage>(`/api/v1/me/points/ledger?${params}`);
}

export function adjustUserPoints(userId: string, delta: number, reason?: string) {
  return apiFetch<PointsLedgerEntry>(`/api/v1/admin/users/${userId}/points/adjust`, {
    method: "POST",
    body: JSON.stringify({ delta, reason }),
  });
}
