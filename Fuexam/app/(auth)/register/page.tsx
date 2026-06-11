"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { type FormEvent, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiError } from "@/lib/api/client";
import { useAuth } from "@/lib/auth/AuthProvider";

export default function RegisterPage() {
  const router = useRouter();
  const { register } = useAuth();
  const [form, setForm] = useState({
    email: "",
    username: "",
    password: "",
    displayName: "",
    campus: "",
  });
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function update(field: keyof typeof form, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await register({
        email: form.email,
        username: form.username,
        password: form.password,
        displayName: form.displayName,
        campus: form.campus || undefined,
      });
      router.push("/verify-email?sent=1");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Đăng ký thất bại. Vui lòng thử lại.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="w-full max-w-md">
      <div className="mb-8 space-y-2 text-center">
        <p className="app-eyebrow">Tham gia cộng đồng</p>
        <h1 className="font-display text-4xl leading-tight">Tạo tài khoản</h1>
      </div>

      <div className="rounded-2xl border border-foreground/10 bg-background/70 p-6 shadow-lg backdrop-blur-xl sm:p-8">
        <form onSubmit={handleSubmit} className="space-y-4">
          {error && (
            <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {error}
            </div>
          )}

          <div className="space-y-1.5">
            <Label htmlFor="email">Email</Label>
            <Input id="email" type="email" required autoComplete="email" value={form.email} onChange={(e) => update("email", e.target.value)} />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="username">Tên đăng nhập</Label>
              <Input id="username" required minLength={3} maxLength={64} autoComplete="username" value={form.username} onChange={(e) => update("username", e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="displayName">Tên hiển thị</Label>
              <Input id="displayName" required value={form.displayName} onChange={(e) => update("displayName", e.target.value)} />
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="campus">Campus (tuỳ chọn)</Label>
            <Input id="campus" value={form.campus} onChange={(e) => update("campus", e.target.value)} />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="password">Mật khẩu</Label>
            <Input id="password" type="password" required minLength={8} autoComplete="new-password" value={form.password} onChange={(e) => update("password", e.target.value)} />
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