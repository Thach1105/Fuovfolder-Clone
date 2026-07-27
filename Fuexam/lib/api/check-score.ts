import { apiFetch } from "@/lib/api/client";

export interface CheckScoreResult {
  totalQuestions: string;
  score: string;
  subject: string;
  correctAnswers: string;
  charged: string;
}

export function checkScore(file: File) {
  const formData = new FormData();
  formData.set("file", file);
  return apiFetch<CheckScoreResult>("/api/v1/check-score", {
    method: "POST",
    body: formData,
  });
}
