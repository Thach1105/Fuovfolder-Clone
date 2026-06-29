"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { loginSchema, type LoginFormValues } from "@/lib/schemas/auth";
import { useAuth } from "@/lib/auth/AuthProvider";
import { PasswordInput } from "@/components/auth/PasswordInput";
import { ApiError } from "@/lib/api/client";
import { ErrorBanner } from "@/components/ui/error-banner";

export function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { login } = useAuth();
  const nextPath = searchParams.get("next");

  const form = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { identifier: "", password: "" },
  });

  async function onSubmit(values: LoginFormValues) {
    try {
      await login(values);
      const destination =
        nextPath && nextPath.startsWith("/") && !nextPath.startsWith("//")
          ? nextPath
          : "/";
      router.push(destination);
      router.refresh();
    } catch (err) {
      form.setError("root", {
        message: err instanceof ApiError ? err.message : "Đăng nhập thất bại. Vui lòng thử lại.",
      });
    }
  }

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
      <ErrorBanner message={form.formState.errors.root?.message} />

      <div>
        <label htmlFor="identifier" className="mb-1 block text-sm font-medium text-slate-700">
          Email hoặc tên đăng nhập
        </label>
        <input
          id="identifier"
          type="text"
          className="input-field"
          autoComplete="username"
          {...form.register("identifier")}
        />
        {form.formState.errors.identifier && (
          <p className="text-xs text-red-600 mt-1">{form.formState.errors.identifier.message}</p>
        )}
      </div>

      <div>
        <div className="mb-1 flex items-center justify-between">
          <label htmlFor="password" className="block text-sm font-medium text-slate-700">
            Mật khẩu
          </label>
          <Link href="/forgot-password" className="text-sm font-medium text-fuo-600 hover:underline">
            Quên mật khẩu?
          </Link>
        </div>
        <PasswordInput
          id="password"
          autoComplete="current-password"
          {...form.register("password")}
        />
        {form.formState.errors.password && (
          <p className="text-xs text-red-600 mt-1">{form.formState.errors.password.message}</p>
        )}
      </div>

      <button type="submit" disabled={form.formState.isSubmitting} className="btn-primary w-full">
        {form.formState.isSubmitting ? "Đang đăng nhập..." : "Đăng nhập"}
      </button>

      <p className="text-center text-sm text-slate-500">
        Chưa có tài khoản?{" "}
        <Link href="/register" className="font-medium text-fuo-600 hover:underline">
          Tạo tài khoản
        </Link>
      </p>
    </form>
  );
}
