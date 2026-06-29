"use client";

import { useState } from "react";
import * as authApi from "@/lib/api/auth";
import { ApiError } from "@/lib/api/client";

export function SetPasswordRequestCard() {
  const [sent, setSent] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleRequest() {
    setLoading(true);
    setError(null);
    try {
      await authApi.requestSetPassword();
      setSent(true);
    } catch (err) {
      setError(
        err instanceof ApiError ? err.message : "Không thể gửi email. Vui lòng thử lại."
      );
    } finally {
      setLoading(false);
    }
  }

  if (sent) {
    return (
      <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
        Kiểm tra hộp thư của bạn — chúng tôi đã gửi link đặt mật khẩu.
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <p className="text-sm text-slate-600">
        Tài khoản của bạn đăng nhập qua Google. Bạn có thể đặt mật khẩu để đăng nhập trực tiếp bằng email.
      </p>
      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {error}
        </div>
      )}
      <button onClick={handleRequest} disabled={loading} className="btn-primary">
        {loading ? "Đang gửi..." : "Gửi email xác nhận"}
      </button>
    </div>
  );
}
