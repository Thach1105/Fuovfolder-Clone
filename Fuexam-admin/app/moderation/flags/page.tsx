"use client";

import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { AdminShell } from "@/components/admin/AdminShell";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ApiError } from "@/lib/api/client";
import { type FlagItem, listModerationFlags, resolveModerationFlag } from "@/lib/api/moderation";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";
import { formatDateTime } from "@/lib/format-datetime";

export default function AdminModerationFlagsPage() {
  const { user } = useAuth();
  const canResolve = can(user, "forum.moderation:update");

  const [items, setItems] = useState<FlagItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [resolvingId, setResolvingId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const page = await listModerationFlags("open", 0, 50);
      setItems(page.items);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được báo cáo.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function resolve(flagId: string, action: string) {
    setResolvingId(flagId);
    try {
      await resolveModerationFlag(flagId, action);
      toast.success("Đã xử lý báo cáo.");
      await load();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Không xử lý được báo cáo.");
    } finally {
      setResolvingId(null);
    }
  }

  return (
    <AdminShell title="Moderation — Báo cáo" description="Xử lý báo cáo nội dung từ thành viên">
      {error && (
        <div className="mb-4 rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      )}

      {loading ? (
        <p className="text-sm text-muted-foreground">Đang tải...</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Không có báo cáo đang mở.</p>
      ) : (
        <div className="rounded-xl border border-border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Mục tiêu</TableHead>
                <TableHead>Lý do</TableHead>
                <TableHead>Thời gian</TableHead>
                {canResolve && <TableHead className="text-right">Hành động</TableHead>}
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((item) => (
                <TableRow key={item.id}>
                  <TableCell>
                    <span className="font-medium text-foreground">{item.targetType}</span>
                    <p className="font-mono text-xs text-muted-foreground">
                      {item.targetId.slice(0, 8)}…
                    </p>
                  </TableCell>
                  <TableCell>
                    {item.reason}
                    {item.note && (
                      <p className="text-xs text-muted-foreground">{item.note}</p>
                    )}
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {formatDateTime(item.createdAt)}
                  </TableCell>
                  {canResolve && (
                    <TableCell className="text-right">
                      <div className="flex flex-wrap justify-end gap-2">
                        {item.targetType === "post" && (
                          <>
                            <Button
                              size="sm"
                              variant="outline"
                              disabled={resolvingId === item.id}
                              onClick={() => resolve(item.id, "hide")}
                            >
                              Ẩn
                            </Button>
                            <Button
                              size="sm"
                              variant="destructive"
                              disabled={resolvingId === item.id}
                              onClick={() => resolve(item.id, "delete")}
                            >
                              Xóa
                            </Button>
                          </>
                        )}
                        {item.targetType === "thread" && (
                          <Button
                            size="sm"
                            variant="outline"
                            disabled={resolvingId === item.id}
                            onClick={() => resolve(item.id, "lock_thread")}
                          >
                            Khóa thread
                          </Button>
                        )}
                        <Button
                          size="sm"
                          variant="ghost"
                          disabled={resolvingId === item.id}
                          onClick={() => resolve(item.id, "reject")}
                        >
                          Bác bỏ
                        </Button>
                      </div>
                    </TableCell>
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </AdminShell>
  );
}
