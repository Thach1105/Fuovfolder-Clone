import { apiFetch } from "@/lib/api/client";

export interface CheckScoreConfig {
  configured: boolean;
  cookieConfigured: boolean;
  authorizeKey: string;
  xsrfCookie: string;
  checkScoreUrl: string;
}

export function getCheckScoreConfig() {
  return apiFetch<CheckScoreConfig>("/api/v1/admin/check-score/config");
}

export function updateCheckScoreConfig(authorizeKey: string, xsrfCookie: string, checkScoreUrl: string) {
  return apiFetch<CheckScoreConfig>("/api/v1/admin/check-score/config", {
    method: "PUT",
    body: JSON.stringify({ authorizeKey, xsrfCookie, checkScoreUrl }),
  });
}
