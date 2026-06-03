"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useState } from "react";
import { ApiError } from "@/lib/api/client";
import * as authApi from "@/lib/api/auth";

function VerifyEmailContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const initialToken = searchParams.get("token") ?? "";

  const [token, setToken] = useState(initialToken);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSuccess(null);
    setSubmitting(true);
    try {
      const user = await authApi.verifyEmail(token);
      setSuccess(`Xác minh thành công cho ${user.displayName}. Bạn có thể đăng nhập.`);
      setTimeout(() => router.push("/login"), 1500);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("Xác minh thất bại. Kiểm tra lại token.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {error}
        </div>
      )}
      {success && (
        <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
          {success}
        </div>
      )}

      <p className="text-sm text-slate-600">
        Nhập mã xác minh email. Trong môi trường dev, token được trả về sau khi đăng ký.
      </p>

      <div>
        <label htmlFor="token" className="mb-1 block text-sm font-medium text-slate-700">
          Verification token
        </label>
        <input
          id="token"
          type="text"
          required
          className="input-field font-mono text-xs"
          value={token}
          onChange={(e) => setToken(e.target.value)}
        />
      </div>

      <button type="submit" disabled={submitting} className="btn-primary w-full">
        {submitting ? "Đang xác minh..." : "Xác minh email"}
      </button>
    </form>
  );
}

export function VerifyEmailForm() {
  return (
    <Suspense fallback={<p className="text-sm text-slate-500">Đang tải...</p>}>
      <VerifyEmailContent />
    </Suspense>
  );
}
