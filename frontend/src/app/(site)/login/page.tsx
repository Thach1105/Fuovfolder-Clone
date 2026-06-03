import { Suspense } from "react";
import { LoginForm } from "@/components/auth/LoginForm";

export default function LoginPage() {
  return (
    <div className="mx-auto max-w-md">
      <div className="card p-6">
        <h1 className="mb-1 text-xl font-bold text-slate-900">Đăng nhập</h1>
        <p className="mb-6 text-sm text-slate-500">
          Đăng nhập để test API auth và profile trực tiếp trên giao diện.
        </p>
        <Suspense fallback={<p className="text-sm text-slate-500">Đang tải...</p>}>
          <LoginForm />
        </Suspense>
      </div>
    </div>
  );
}
