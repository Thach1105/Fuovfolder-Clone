import { ProfileSettingsForm } from "@/components/auth/ProfileSettingsForm";
import { ProfileHero } from "@/components/app/profile-hero";

export default function ProfilePage() {
  return (
    <div className="mx-auto max-w-3xl space-y-6 px-4 py-8">
      <ProfileHero />

      <div className="rounded-2xl border border-foreground/10 bg-background p-6 shadow-sm">
        <h2 className="mb-4 font-display text-lg">Thông tin tài khoản</h2>
        <ProfileSettingsForm />
      </div>
    </div>
  );
}
