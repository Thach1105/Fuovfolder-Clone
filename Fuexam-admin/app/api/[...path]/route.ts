import { NextRequest, NextResponse } from "next/server";

/**
 * Same-origin BFF proxy for the admin panel.
 *
 * The browser calls `/api/...` on the Next.js origin (same-origin → no CORS, no
 * preflight). This handler forwards each request server-to-server to the real
 * backend and pipes the response back. HttpOnly auth cookies set by the backend
 * are rewritten so the browser can store them against the Next.js host even over
 * plain http during local dev.
 *
 * Because this proxy is mounted at `/api`, the backend cookie paths (`/` for the
 * access token and `/api/v1/auth` for the refresh token) keep working unchanged —
 * the browser replays them on exactly the right requests.
 */

const BACKEND_ORIGIN = (process.env.BACKEND_ORIGIN ?? "https://api-fuexam.fuexam.com").replace(
  /\/$/,
  "",
);

// Never cache proxied responses.
export const dynamic = "force-dynamic";
// Pin the Node.js runtime so Headers.getSetCookie() is guaranteed available
// (Edge runtime lacks it, which would silently drop auth cookies).
export const runtime = "nodejs";

// Headers that must not be forwarded verbatim (connection-level or recomputed by fetch).
// `accept-encoding` is dropped so the backend never compresses — we re-buffer the body
// and let Next set content-length, which would otherwise mismatch a gzipped payload.
const STRIP_REQUEST_HEADERS = new Set([
  "host",
  "connection",
  "content-length",
  "accept-encoding",
  "transfer-encoding",
  "keep-alive",
  "upgrade",
]);

const STRIP_RESPONSE_HEADERS = new Set([
  "set-cookie",
  "content-encoding",
  "content-length",
  "transfer-encoding",
  "connection",
  "keep-alive",
]);

/**
 * Rewrite a single Set-Cookie header so the browser will store it against the
 * proxy host. Strips the Domain attribute always; over http (local dev) also
 * strips Secure and downgrades SameSite=None (which requires Secure) to Lax.
 */
function rewriteSetCookie(raw: string, isHttps: boolean): string {
  let cookie = raw.replace(/;\s*Domain=[^;]*/i, "");
  if (!isHttps) {
    cookie = cookie.replace(/;\s*Secure/i, "");
    cookie = cookie.replace(/;\s*SameSite=None/i, "; SameSite=Lax");
  }
  return cookie;
}

function envelope(code: string, message: string, status: number): NextResponse {
  return NextResponse.json(
    { success: false, code, message, data: null, error: null, timestamp: new Date().toISOString() },
    { status },
  );
}

async function proxy(request: NextRequest, segments: string[]): Promise<NextResponse> {
  const targetUrl = `${BACKEND_ORIGIN}/api/${segments.join("/")}${request.nextUrl.search}`;

  const headers = new Headers();
  request.headers.forEach((value, key) => {
    if (!STRIP_REQUEST_HEADERS.has(key.toLowerCase())) {
      headers.set(key, value);
    }
  });

  const method = request.method.toUpperCase();
  const hasBody = method !== "GET" && method !== "HEAD";
  const body = hasBody ? await request.arrayBuffer() : undefined;

  let backendRes: Response;
  try {
    backendRes = await fetch(targetUrl, {
      method,
      headers,
      body: body && body.byteLength > 0 ? body : undefined,
      redirect: "manual",
      cache: "no-store",
    });
  } catch {
    return envelope("PROXY_ERROR", "Không kết nối được máy chủ backend.", 502);
  }

  const isHttps = request.nextUrl.protocol === "https:";
  const responseHeaders = new Headers();
  backendRes.headers.forEach((value, key) => {
    if (!STRIP_RESPONSE_HEADERS.has(key.toLowerCase())) {
      responseHeaders.set(key, value);
    }
  });

  // getSetCookie() returns each Set-Cookie header individually (undici/Node 18+).
  const setCookies = backendRes.headers.getSetCookie?.() ?? [];
  for (const cookie of setCookies) {
    responseHeaders.append("set-cookie", rewriteSetCookie(cookie, isHttps));
  }

  const payload = await backendRes.arrayBuffer();
  return new NextResponse(payload, {
    status: backendRes.status,
    statusText: backendRes.statusText,
    headers: responseHeaders,
  });
}

type RouteContext = { params: Promise<{ path: string[] }> };

async function handle(request: NextRequest, ctx: RouteContext): Promise<NextResponse> {
  const { path } = await ctx.params;
  return proxy(request, path ?? []);
}

export { handle as GET, handle as POST, handle as PUT, handle as PATCH, handle as DELETE, handle as HEAD };
