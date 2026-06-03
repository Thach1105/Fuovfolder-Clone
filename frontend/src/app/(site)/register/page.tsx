import { RegisterForm } from "@/components/auth/RegisterForm";

export default function RegisterPage() {
  return (
    <div className="mx-auto max-w-md">
      <div className="card p-6">
        <h1 className="mb-1 text-xl font-bold text-slate-900">Tạo tài khoản</h1>
        <p className="mb-6 text-sm text-slate-500">
          Đăng ký tài khoản mới trên hệ thống FuOverflow clone.
        </p>
        <RegisterForm />
      </div>
    </div>
  );
}
