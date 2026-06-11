"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
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
    if (!token) {
      return;
    }

    const verifyToken = token;
    let cancelled = false;

    async function verify() {
      setState("verifying");
      setError(null);
      try {
        const user = await authApi.verifyEmail(verifyToken);
        if (cancelled) {
          return;
        }
        setDisplayName(user.displayName);
        setState("success");
      } catch (err) {
        if (cancelled) {
          return;
        }
        if (err instanceof ApiError) {
          setError(err.message);
        } else {
          setError("Xác minh thất bại. Liên kết có thể đã hết hạn hoặc không hợp lệ.");
        }
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
          Email đã được xác minh thành công
          {displayName ? ` cho ${displayName}` : ""}. Bạn có thể đăng nhập ngay.
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
        <div className="rounded-lg border border-sky-200 bg-sky-50 px-4 py-3 text-sm text-sky-800">
          Chúng tôi đã gửi email xác minh đến hộp thư của bạn. Nhấn vào liên kết trong email để kích hoạt tài
          khoản.
        </div>
        <p className="text-sm text-slate-500">
          Không thấy email? Kiểm tra thư mục spam hoặc đợi vài phút rồi thử lại.
        </p>
        <Link href="/login" className="font-medium text-fuo-600 hover:underline">
          Về trang đăng nhập
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-4 text-center">
      <p className="text-sm text-slate-600">
        Mở liên kết xác minh trong email để kích hoạt tài khoản. Trang này sẽ tự động xác minh khi bạn nhấn vào
        liên kết.
      </p>
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
