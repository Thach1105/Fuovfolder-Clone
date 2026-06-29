import Link from "next/link";
import { NotificationSettingsForm } from "@/components/settings/NotificationSettingsForm";
import { SettingsNav } from "@/components/settings/SettingsNav";

export default function NotificationSettingsPage() {
  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <SettingsNav />
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold text-slate-900">Cài đặt thông báo</h1>
        <Link href="/notifications" className="text-sm font-medium text-fuo-600 hover:underline">
          Xem thông báo
        </Link>
      </div>
      <div className="card p-6">
        <NotificationSettingsForm />
      </div>
    </div>
  );
}
