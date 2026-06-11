import type { ApiEnvelope } from "@/types/api";

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

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
    super(body?.message ?? body?.code ?? "Request failed");
    this.name = "ApiError";
  }

  get fieldErrors(): { field: string; message: string }[] {
    return this.body?.error?.fields ?? [];
  }
}

export async function apiFetch<T>(
  path: string,
  init?: RequestInit,
): Promise<T> {
  const hasBody = init?.body != null;
  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    credentials: "include",
    headers: {
      ...(hasBody ? { "Content-Type": "application/json" } : {}),
      ...init?.headers,
    },
  });

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

export async function checkHealth(): Promise<{ status: string }> {
  const res = await fetch(`${API_BASE}/actuator/health`);
  if (!res.ok) {
    throw new Error("Backend unavailable");
  }
  return res.json();
}

export { API_BASE };
