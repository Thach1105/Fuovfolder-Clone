import { apiFetch } from "@/lib/api/client";

export interface DepositTier {
  id: string;
  label: string;
  amountVnd: number;
  totalPoints: number;
  bonusPercent: number;
  sortOrder: number;
}

export interface CreatePaymentLinkRequest {
  tierId: string;
  returnUrl: string;
  cancelUrl: string;
}

export interface PayOSPaymentLinkResponse {
  checkoutUrl?: string | null;
  qrCode?: string | null;
  orderCode: string;
}

export interface PaymentStatusResponse {
  orderCode: string;
  status: string;
  amountCents: number;
  currency: string;
  pointsEarned: number;
  provider: string;
  providerOrderId: string;
}

export function listDepositTiers() {
  return apiFetch<DepositTier[]>("/api/v1/deposit/tiers");
}

export function createPaymentLink(payload: CreatePaymentLinkRequest) {
  return apiFetch<PayOSPaymentLinkResponse>("/api/v1/payment/create", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function getPaymentStatus(orderCode: string) {
  const params = new URLSearchParams({ orderCode });
  return apiFetch<PaymentStatusResponse>(`/api/v1/payment/status?${params}`);
}
