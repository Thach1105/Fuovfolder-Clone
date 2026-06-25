import type { ApiEnvelope } from "@/types/api";
import { translateApiError, translateFieldMessage } from "@/lib/api/error-messages";

/**
 * Base URL for API calls.
 * - Empty (default): browser calls the same-origin Next.js proxy at `/api/...`
 *   (see app/api/[...path]/route.ts). Avoids CORS + SameSite cookie problems.
 * - Set to the backend URL only when the backend serves SameSite=None;Secure
 *   cookies and CORS-allows this origin.
 */
const API_BASE = (process.env.NEXT_PUBLIC_API_URL ?? "").replace(/\/$/, "");

function isApiEnvelope(value: unknown): value is ApiEnvelope<unknown> {
  return (
    typeof value === "object" &&
    value !== null &&
    "success" in value &&
    "code" in value
  );
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly body: ApiEnvelope<null> | null,
  ) {
    super(translateApiError(body?.code, body?.message));
    this.name = "ApiError";
  }

  get code(): string | null {
    return this.body?.code ?? null;
  }

  get fieldErrors(): { field: string; message: string }[] {
    return (this.body?.error?.fields ?? []).map((f) => ({
      field: f.field,
      message: translateFieldMessage(f.message),
    }));
  }
}

/** Endpoints that must never trigger the silent-refresh retry loop. */
function isAuthEndpoint(path: string): boolean {
  return (
    path.includes("/api/v1/auth/login") ||
    path.includes("/api/v1/auth/refresh") ||
    path.includes("/api/v1/auth/logout")
  );
}

let refreshInFlight: Promise<boolean> | null = null;

/** Single-flight refresh: many concurrent 401s share one /auth/refresh call. */
async function refreshOnce(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const res = await fetch(`${API_BASE}/api/v1/auth/refresh`, {
          method: "POST",
          credentials: "include",
        });
        return res.ok;
      } catch {
        return false;
      } finally {
        // Clear synchronously once settled: concurrent callers already hold this
        // promise reference; the next 401 after settling starts a fresh refresh.
        refreshInFlight = null;
      }
    })();
  }
  return refreshInFlight;
}

/** Notify the app that the session is gone so it can clear state + redirect. */
function notifyUnauthorized() {
  if (typeof window !== "undefined") {
    window.dispatchEvent(new CustomEvent("fuexam:unauthorized"));
  }
}

async function rawFetch(path: string, init?: RequestInit): Promise<Response> {
  const hasBody = init?.body != null;
  const isFormData =
    typeof FormData !== "undefined" && init?.body instanceof FormData;
  return fetch(`${API_BASE}${path}`, {
    ...init,
    credentials: "include",
    headers: {
      // Only set JSON content-type for non-FormData bodies; FormData must keep
      // its auto-generated multipart boundary.
      ...(hasBody && !isFormData ? { "Content-Type": "application/json" } : {}),
      ...init?.headers,
    },
  });
}

async function parse<T>(res: Response): Promise<T> {
  if (res.status === 204) {
    return undefined as T;
  }

  const text = await res.text();
  if (!text) {
    if (!res.ok) {
      throw new ApiError(res.status, null);
    }
    return undefined as T;
  }

  const parsed: unknown = JSON.parse(text);

  if (isApiEnvelope(parsed)) {
    if (!res.ok || parsed.success === false) {
      throw new ApiError(res.status, parsed as ApiEnvelope<null>);
    }
    return parsed.data as T;
  }

  if (!res.ok) {
    throw new ApiError(res.status, null);
  }

  return parsed as T;
}

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  let res = await rawFetch(path, init);

  // On a 401 for a non-auth endpoint, try one silent refresh then replay once.
  if (res.status === 401 && !isAuthEndpoint(path)) {
    const refreshed = await refreshOnce();
    if (refreshed) {
      // FormData bodies are safe to reuse here (fetch re-serializes them); the
      // same init is replayed once.
      res = await rawFetch(path, init);
    }
    // Still unauthorized after a refresh attempt → session is truly gone.
    if (res.status === 401) {
      notifyUnauthorized();
    }
  }

  return parse<T>(res);
}

export async function checkHealth(): Promise<{ status: string }> {
  const res = await fetch(`${API_BASE}/actuator/health`);
  if (!res.ok) {
    throw new Error("Backend unavailable");
  }
  return res.json();
}

export { API_BASE };
