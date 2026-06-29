"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { registerSchema, type RegisterFormValues } from "@/lib/schemas/auth";
import { useAuth } from "@/lib/auth/AuthProvider";
import { PasswordInput } from "@/components/auth/PasswordInput";
import { FPT_CAMPUSES } from "@/lib/fpt-campuses";
import { ApiError } from "@/lib/api/client";
import { ErrorBanner } from "@/components/ui/error-banner";

export function RegisterForm() {
  const router = useRouter();
  const { register: authRegister } = useAuth();

  const form = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      email: "",
      username: "",
      password: "",
      displayName: "",
      campus: "",
    },
  });

  async function onSubmit(values: RegisterFormValues) {
    try {
      await authRegister({
        email: values.email,
        username: values.username,
        password: values.password,
        displayName: values.displayName,
        campus: values.campus || undefined,
      });
      router.push("/verify-email?sent=1");
    } catch (err) {
      form.setError("root", {
        message: err instanceof ApiError ? err.message : "Đăng ký thất bại. Vui lòng thử lại.",
      });
    }
  }

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
      <ErrorBanner message={form.formState.errors.root?.message} />

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

      <div>
        <label htmlFor="username" className="mb-1 block text-sm font-medium text-slate-700">
          Tên đăng nhập
        </label>
        <input
          id="username"
          type="text"
          className="input-field"
          autoComplete="username"
          {...form.register("username")}
        />
        {form.formState.errors.username && (
          <p className="text-xs text-red-600 mt-1">{form.formState.errors.username.message}</p>
        )}
      </div>

      <div>
        <label htmlFor="displayName" className="mb-1 block text-sm font-medium text-slate-700">
          Tên hiển thị
        </label>
        <input
          id="displayName"
          type="text"
          className="input-field"
          {...form.register("displayName")}
        />
        {form.formState.errors.displayName && (
          <p className="text-xs text-red-600 mt-1">{form.formState.errors.displayName.message}</p>
        )}
      </div>

      <div>
        <label htmlFor="campus" className="mb-1 block text-sm font-medium text-slate-700">
          Cơ sở (tuỳ chọn)
        </label>
        <select
          id="campus"
          className="input-field"
          {...form.register("campus")}
        >
          <option value="">-- Chọn cơ sở --</option>
          {FPT_CAMPUSES.map((campus) => (
            <option key={campus} value={campus}>
              {campus}
            </option>
          ))}
        </select>
      </div>

      <div>
        <label htmlFor="password" className="mb-1 block text-sm font-medium text-slate-700">
          Mật khẩu
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

      <button type="submit" disabled={form.formState.isSubmitting} className="btn-primary w-full">
        {form.formState.isSubmitting ? "Đang đăng ký..." : "Tạo tài khoản"}
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
