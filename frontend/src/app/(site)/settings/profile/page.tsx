import { ProfileSettingsForm } from "@/components/auth/ProfileSettingsForm";

export default function SettingsProfilePage() {
  return (
    <div className="mx-auto max-w-2xl">
      <div className="card p-6">
        <h1 className="mb-6 text-xl font-bold text-slate-900">Cài đặt hồ sơ</h1>
        <ProfileSettingsForm />
      </div>
    </div>
  );
}
