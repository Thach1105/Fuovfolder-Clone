import Link from "next/link";
import { ProfileSettingsForm } from "@/components/auth/ProfileSettingsForm";

export default function ProfilePage() {
  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-slate-900">Hồ sơ của tôi</h1>
          <p className="text-sm text-slate-500">
            Gọi trực tiếp <code className="text-xs">GET /api/v1/users/me</code> và{" "}
            <code className="text-xs">PATCH /api/v1/users/me/profile</code>
          </p>
        </div>
        <Link href="/settings/profile" className="btn-secondary text-sm">
          Cài đặt
        </Link>
      </div>
      <div className="card p-6">
        <ProfileSettingsForm />
      </div>
    </div>
  );
}
