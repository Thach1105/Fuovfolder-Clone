import { ResetPasswordForm } from "@/components/auth/ResetPasswordForm";

export default function ResetPasswordPage() {
  return (
    <div className="mx-auto max-w-md">
      <div className="card p-6">
        <h1 className="mb-1 text-xl font-bold text-slate-900">Đặt lại mật khẩu</h1>
        <p className="mb-6 text-sm text-slate-500">Chọn mật khẩu mới cho tài khoản của bạn.</p>
        <ResetPasswordForm />
      </div>
    </div>
  );
}
