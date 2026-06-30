import { apiFetch } from "@/lib/api/client";
import { API_V1 } from "@/lib/constants/api";

export interface VoucherPreviewResponse {
  valid: boolean;
  discountPoints: number;
  finalPoints: number;
  message: string;
}

export function previewVoucher(code: string, transactionType: string, originalPoints: number) {
  return apiFetch<VoucherPreviewResponse>(`${API_V1}/vouchers/preview`, {
    method: "POST",
    body: JSON.stringify({ code, transactionType, originalPoints }),
  });
}
