"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { changePasswordSchema, type ChangePasswordFormValues } from "@/lib/schemas/auth";
import * as authApi from "@/lib/api/auth";
import { PasswordInput } from "@/components/auth/PasswordInput";
import { ApiError } from "@/lib/api/client";
import { ErrorBanner } from "@/components/ui/error-banner";

export function ChangePasswordForm() {
  const [success, setSuccess] = useState(false);
  const form = useForm<ChangePasswordFormValues>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: "", newPassword: "", confirmNewPassword: "" },
  });

  async function onSubmit(values: ChangePasswordFormValues) {
    setSuccess(false);
    try {
      await authApi.changePassword(values.currentPassword, values.newPassword);
      setSuccess(true);
      form.reset();
    } catch (err) {
      const message =
        err instanceof ApiError && err.code === "WRONG_PASSWORD"
          ? "Mật khẩu hiện tại không đúng."
          : err instanceof ApiError
            ? err.message
            : "Không thể đổi mật khẩu. Vui lòng thử lại.";
      form.setError("root", { message });
    }
  }

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
      {success && (
        <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
          Đổi mật khẩu thành công.
        </div>
      )}
      <ErrorBanner message={form.formState.errors.root?.message} />

      <div>
        <label htmlFor="currentPassword" className="mb-1 block text-sm font-medium text-slate-700">
          Mật khẩu hiện tại
        </label>
        <PasswordInput id="currentPassword" autoComplete="current-password" {...form.register("currentPassword")} />
        {form.formState.errors.currentPassword && (
          <p className="mt-1 text-xs text-red-600">{form.formState.errors.currentPassword.message}</p>
        )}
      </div>

      <div>
        <label htmlFor="newPassword" className="mb-1 block text-sm font-medium text-slate-700">
          Mật khẩu mới
        </label>
        <PasswordInput id="newPassword" autoComplete="new-password" {...form.register("newPassword")} />
        {form.formState.errors.newPassword && (
          <p className="mt-1 text-xs text-red-600">{form.formState.errors.newPassword.message}</p>
        )}
      </div>

      <div>
        <label htmlFor="confirmNewPassword" className="mb-1 block text-sm font-medium text-slate-700">
          Xác nhận mật khẩu mới
        </label>
        <PasswordInput id="confirmNewPassword" autoComplete="new-password" {...form.register("confirmNewPassword")} />
        {form.formState.errors.confirmNewPassword && (
          <p className="mt-1 text-xs text-red-600">{form.formState.errors.confirmNewPassword.message}</p>
        )}
      </div>

      <button type="submit" disabled={form.formState.isSubmitting} className="btn-primary w-full">
        {form.formState.isSubmitting ? "Đang cập nhật..." : "Đổi mật khẩu"}
      </button>
    </form>
  );
}
