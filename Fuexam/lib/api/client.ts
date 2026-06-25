import * as authApi from "@/lib/api/auth";
import type { ApiEnvelope } from "@/types/api";
import { translateApiError, translateFieldMessage } from "@/lib/api/error-messages";

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
const NO_REFRESH_RETRY_PATHS = new Set(["/api/v1/auth/refresh", "/api/v1/auth/logout"]);

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
    // Dịch sang tiếng Việt ngay tại đây để mọi nơi dùng err.message đều có
    // thông báo thân thiện, không lộ message tiếng Anh / mã code của backend.
    super(translateApiError(body?.code, body?.message));
    this.name = "ApiError";
  }

  /** Mã lỗi gốc từ backend (UPPER_SNAKE), hữu ích khi cần phân nhánh logic. */
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

export async function apiFetch<T>(
  path: string,
  init?: RequestInit,
): Promise<T> {
  let res = await doFetch(path, init);

  if (
    (res.status === 401 || res.status === 403) &&
    !NO_REFRESH_RETRY_PATHS.has(path)
  ) {
    try {
      await authApi.refreshSession();
      res = await doFetch(path, init);
    } catch {
      // fall through to normal parsing of the original auth failure path
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
