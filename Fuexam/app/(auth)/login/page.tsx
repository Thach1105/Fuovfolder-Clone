"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { type FormEvent, Suspense, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { PasswordInput } from "@/components/auth/PasswordInput";
import { ApiError } from "@/lib/api/client";
import { useAuth } from "@/lib/auth/AuthProvider";

function LoginInner() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { login } = useAuth();
  const nextPath = searchParams.get("next");
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login({ identifier, password });
      const destination = nextPath && nextPath.startsWith("/") && !nextPath.startsWith("//") ? nextPath : "/suoc";
      router.push(destination);
      router.refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Đăng nhập thất bại. Vui lòng thử lại.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="w-full max-w-md">
      <div className="mb-8 space-y-2 text-center">
        <p className="app-eyebrow">Chào mừng trở lại</p>
        <h1 className="font-display text-4xl leading-tight">Đăng nhập</h1>
      </div>

      <div className="rounded-2xl border border-foreground/10 bg-background/70 p-6 shadow-lg backdrop-blur-xl sm:p-8">
        <form onSubmit={handleSubmit} className="space-y-5">
          {error && (
            <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {error}
            </div>
          )}

          <div className="space-y-1.5">
            <Label htmlFor="identifier">Email hoặc tên đăng nhập</Label>
            <Input
              id="identifier"
              required
              autoComplete="username"
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
            />
          </div>

          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <Label htmlFor="password">Mật khẩu</Label>
              <Link href="/forgot-password" className="text-sm text-foreground/60 hover:text-foreground hover:underline">
                Quên mật khẩu?
              </Link>
            </div>
            <PasswordInput
              id="password"
              required
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>

          <Button type="submit" disabled={submitting} className="h-11 w-full rounded-full bg-foreground text-background hover:bg-foreground/90">
            {submitting ? "Đang đăng nhập..." : "Đăng nhập"}
          </Button>
        </form>
      </div>

      <p className="mt-6 text-center text-sm text-foreground/60">
        Chưa có tài khoản?{" "}
        <Link href="/register" className="font-medium text-foreground hover:underline">
          Tạo tài khoản
        </Link>
      </p>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense fallback={<div className="app-skeleton h-96 w-full max-w-md rounded-2xl" />}>
      <LoginInner />
    </Suspense>
  );
}
