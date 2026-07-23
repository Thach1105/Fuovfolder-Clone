"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { AdminShell } from "@/components/admin/AdminShell";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ApiError } from "@/lib/api/client";
import * as announcementApi from "@/lib/api/admin-announcements";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";
import type { AnnouncementResponse } from "@/types/api";

const STATUS_TABS = [
  { value: "", label: "Tất cả" },
  { value: "DRAFT", label: "Nháp" },
  { value: "SCHEDULED", label: "Đã lên lịch" },
  { value: "ACTIVE", label: "Đang chạy" },
  { value: "EXPIRED", label: "Đã hết hạn" },
];

const STATUS_BADGE: Record<string, string> = {
  DRAFT: "bg-muted text-muted-foreground",
  SCHEDULED: "bg-amber-500/15 text-amber-500",
  ACTIVE: "bg-emerald-500/15 text-emerald-500",
  EXPIRED: "bg-red-500/15 text-red-500",
};

const STATUS_LABEL: Record<string, string> = {
  DRAFT: "Nháp",
  SCHEDULED: "Đã lên lịch",
  ACTIVE: "Đang chạy",
  EXPIRED: "Đã hết hạn",
};

function formatDate(iso: string) {
  return new Date(iso).toLocaleString("vi-VN", {
    day: "2-digit", month: "2-digit", year: "numeric",
    hour: "2-digit", minute: "2-digit",
  });
}

export default function AnnouncementsPage() {
  const { user } = useAuth();
  const router = useRouter();
  const canWrite = can(user, "announcement.admin:write");

  const [items, setItems] = useState<AnnouncementResponse[]>([]);
  const [tab, setTab] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setItems(await announcementApi.listAnnouncements(tab || undefined));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được danh sách.");
    } finally {
      setLoading(false);
    }
  }, [tab]);

  useEffect(() => { load(); }, [load]);

  const handleActivate = async (id: string) => {
    try {
      await announcementApi.activateAnnouncement(id);
      toast.success("Đã kích hoạt thông báo.");
      await load();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Thao tác thất bại.");
    }
  };

  const handleDeactivate = async (id: string) => {
    try {
      await announcementApi.deactivateAnnouncement(id);
      toast.success("Đã dừng thông báo.");
      await load();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Thao tác thất bại.");
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm("Xóa thông báo này?")) return;
    try {
      await announcementApi.deleteAnnouncement(id);
      toast.success("Đã xóa.");
      await load();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Xóa thất bại.");
    }
  };

  return (
    <AdminShell
      title="Thông báo toàn server"
      description="Quản lý thông báo marquee hiển thị cho tất cả người dùng"
    >
      <div className="mb-4 flex items-center justify-between">
        <div className="flex gap-1">
          {STATUS_TABS.map((t) => (
            <button
              key={t.value}
              onClick={() => setTab(t.value)}
              className={`rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${
                tab === t.value
                  ? "bg-primary text-primary-foreground"
                  : "bg-muted text-muted-foreground hover:bg-muted/80"
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>
        {canWrite && (
          <Button size="sm" onClick={() => router.push("/announcements/new")}>
            + Tạo thông báo
          </Button>
        )}
      </div>

      {error && (
        <div className="mb-4 rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      )}

      {loading && <p className="text-sm text-muted-foreground">Đang tải...</p>}

      {!loading && items.length === 0 && (
        <p className="text-sm text-muted-foreground">Chưa có thông báo nào.</p>
      )}

      {!loading && (
        <div className="space-y-3">
          {items.map((item) => (
            <Card key={item.id}>
              <CardContent className="p-4">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="font-semibold text-foreground">{item.title}</p>
                    <p className="mt-1 text-xs text-muted-foreground">
                      {formatDate(item.startAt)} → {formatDate(item.endAt)} &middot; lặp mỗi{" "}
                      {Math.round(item.stepSeconds / 60)} phút &middot; ưu tiên: {item.priority}
                    </p>
                  </div>
                  <span
                    className={`shrink-0 rounded px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${STATUS_BADGE[item.status] ?? ""}`}
                  >
                    {STATUS_LABEL[item.status] ?? item.status}
                  </span>
                </div>

                <div
                  className="mt-3 overflow-hidden rounded px-3 py-1.5 text-sm text-white"
                  style={{ backgroundColor: item.backgroundColor }}
                  dangerouslySetInnerHTML={{ __html: item.contentHtml }}
                />

                {canWrite && (
                  <div className="mt-3 flex items-center gap-3 text-sm">
                    <button
                      className="text-primary hover:underline"
                      onClick={() => router.push(`/announcements/${item.id}/edit`)}
                    >
                      Sửa
                    </button>
                    {(item.status === "DRAFT" || item.status === "SCHEDULED") && (
                      <button
                        className="text-emerald-500 hover:underline"
                        onClick={() => handleActivate(item.id)}
                      >
                        Kích hoạt ngay
                      </button>
                    )}
                    {item.status === "ACTIVE" && (
                      <button
                        className="text-amber-500 hover:underline"
                        onClick={() => handleDeactivate(item.id)}
                      >
                        Dừng sớm
                      </button>
                    )}
                    {(item.status === "DRAFT" || item.status === "SCHEDULED") && (
                      <button
                        className="text-destructive hover:underline"
                        onClick={() => handleDelete(item.id)}
                      >
                        Xóa
                      </button>
                    )}
                  </div>
                )}
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </AdminShell>
  );
}
