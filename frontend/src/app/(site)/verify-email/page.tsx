import { VerifyEmailForm } from "@/components/auth/VerifyEmailForm";

export default function VerifyEmailPage() {
  return (
    <div className="mx-auto max-w-md">
      <div className="card p-6">
        <h1 className="mb-1 text-xl font-bold text-slate-900">Xác minh email</h1>
        <p className="mb-6 text-sm text-slate-500">
          Bước bắt buộc trước khi đăng nhập.
        </p>
        <VerifyEmailForm />
      </div>
    </div>
  );
}
