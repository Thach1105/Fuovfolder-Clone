"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { type FormEvent, useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { PasswordInput } from "@/components/auth/PasswordInput";
import { API_BASE, ApiError } from "@/lib/api/client";
import { useAuth } from "@/lib/auth/AuthProvider";
import {
  type ProfileIdentityFormState,
  validateProfileIdentity,
} from "@/lib/auth/profile-validation";
import { FPT_CAMPUSES } from "@/lib/fpt-campuses";
import { cn } from "@/lib/utils";
import { GoogleIcon } from "@/components/icons/GoogleIcon";

type RegisterFormState = {
  email: string;
  username: string;
  password: string;
  confirmPassword: string;
  campus: string;
  displayName: string;
};

type FieldName = keyof RegisterFormState;
type FieldErrors = Partial<Record<FieldName, string>>;

function validate(form: RegisterFormState): FieldErrors {
  const errors: FieldErrors = {};
  const email = form.email.trim();

  if (!email) errors.email = "Nhập email của bạn.";
  else if (!/^\S+@\S+\.\S+$/.test(email)) {
    errors.email = "Email chưa đúng định dạng. Ví dụ: name@domain.com.";
  }

  if (!form.password) errors.password = "Nhập mật khẩu.";
  else if (form.password.length < 8) {
    errors.password = "Mật khẩu cần ít nhất 8 ký tự.";
  } else if (/^\s+$/.test(form.password)) {
    errors.password = "Mật khẩu không được chỉ gồm khoảng trắng.";
  }

  if (!form.confirmPassword) {
    errors.confirmPassword = "Nhập lại mật khẩu để xác nhận.";
  } else if (form.confirmPassword !== form.password) {
    errors.confirmPassword = "Mật khẩu nhập lại chưa khớp.";
  }

  return {
    ...errors,
    ...validateProfileIdentity(form satisfies ProfileIdentityFormState),
  } satisfies FieldErrors;
}

function FieldError({ message }: { message?: string }) {
  if (!message) return null;
  return <p className="text-xs font-medium text-destructive">{message}</p>;
}

