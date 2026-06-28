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

export interface CreateCustomPaymentLinkRequest {
  amountVnd: number;
  returnUrl: string;
  cancelUrl: string;
}

/** Số tiền nạp linh động tối thiểu (đồng bộ với backend). */
export const MIN_CUSTOM_DEPOSIT_VND = 1000;

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

export function createCustomPaymentLink(payload: CreateCustomPaymentLinkRequest) {
  return apiFetch<PayOSPaymentLinkResponse>("/api/v1/payment/create-custom", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function getPaymentStatus(orderCode: string) {
  const params = new URLSearchParams({ orderCode });
  return apiFetch<PaymentStatusResponse>(`/api/v1/payment/status?${params}`);
}

export interface DepositHistoryItem {
  orderId: string;
  orderCode: string;
  amount: number;
  currency: string;
  status: string;
  pointsAwarded: number | null;
  tierLabel: string | null;
  canResume: boolean;
  expiredAt: string | null;
  createdAt: string;
  paidAt: string | null;
}

export interface DepositHistoryPage {
  items: DepositHistoryItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface DepositResumeInfo {
  canResume: boolean;
  checkoutUrl: string | null;
  expiredAt: string | null;
  remainingSeconds: number | null;
  reason: string | null;
  message: string | null;
}

export function listMyDeposits(params: {
  page?: number;
  size?: number;
  status?: string;
}) {
  const q = new URLSearchParams();
  if (params.page != null) q.set("page", String(params.page));
  if (params.size != null) q.set("size", String(params.size));
  if (params.status) q.set("status", params.status);
  return apiFetch<DepositHistoryPage>(`/api/v1/payment/me/deposits?${q}`);
}

export function resumeDeposit(orderId: string) {
  return apiFetch<DepositResumeInfo>(`/api/v1/payment/me/deposits/${orderId}/resume`);
}
