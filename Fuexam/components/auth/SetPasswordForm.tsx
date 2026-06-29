"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { setPasswordSchema, type SetPasswordFormValues } from "@/lib/schemas/auth";
import * as authApi from "@/lib/api/auth";
import { PasswordInput } from "@/components/auth/PasswordInput";
import { ApiError } from "@/lib/api/client";
import { ErrorBanner } from "@/components/ui/error-banner";

interface Props {
  token: string;
}

export function SetPasswordForm({ token }: Props) {
  const router = useRouter();
  const [tokenInvalid, setTokenInvalid] = useState(false);
  const form = useForm<SetPasswordFormValues>({
    resolver: zodResolver(setPasswordSchema),
    defaultValues: { password: "", confirmPassword: "" },
  });

  async function onSubmit(values: SetPasswordFormValues) {
    try {
      await authApi.setPassword(token, values.password);
      router.push("/settings/security?passwordSet=1");
    } catch (err) {
      if (err instanceof ApiError && (err.code === "TOKEN_INVALID" || err.code === "TOKEN_EXPIRED")) {
        setTokenInvalid(true);
        return;
      }
      form.setError("root", {
        message: err instanceof ApiError ? err.message : "Không thể đặt mật khẩu. Vui lòng thử lại.",
      });
    }
  }

  if (tokenInvalid) {
    return (
      <div className="space-y-4">
        <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          Liên kết đặt mật khẩu không hợp lệ hoặc đã hết hạn.
        </div>
        <Link href="/settings/security" className="btn-primary inline-block w-full text-center">
          Gửi lại email xác nhận
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
        {form.formState.isSubmitting ? "Đang xác nhận..." : "Xác nhận mật khẩu"}
      </button>
    </form>
  );
}
