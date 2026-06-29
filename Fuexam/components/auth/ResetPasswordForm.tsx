"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { resetPasswordSchema, type ResetPasswordFormValues } from "@/lib/schemas/auth";
import * as authApi from "@/lib/api/auth";
import { PasswordInput } from "@/components/auth/PasswordInput";
import { ApiError } from "@/lib/api/client";
import { ErrorBanner } from "@/components/ui/error-banner";

function ResetPasswordContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const token = searchParams.get("token") ?? "";

  const form = useForm<ResetPasswordFormValues>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { password: "", confirmPassword: "" },
  });

  const [success, setSuccess] = useState(false);

  async function onSubmit(values: ResetPasswordFormValues) {
    if (!token) {
      form.setError("root", { message: "Liên kết đặt lại mật khẩu không hợp lệ." });
      return;
    }
    try {
      await authApi.resetPassword(token, values.password);
      setSuccess(true);
      setTimeout(() => {
        router.push("/login");
      }, 2000);
    } catch (err) {
      form.setError("root", {
        message: err instanceof ApiError ? err.message : "Không thể đặt lại mật khẩu. Liên kết có thể đã hết hạn.",
      });
    }
  }

  if (!token) {
    return (
      <div className="space-y-4">
        <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          Liên kết đặt lại mật khẩu không hợp lệ hoặc thiếu mã token.
        </div>
        <Link href="/forgot-password" className="btn-primary inline-block w-full text-center">
          Yêu cầu liên kết mới
        </Link>
      </div>
    );
  }

  if (success) {
    return (
      <div className="space-y-4 text-center">
        <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
          Mật khẩu đã được cập nhật. Bạn sẽ được chuyển đến trang đăng nhập...
        </div>
        <Link href="/login" className="font-medium text-fuo-600 hover:underline">
          Đăng nhập ngay
        </Link>
      </div>
    );
  }

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
      <ErrorBanner message={form.formState.errors.root?.message} />

      <div>
        <label htmlFor="password" className="mb-1 block text-sm font-medium text-slate-700">
          Mật khẩu mới
        </label>
        <PasswordInput
          id="password"
          autoComplete="new-password"
          {...form.register("password")}
        />
        {form.formState.errors.password && (
          <p className="text-xs text-red-600 mt-1">{form.formState.errors.password.message}</p>
        )}
      </div>

      <div>
        <label htmlFor="confirmPassword" className="mb-1 block text-sm font-medium text-slate-700">
          Xác nhận mật khẩu mới
        </label>
        <PasswordInput
          id="confirmPassword"
          autoComplete="new-password"
          {...form.register("confirmPassword")}
        />
        {form.formState.errors.confirmPassword && (
          <p className="text-xs text-red-600 mt-1">{form.formState.errors.confirmPassword.message}</p>
        )}
      </div>

      <button type="submit" disabled={form.formState.isSubmitting} className="btn-primary w-full">
        {form.formState.isSubmitting ? "Đang cập nhật..." : "Đặt lại mật khẩu"}
      </button>
    </form>
  );
}

export function ResetPasswordForm() {
  return (
    <Suspense fallback={<p className="text-sm text-slate-500">Đang tải...</p>}>
      <ResetPasswordContent />
    </Suspense>
  );
}
