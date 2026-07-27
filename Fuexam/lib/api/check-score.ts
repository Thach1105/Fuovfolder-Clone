import { apiFetch } from "@/lib/api/client";

export interface CheckScoreResult {
  totalQuestions: string;
  score: string;
  subject: string;
  correctAnswers: string;
  charged: string;
  originalPricePoints: number;
  discountPoints: number;
  chargedPoints: number;
}

export function checkScore(file: File, voucherCode?: string) {
  const formData = new FormData();
  formData.set("file", file);
  if (voucherCode?.trim()) formData.set("voucherCode", voucherCode.trim());
  return apiFetch<CheckScoreResult>("/api/v1/check-score", {
    method: "POST",
    body: formData,
  });
}
