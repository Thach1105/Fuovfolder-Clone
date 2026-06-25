import type { ApiEnvelope, AuthTokenResponse } from "@/types/api";
import { translateApiError, translateFieldMessage } from "@/lib/api/error-messages";

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
const REFRESH_PATH = "/api/v1/auth/refresh";
const NO_REFRESH_RETRY_PATHS = new Set([REFRESH_PATH, "/api/v1/auth/logout"]);

export const AUTH_SESSION_REFRESHED_EVENT = "fuexam:auth-session-refreshed";
export const AUTH_SESSION_EXPIRED_EVENT = "fuexam:auth-session-expired";

let refreshPromise: Promise<AuthTokenResponse> | null = null;

function isApiEnvelope(value: unknown): value is ApiEnvelope<unknown> {
  return (
    typeof value === "object" &&
    value !== null &&
    "success" in value &&
    "code" in value
  );
}

function dispatchBrowserEvent(name: string, detail?: unknown) {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new CustomEvent(name, { detail }));
}

function pathOnly(path: string) {
  return path.split("?")[0];
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
    return (this.body?.error?.fields ?? []).map((field) => ({
      field: field.field,
      message: translateFieldMessage(field.message),
    }));
  }
}

async function doFetch(path: string, init?: RequestInit): Promise<Response> {
  const hasBody = init?.body != null;
  return fetch(`${API_BASE}${path}`, {
    ...init,
    credentials: "include",
    headers: {
      ...(hasBody ? { "Content-Type": "application/json" } : {}),
      ...init?.headers,
    },
  });
}

async function parseResponse<T>(res: Response): Promise<T> {
  if (res.status === 204) {
    return undefined as T;
  }

  const text = await res.text();
  if (!text) {
    if (!res.ok) throw new ApiError(res.status, null);
    return undefined as T;
  }

  const parsed: unknown = JSON.parse(text);

  if (isApiEnvelope(parsed)) {
    if (!res.ok || parsed.success === false) {
      throw new ApiError(res.status, parsed as ApiEnvelope<null>);
    }
    return parsed.data as T;
  }

  if (!res.ok) throw new ApiError(res.status, null);
  return parsed as T;
}

export async function refreshAuthSession(): Promise<AuthTokenResponse> {
  if (!refreshPromise) {
    refreshPromise = doFetch(REFRESH_PATH, { method: "POST" })
      .then((response) => parseResponse<AuthTokenResponse>(response))
      .then((session) => {
        dispatchBrowserEvent(AUTH_SESSION_REFRESHED_EVENT, session);
        return session;
      })
      .catch((error) => {
        dispatchBrowserEvent(AUTH_SESSION_EXPIRED_EVENT);
        throw error;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }

  return refreshPromise;
}

export async function apiFetch<T>(
  path: string,
  init?: RequestInit,
): Promise<T> {
  let res = await doFetch(path, init);

  if (res.status === 401 && !NO_REFRESH_RETRY_PATHS.has(pathOnly(path))) {
    try {
      await refreshAuthSession();
      res = await doFetch(path, init);
    } catch {
      // Keep original 401 response so caller receives the real failed request context.
    }
  }

  return parseResponse<T>(res);
}

export async function checkHealth(): Promise<{ status: string }> {
  const res = await fetch(`${API_BASE}/actuator/health`);
  if (!res.ok) {
    throw new Error("Backend unavailable");
  }
  return res.json();
}

export { API_BASE };
