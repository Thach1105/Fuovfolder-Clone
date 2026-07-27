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

export interface CheckScoreSubjects {
  items: string[];
  total: number;
  page: number;
  limit: number;
  hasPrevious: boolean;
  hasNext: boolean;
  totalPages: number;
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

export function listCheckScoreSubjects() {
  return apiFetch<CheckScoreSubjects>("/api/v1/check-score/subjects");
}
