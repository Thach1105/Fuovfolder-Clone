"use client";

import { Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { useAuth } from "@/lib/auth/AuthProvider";
import { ChangePasswordForm } from "@/components/auth/ChangePasswordForm";
import { SetPasswordRequestCard } from "@/components/auth/SetPasswordRequestCard";
import { SettingsNav } from "@/components/settings/SettingsNav";

function SecurityPageContent() {
  const { user, loading } = useAuth();
  const searchParams = useSearchParams();
  const passwordSet = searchParams.get("passwordSet") === "1";

  if (loading) {
    return <p className="text-sm text-slate-500">Đang tải...</p>;
  }

  if (!user) return null;

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <SettingsNav />
      <h1 className="text-xl font-bold text-slate-900">Bảo mật</h1>

      {passwordSet && (
        <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
          Đặt mật khẩu thành công. Bạn có thể đăng nhập bằng email và mật khẩu.
        </div>
      )}

      <div className="card p-6">
        <h2 className="mb-4 text-base font-semibold text-slate-900">
          {user.hasPassword ? "Đổi mật khẩu" : "Đặt mật khẩu"}
        </h2>
        {user.hasPassword ? <ChangePasswordForm /> : <SetPasswordRequestCard />}
      </div>
    </div>
  );
}

export default function SecurityPage() {
  return (
    <Suspense fallback={<p className="text-sm text-slate-500">Đang tải...</p>}>
      <SecurityPageContent />
    </Suspense>
  );
}
