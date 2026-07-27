"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useCallback, useEffect, useRef, useState } from "react";
import { AlertTriangle, MailCheck } from "lucide-react";
import { ApiError } from "@/lib/api/client";
import * as authApi from "@/lib/api/auth";
import { InputOTP, InputOTPGroup, InputOTPSlot } from "@/components/ui/input-otp";

const COOLDOWN = 120;

type VerifyState = "idle" | "verifying" | "success" | "error";

function useResendCooldown() {
  const [countdown, setCountdown] = useState(0);
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

  return { countdown, startCooldown };
}

function VerifyEmailContent() {
  const searchParams = useSearchParams();
  const token = searchParams.get("token");
  const sent = searchParams.get("sent") === "1";
  const emailParam = searchParams.get("email") ?? "";

  const [state, setState] = useState<VerifyState>(token ? "verifying" : "idle");
  const [displayName, setDisplayName] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [otpCode, setOtpCode] = useState("");
  const [otpState, setOtpState] = useState<"idle" | "verifying" | "error">("idle");
  const [otpError, setOtpError] = useState<string | null>(null);

  const [resendStatus, setResendStatus] = useState<"idle" | "sending" | "sent" | "error">("idle");
  const [resendError, setResendError] = useState<string | null>(null);
  const { countdown, startCooldown } = useResendCooldown();

  async function handleResend() {
    if (!emailParam || countdown > 0 || resendStatus === "sending") return;
    setResendError(null);
    setResendStatus("sending");
    try {
      await authApi.resendVerificationEmail(emailParam);
      setResendStatus("sent");
      startCooldown();
      setOtpCode("");
      setOtpState("idle");
      setOtpError(null);
    } catch (err) {
      setResendStatus("error");
      setResendError(
        err instanceof ApiError ? err.message : "Không gửi được email. Vui lòng thử lại."
      );
    }
  }

  async function handleOtpSubmit() {
    if (!emailParam || otpCode.length !== 6 || otpState === "verifying") return;
    setOtpState("verifying");
    setOtpError(null);
    try {
      const user = await authApi.verifyEmailCode(emailParam, otpCode);
      setDisplayName(user.displayName);
      setState("success");
    } catch (err) {
      setOtpState("error");
      setOtpError(
        err instanceof ApiError ? err.message : "Mã xác minh không hợp lệ. Vui lòng thử lại."
      );
    }
  }

  useEffect(() => {
    if (otpCode.length === 6 && emailParam) {
      handleOtpSubmit();
    }
  }, [otpCode]);

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
                Nhập mã xác minh 6 số trong email để kích hoạt tài khoản.
              </p>
            </div>
          </div>
        </div>

        {emailParam && (
          <div className="space-y-3">
            <p className="text-sm font-medium text-slate-700">Nhập mã xác minh</p>
            <div className="flex justify-center">
              <InputOTP
                maxLength={6}
                value={otpCode}
                onChange={setOtpCode}
                disabled={otpState === "verifying"}
              >
                <InputOTPGroup>
                  <InputOTPSlot index={0} />
                  <InputOTPSlot index={1} />
                  <InputOTPSlot index={2} />
                  <InputOTPSlot index={3} />
                  <InputOTPSlot index={4} />
                  <InputOTPSlot index={5} />
                </InputOTPGroup>
              </InputOTP>
            </div>

            {otpState === "verifying" && (
              <div className="flex items-center justify-center gap-2 text-sm text-slate-600">
                <div className="h-4 w-4 animate-spin rounded-full border-2 border-fuo-600 border-t-transparent" />
                Đang xác minh...
              </div>
            )}

            {otpState === "error" && otpError && (
              <p className="text-sm text-red-600">{otpError}</p>
            )}
          </div>
        )}

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

        {emailParam && (
          <div className="space-y-2">
            {resendStatus === "sent" && (
              <p className="text-sm text-emerald-600">Email đã được gửi lại thành công.</p>
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
                  : "Không nhận được email? Gửi lại"}
            </button>
          </div>
        )}

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
