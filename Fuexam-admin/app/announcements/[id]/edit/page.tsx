"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { AdminShell } from "@/components/admin/AdminShell";
import { AnnouncementForm } from "@/components/admin/AnnouncementForm";
import { ApiError } from "@/lib/api/client";
import * as announcementApi from "@/lib/api/admin-announcements";
import type { AnnouncementResponse } from "@/types/api";

export default function EditAnnouncementPage() {
  const { id } = useParams<{ id: string }>();
  const [data, setData] = useState<AnnouncementResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    announcementApi
      .getAnnouncement(id)
      .then(setData)
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : "Không tải được thông báo."),
      );
  }, [id]);

  return (
    <AdminShell title="Sửa thông báo" description="">
      {error && (
        <p className="text-sm text-destructive">{error}</p>
      )}
      {!data && !error && (
        <p className="text-sm text-muted-foreground">Đang tải...</p>
      )}
      {data && <AnnouncementForm initial={data} />}
    </AdminShell>
  );
}
