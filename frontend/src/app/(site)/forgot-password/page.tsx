import { ForgotPasswordForm } from "@/components/auth/ForgotPasswordForm";

export default function ForgotPasswordPage() {
  return (
    <div className="mx-auto max-w-md">
      <div className="card p-6">
        <h1 className="mb-1 text-xl font-bold text-slate-900">Quên mật khẩu</h1>
        <p className="mb-6 text-sm text-slate-500">Nhận liên kết đặt lại mật khẩu qua email.</p>
        <ForgotPasswordForm />
      </div>
    </div>
  );
}
