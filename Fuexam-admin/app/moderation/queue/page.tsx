"use client";

import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { AdminShell } from "@/components/admin/AdminShell";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ApiError } from "@/lib/api/client";
import {
  type ModerationPost,
  approveModerationPost,
  listModerationQueue,
  rejectModerationPost,
} from "@/lib/api/moderation";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";
import { formatDateTime } from "@/lib/format-datetime";

export default function AdminModerationQueuePage() {
  const { user } = useAuth();
  const canAct = can(user, "forum.moderation:update");

  const [items, setItems] = useState<ModerationPost[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actingId, setActingId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const page = await listModerationQueue(0, 50);
      setItems(page.items);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được hàng chờ.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function act(postId: string, kind: "approve" | "reject") {
    setActingId(postId);
    try {
      if (kind === "approve") {
        await approveModerationPost(postId);
        toast.success("Đã duyệt bài.");
      } else {
        await rejectModerationPost(postId);
        toast.success("Đã từ chối bài.");
      }
      await load();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Thao tác thất bại.");
    } finally {
      setActingId(null);
    }
  }

  return (
    <AdminShell
      title="Moderation — Hàng chờ duyệt"
      description="Duyệt bài viết từ thành viên chưa VIP"
    >
      {error && (
        <div className="mb-4 rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      )}

      {loading ? (
        <p className="text-sm text-muted-foreground">Đang tải...</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Không có bài chờ duyệt.</p>
      ) : (
        <div className="space-y-4">
          {items.map((post) => (
            <Card key={post.id}>
              <CardContent className="p-4">
                <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                  <div className="text-sm text-muted-foreground">
                    <span className="font-medium text-foreground">
                      {post.authorHandle ?? "Thành viên"}
                    </span>
                    <span className="mx-2">·</span>
                    {formatDateTime(post.createdAt)}
                  </div>
                  {canAct && (
                    <div className="flex gap-2">
                      <Button
                        size="sm"
                        disabled={actingId === post.id}
                        onClick={() => act(post.id, "approve")}
                      >
                        Duyệt
                      </Button>
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={actingId === post.id}
                        onClick={() => act(post.id, "reject")}
                      >
                        Từ chối
                      </Button>
                    </div>
                  )}
                </div>
                <div
                  className="prose prose-sm max-w-none dark:prose-invert"
                  dangerouslySetInnerHTML={{ __html: post.bodyHtml }}
                />
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </AdminShell>
  );
}
