"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { Button } from "@/components/ui/button";

const ERROR_MESSAGES: Record<string, string> = {
  OAUTH_CANCELED: "Bạn đã huỷ đăng nhập bằng Google.",
  OAUTH_EMAIL_NOT_VERIFIED:
    "Email Google của bạn chưa được xác minh. Vui lòng xác minh email trước khi đăng nhập.",
  OAUTH_USER_BLOCKED:
    "Tài khoản của bạn đã bị khoá. Vui lòng liên hệ quản trị viên.",
  OAUTH_PROVIDER_ERROR:
    "Đã xảy ra lỗi khi đăng nhập bằng Google. Vui lòng thử lại.",
};

function ErrorInner() {
  const searchParams = useSearchParams();
  const code = searchParams.get("code") ?? "OAUTH_PROVIDER_ERROR";
  const message = ERROR_MESSAGES[code] ?? ERROR_MESSAGES.OAUTH_PROVIDER_ERROR;

  return (
    <div className="w-full max-w-md text-center">
      <div className="mb-8 space-y-2">
        <p className="app-eyebrow">Lỗi đăng nhập</p>
        <h1 className="font-display text-3xl leading-tight">Không thể đăng nhập</h1>
      </div>

      <div className="rounded-2xl border border-foreground/10 bg-background/70 p-6 shadow-lg backdrop-blur-xl sm:p-8">
        <p className="text-foreground/70">{message}</p>
        <div className="mt-6">
          <Button
            asChild
            className="h-11 w-full rounded-full bg-foreground text-background hover:bg-foreground/90"
          >
            <Link href="/login">Thử lại</Link>
          </Button>
        </div>
      </div>
    </div>
  );
}

export default function OAuthErrorPage() {
  return (
    <Suspense fallback={<div className="app-skeleton h-64 w-full max-w-md rounded-2xl" />}>
      <ErrorInner />
    </Suspense>
  );
}
