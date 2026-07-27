import { AdminShell } from "@/components/admin/AdminShell";
import { AnnouncementForm } from "@/components/admin/AnnouncementForm";

export default function NewAnnouncementPage() {
  return (
    <AdminShell title="Tạo thông báo mới" description="Soạn thông báo marquee hiển thị toàn server">
      <AnnouncementForm />
    </AdminShell>
  );
}
