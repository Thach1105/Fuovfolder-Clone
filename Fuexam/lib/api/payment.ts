import { apiFetch } from "@/lib/api/client";

export interface CreatePaymentLinkRequest {
  amount: number;
  returnUrl: string;
  cancelUrl: string;
  description?: string;
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
