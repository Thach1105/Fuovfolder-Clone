import { apiFetch } from "@/lib/api/client";
import type { AdminDepositTierResponse } from "@/types/api";

export function listAdminDepositTiers() {
  return apiFetch<AdminDepositTierResponse[]>("/api/v1/admin/deposit-tiers");
}

export function createDepositTier(body: {
  label: string;
  amountVnd: number;
  points: number;
  bonusPercent: number;
  sortOrder: number;
  active?: boolean;
}) {
  return apiFetch<AdminDepositTierResponse>("/api/v1/admin/deposit-tiers", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function updateDepositTier(
  tierId: string,
  body: {
    label?: string;
    amountVnd?: number;
    points?: number;
    bonusPercent?: number;
    sortOrder?: number;
    active?: boolean;
  },
) {
  return apiFetch<AdminDepositTierResponse>(`/api/v1/admin/deposit-tiers/${tierId}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export function toggleDepositTier(tierId: string) {
  return apiFetch<AdminDepositTierResponse>(`/api/v1/admin/deposit-tiers/${tierId}/toggle`, {
    method: "PATCH",
  });
}