export default function RegisterPage() {
  const router = useRouter();
  const { register } = useAuth();
  const [form, setForm] = useState<RegisterFormState>({
    email: "",
    username: "",
    password: "",
    confirmPassword: "",
    campus: "",
    displayName: "",
  });
  const [touched, setTouched] = useState<Partial<Record<FieldName, boolean>>>({});
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const errors = useMemo(() => validate(form), [form]);

  function visibleError(field: FieldName) {
    return touched[field] || submitted ? errors[field] : undefined;
  }

  function update(field: FieldName, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
    setTouched((prev) => ({ ...prev, [field]: true }));
  }

  function markTouched(field: FieldName) {
    setTouched((prev) => ({ ...prev, [field]: true }));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitted(true);
    setError(null);

    const nextErrors = validate(form);
    if (Object.keys(nextErrors).length > 0) return;

    setSubmitting(true);
    try {
      await register({
        email: form.email.trim(),
        username: form.username.trim(),
        password: form.password,
        displayName: form.displayName.trim(),
        campus: form.campus,
      });
      router.push(`/verify-email?sent=1&email=${encodeURIComponent(form.email.trim())}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Đăng ký thất bại. Vui lòng thử lại.");
    } finally {
      setSubmitting(false);
    }
  }

  const fieldClass = (field: FieldName) => cn(visibleError(field) && "border-destructive focus-visible:ring-destructive/20");

  return (
    <div className="w-full max-w-lg">
      <div className="mb-8 space-y-2 text-center">
        <p className="app-eyebrow">Tham gia cộng đồng</p>
        <h1 className="font-display text-4xl leading-tight">Tạo tài khoản</h1>
        <p className="text-sm text-muted-foreground">Điền đúng thông tin để nhận email xác thực và kích hoạt tài khoản.</p>
      </div>

      <div className="rounded-2xl border border-foreground/10 bg-background/70 p-6 shadow-lg backdrop-blur-xl sm:p-8">
        <div className="space-y-4">
          <button
            type="button"
            onClick={() => {
              window.location.href = `${API_BASE}/oauth2/authorization/google`;
            }}
            className="flex h-11 w-full items-center justify-center gap-3 rounded-full border border-foreground/15 bg-background text-sm font-medium transition-colors hover:bg-foreground/5"
          >
            <GoogleIcon className="size-5" />
            Đăng ký bằng Google
          </button>

          <div className="flex items-center gap-4">
            <div className="h-px flex-1 bg-foreground/10" />
            <span className="text-xs text-foreground/40">hoặc</span>
            <div className="h-px flex-1 bg-foreground/10" />
          </div>
        </div>

        <form onSubmit={handleSubmit} noValidate className="mt-4 space-y-4">
          {error && (
            <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {error}
            </div>
          )}

          <div className="space-y-1.5">
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              value={form.email}
              onBlur={() => markTouched("email")}
              onChange={(e) => update("email", e.target.value)}
              aria-invalid={Boolean(visibleError("email"))}
              className={fieldClass("email")}
              placeholder="name@domain.com"
            />
            <FieldError message={visibleError("email")} />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="username">Tên đăng nhập</Label>
            <Input
              id="username"
              autoComplete="username"
              value={form.username}
              onBlur={() => markTouched("username")}
              onChange={(e) => update("username", e.target.value)}
              aria-invalid={Boolean(visibleError("username"))}
              className={fieldClass("username")}
              placeholder="nguyen_van_a"
            />
            <p className="text-xs text-muted-foreground">Viết liền, không dấu. Chỉ dùng chữ thường a-z, số 0-9 và dấu gạch dưới.</p>
            <FieldError message={visibleError("username")} />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="password">Mật khẩu</Label>
            <PasswordInput
              id="password"
              minLength={8}
              autoComplete="new-password"
              value={form.password}
              onBlur={() => markTouched("password")}
              onChange={(e) => update("password", e.target.value)}
              aria-invalid={Boolean(visibleError("password"))}
              className={fieldClass("password")}
            />
            <p className="text-xs text-muted-foreground">Ít nhất 8 ký tự. Bấm biểu tượng mắt để xem mật khẩu.</p>
            <FieldError message={visibleError("password")} />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="confirmPassword">Nhập lại mật khẩu</Label>
            <PasswordInput
              id="confirmPassword"
              minLength={8}
              autoComplete="new-password"
              value={form.confirmPassword}
              onBlur={() => markTouched("confirmPassword")}
              onChange={(e) => update("confirmPassword", e.target.value)}
              aria-invalid={Boolean(visibleError("confirmPassword"))}
              className={fieldClass("confirmPassword")}
            />
            <FieldError message={visibleError("confirmPassword")} />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="campus">Campus</Label>
            <select
              id="campus"
              value={form.campus}
              onBlur={() => markTouched("campus")}
              onChange={(e) => update("campus", e.target.value)}
              aria-invalid={Boolean(visibleError("campus"))}
              className={cn(
                "file:text-foreground placeholder:text-muted-foreground selection:bg-primary selection:text-primary-foreground dark:bg-input/30 border-input h-9 w-full min-w-0 rounded-md border bg-transparent px-3 py-1 text-base shadow-xs outline-none transition-[color,box-shadow] focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50 md:text-sm",
                fieldClass("campus"),
              )}
            >
              <option value="">Chọn campus</option>
              {FPT_CAMPUSES.map((campus) => (
                <option key={campus} value={campus}>{campus}</option>
              ))}
            </select>
            <FieldError message={visibleError("campus")} />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="displayName">Tên hiển thị</Label>
            <Input
              id="displayName"
              value={form.displayName}
              onBlur={() => markTouched("displayName")}
              onChange={(e) => update("displayName", e.target.value)}
              aria-invalid={Boolean(visibleError("displayName"))}
              className={fieldClass("displayName")}
              placeholder="Nguyễn Văn A"
            />
            <p className="text-xs text-muted-foreground">Tên này hiển thị trên diễn đàn và hồ sơ.</p>
            <FieldError message={visibleError("displayName")} />
          </div>

          <Button type="submit" disabled={submitting} className="h-11 w-full rounded-full bg-foreground text-background hover:bg-foreground/90">
            {submitting ? "Đang đăng ký..." : "Tạo tài khoản"}
          </Button>
        </form>
      </div>

      <p className="mt-6 text-center text-sm text-foreground/60">
        Đã có tài khoản?{" "}
        <Link href="/login" className="font-medium text-foreground hover:underline">
          Đăng nhập
        </Link>
      </p>
    </div>
  );
}
