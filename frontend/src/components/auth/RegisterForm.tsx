"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { ApiError } from "@/lib/api/client";
import { useAuth } from "@/lib/auth/AuthProvider";

export function RegisterForm() {
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

  function updateField(field: keyof typeof form, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const token = await register({
        email: form.email,
        username: form.username,
        password: form.password,
        displayName: form.displayName,
        campus: form.campus || undefined,
      });
      router.push(`/verify-email?token=${encodeURIComponent(token)}`);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("Đăng ký thất bại. Vui lòng thử lại.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {error}
        </div>
      )}

      <div>
        <label htmlFor="email" className="mb-1 block text-sm font-medium text-slate-700">
          Email
        </label>
        <input
          id="email"
          type="email"
          required
          className="input-field"
          value={form.email}
          onChange={(e) => updateField("email", e.target.value)}
          autoComplete="email"
        />
      </div>

      <div>
        <label htmlFor="username" className="mb-1 block text-sm font-medium text-slate-700">
          Tên đăng nhập
        </label>
        <input
          id="username"
          type="text"
          required
          minLength={3}
          maxLength={64}
          className="input-field"
          value={form.username}
          onChange={(e) => updateField("username", e.target.value)}
          autoComplete="username"
        />
      </div>

      <div>
        <label htmlFor="displayName" className="mb-1 block text-sm font-medium text-slate-700">
          Tên hiển thị
        </label>
        <input
          id="displayName"
          type="text"
          required
          className="input-field"
          value={form.displayName}
          onChange={(e) => updateField("displayName", e.target.value)}
        />
      </div>

      <div>
        <label htmlFor="campus" className="mb-1 block text-sm font-medium text-slate-700">
          Campus (tuỳ chọn)
        </label>
        <input
          id="campus"
          type="text"
          className="input-field"
          value={form.campus}
          onChange={(e) => updateField("campus", e.target.value)}
        />
      </div>

      <div>
        <label htmlFor="password" className="mb-1 block text-sm font-medium text-slate-700">
          Mật khẩu
        </label>
        <input
          id="password"
          type="password"
          required
          minLength={8}
          className="input-field"
          value={form.password}
          onChange={(e) => updateField("password", e.target.value)}
          autoComplete="new-password"
        />
      </div>

      <button type="submit" disabled={submitting} className="btn-primary w-full">
        {submitting ? "Đang đăng ký..." : "Tạo tài khoản"}
      </button>

      <p className="text-center text-sm text-slate-500">
        Đã có tài khoản?{" "}
        <Link href="/login" className="font-medium text-fuo-600 hover:underline">
          Đăng nhập
        </Link>
      </p>
    </form>
  );
}
