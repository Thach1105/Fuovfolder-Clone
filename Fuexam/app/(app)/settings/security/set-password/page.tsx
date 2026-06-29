"use client";

import { Suspense, useEffect } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { SetPasswordForm } from "@/components/auth/SetPasswordForm";

function SetPasswordContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const token = searchParams.get("token");

  useEffect(() => {
    if (!token) {
      router.replace("/settings/security");
    }
  }, [token, router]);

  if (!token) return null;

  return (
    <div className="mx-auto max-w-md">
      <div className="card p-6">
        <h1 className="mb-6 text-xl font-bold text-slate-900">Đặt mật khẩu mới</h1>
        <SetPasswordForm token={token} />
      </div>
    </div>
  );
}

export default function SetPasswordPage() {
  return (
    <Suspense fallback={<p className="text-sm text-slate-500">Đang tải...</p>}>
      <SetPasswordContent />
    </Suspense>
  );
}
