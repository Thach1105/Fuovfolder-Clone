"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { AlertTriangle, MailCheck } from "lucide-react";
import { ApiError } from "@/lib/api/client";
import * as authApi from "@/lib/api/auth";

type VerifyState = "idle" | "verifying" | "success" | "error";

function VerifyEmailContent() {
  const searchParams = useSearchParams();
  const token = searchParams.get("token");
  const sent = searchParams.get("sent") === "1";

  const [state, setState] = useState<VerifyState>(token ? "verifying" : "idle");
  const [displayName, setDisplayName] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;

    const verifyToken = token;
    let cancelled = false;

    async function verify() {
      setState("verifying");
      setError(null);
      try {
        const user = await authApi.verifyEmail(verifyToken);
        if (cancelled) return;
        setDisplayName(user.displayName);
        setState("success");
      } catch (err) {
        if (cancelled) return;
        setError(err instanceof ApiError ? err.message : "Xác minh thất bại. Liên kết có thể đã hết hạn hoặc không hợp lệ.");
        setState("error");
      }
    }

    verify();

    return () => {
      cancelled = true;
    };
  }, [token]);

  if (state === "verifying") {
    return (
      <div className="space-y-3 text-center">
        <p className="text-sm text-slate-600">Đang xác minh email của bạn...</p>
        <div className="mx-auto h-8 w-8 animate-spin rounded-full border-2 border-fuo-600 border-t-transparent" />
      </div>
    );
  }

  if (state === "success") {
    return (
      <div className="space-y-5 text-center">
        <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
          Email đã được xác minh thành công{displayName ? ` cho ${displayName}` : ""}. Bạn có thể đăng nhập ngay.
        </div>
        <Link href="/login" className="btn-primary inline-block w-full">
          Đăng nhập
        </Link>
      </div>
    );
  }

  if (state === "error") {
    return (
      <div className="space-y-4">
        <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {error}
        </div>
        <p className="text-center text-sm text-slate-500">
          Liên kết xác minh không hợp lệ hoặc đã hết hạn. Vui lòng đăng ký lại hoặc liên hệ hỗ trợ.
        </p>
        <Link href="/login" className="btn-primary inline-block w-full text-center">
          Về trang đăng nhập
        </Link>
      </div>
    );
  }

  if (sent) {
    return (
      <div className="space-y-4 text-center">
        <div className="rounded-xl border border-sky-200 bg-sky-50 px-4 py-4 text-left text-sm text-sky-900">
          <div className="flex gap-3">
            <MailCheck className="mt-0.5 h-5 w-5 shrink-0 text-sky-600" />
            <div>
              <p className="font-semibold">Email xác thực đã được gửi.</p>
              <p className="mt-1 text-sky-800/80">
                Mở hộp thư email bạn vừa đăng ký, rồi bấm vào liên kết xác thực để kích hoạt tài khoản.
              </p>
            </div>
          </div>
        </div>

        <div className="rounded-xl border-2 border-amber-300 bg-amber-50 px-4 py-4 text-left text-sm text-amber-950 shadow-sm">
          <div className="flex gap-3">
            <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-amber-600" />
            <div>
              <p className="font-bold uppercase tracking-wide">Quan trọng: kiểm tra Spam / Junk</p>
              <p className="mt-1">
                Nếu không thấy email trong hộp thư chính, hãy mở thư mục <span className="font-bold">Spam</span>, <span className="font-bold">Junk</span> hoặc <span className="font-bold">Quảng cáo</span>. Email có thể mất vài phút để tới.
              </p>
            </div>
          </div>
        </div>

        <Link href="/login" className="font-medium text-fuo-600 hover:underline">
          Về trang đăng nhập
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-4 text-center">
      <p className="text-sm text-slate-600">
        Mở liên kết xác minh trong email để kích hoạt tài khoản. Trang này sẽ tự động xác minh khi bạn bấm vào liên kết.
      </p>
      <div className="rounded-xl border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-950">
        Không thấy email? Kiểm tra thư mục <strong>Spam / Junk</strong> trước khi thử lại.
      </div>
      <Link href="/login" className="font-medium text-fuo-600 hover:underline">
        Về trang đăng nhập
      </Link>
    </div>
  );
}

export function VerifyEmailForm() {
  return (
    <Suspense fallback={<p className="text-sm text-slate-500">Đang tải...</p>}>
      <VerifyEmailContent />
    </Suspense>
  );
}
