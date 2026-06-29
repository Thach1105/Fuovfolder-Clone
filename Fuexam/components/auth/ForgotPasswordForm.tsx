"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { forgotPasswordSchema, type ForgotPasswordFormValues } from "@/lib/schemas/auth";
import { ApiError } from "@/lib/api/client";
import * as authApi from "@/lib/api/auth";
import { ErrorBanner } from "@/components/ui/error-banner";

const COOLDOWN = 120;

export function ForgotPasswordForm() {
  const [message, setMessage] = useState<string | null>(null);

  const form = useForm<ForgotPasswordFormValues>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: { email: "" },
  });

  const [countdown, setCountdown] = useState(0);
  const [resendStatus, setResendStatus] = useState<"idle" | "sending" | "sent" | "error">("idle");
  const [resendError, setResendError] = useState<string | null>(null);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const startCooldown = useCallback(() => {
    setCountdown(COOLDOWN);
    timerRef.current = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          clearInterval(timerRef.current!);
          timerRef.current = null;
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
  }, []);

  useEffect(() => () => { if (timerRef.current) clearInterval(timerRef.current); }, []);

  async function onSubmit(values: ForgotPasswordFormValues) {
    setMessage(null);
    try {
      await authApi.forgotPassword(values.email);
      setMessage(
        "Nếu email tồn tại trong hệ thống, chúng tôi đã gửi liên kết đặt lại mật khẩu.",
      );
      startCooldown();
    } catch (err) {
      form.setError("root", {
        message: err instanceof ApiError ? err.message : "Không gửi được yêu cầu. Vui lòng thử lại.",
      });
    }
  }

  async function handleResend() {
    if (countdown > 0 || resendStatus === "sending") return;
    setResendError(null);
    setResendStatus("sending");
    try {
      await authApi.forgotPassword(form.getValues("email"));
      setResendStatus("sent");
      startCooldown();
    } catch (err) {
      setResendStatus("error");
      setResendError(
        err instanceof ApiError ? err.message : "Không gửi được email. Vui lòng thử lại."
      );
    }
  }

  if (message) {
    return (
      <div className="space-y-4">
        <div className="rounded-lg border border-sky-200 bg-sky-50 px-4 py-3 text-sm text-sky-800">
          {message}
        </div>
        <p className="text-sm text-slate-500">
          Kiểm tra hộp thư (và thư mục spam). Liên kết đặt lại mật khẩu sẽ hết hạn sau một thời gian ngắn.
        </p>

        <div className="space-y-1 text-center">
          {resendStatus === "sent" && (
            <p className="text-sm text-emerald-600">Đã gửi lại liên kết đặt lại mật khẩu.</p>
          )}
          {resendStatus === "error" && resendError && (
            <p className="text-sm text-red-600">{resendError}</p>
          )}
          <button
            type="button"
            onClick={handleResend}
            disabled={countdown > 0 || resendStatus === "sending"}
            className="text-sm font-medium text-fuo-600 hover:underline disabled:cursor-not-allowed disabled:opacity-50"
          >
            {resendStatus === "sending"
              ? "Đang gửi..."
              : countdown > 0
                ? `Gửi lại sau ${countdown}s`
                : "Gửi lại liên kết"}
          </button>
        </div>

        <Link href="/login" className="font-medium text-fuo-600 hover:underline">
          Về trang đăng nhập
        </Link>
      </div>
    );
  }

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
      <ErrorBanner message={form.formState.errors.root?.message} />

      <p className="text-sm text-slate-600">
        Nhập email đã đăng ký. Chúng tôi sẽ gửi liên kết đặt lại mật khẩu nếu tài khoản tồn tại.
      </p>

      <div>
        <label htmlFor="email" className="mb-1 block text-sm font-medium text-slate-700">
          Email
        </label>
        <input
          id="email"
          type="email"
          className="input-field"
          autoComplete="email"
          {...form.register("email")}
        />
        {form.formState.errors.email && (
          <p className="text-xs text-red-600 mt-1">{form.formState.errors.email.message}</p>
        )}
      </div>

      <button type="submit" disabled={form.formState.isSubmitting} className="btn-primary w-full">
        {form.formState.isSubmitting ? "Đang gửi..." : "Gửi liên kết đặt lại"}
      </button>

      <p className="text-center text-sm text-slate-500">
        <Link href="/login" className="font-medium text-fuo-600 hover:underline">
          Về trang đăng nhập
        </Link>
      </p>
    </form>
  );
}
