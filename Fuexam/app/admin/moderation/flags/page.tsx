"use client";

import { useCallback, useEffect, useState } from "react";
import { AdminShell } from "@/components/admin/AdminShell";
import { ErrorBanner } from "@/components/ui/error-banner";
import { LoadingState } from "@/components/ui/loading-state";
import { useAsyncAction } from "@/hooks/use-async-action";
import { ApiError } from "@/lib/api/client";
import {
  type FlagItem,
  listModerationFlags,
  resolveModerationFlag,
} from "@/lib/api/moderation";
import { formatDateTime } from "@/lib/format-datetime";

export default function AdminModerationFlagsPage() {
  const [items, setItems] = useState<FlagItem[]>([]);
  const { loading, error: loadError, run } = useAsyncAction("Không tải được báo cáo");
  const [actionError, setActionError] = useState<string | null>(null);
  const [resolvingId, setResolvingId] = useState<string | null>(null);

  const error = loadError || actionError;

  const load = useCallback(() => run(async () => {
    const page = await listModerationFlags("open", 0, 50);
    setItems(page.items);
  }), [run]);

  useEffect(() => {
    load();
  }, [load]);

  async function resolve(flagId: string, action: string) {
    setResolvingId(flagId);
    setActionError(null);
    try {
      await resolveModerationFlag(flagId, action);
      await load();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Không xử lý được báo cáo");
    } finally {
      setResolvingId(null);
    }
  }

  return (
    <AdminShell title="Moderation — Báo cáo" description="Xử lý báo cáo nội dung từ thành viên">
      <ErrorBanner message={error} className="mb-4" />

      {loading ? (
        <LoadingState />
      ) : items.length === 0 ? (
        <p className="text-sm text-slate-400">Không có báo cáo đang mở.</p>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-slate-800">
          <table className="min-w-full text-sm">
            <thead className="bg-slate-900 text-left text-slate-400">
              <tr>
                <th className="px-3 py-2">Mục tiêu</th>
                <th className="px-3 py-2">Lý do</th>
                <th className="px-3 py-2">Thời gian</th>
                <th className="px-3 py-2">Hành động</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.id} className="border-t border-slate-800 text-slate-200">
                  <td className="px-3 py-2">
                    {item.targetType} · {item.targetId.slice(0, 8)}...
                  </td>
                  <td className="px-3 py-2">{item.reason}</td>
                  <td className="px-3 py-2">{formatDateTime(item.createdAt)}</td>
                  <td className="px-3 py-2">
                    <div className="flex flex-wrap gap-2">
                      {item.targetType === "post" && (
                        <>
                          <button
                            type="button"
                            className="btn-secondary text-xs"
                            disabled={resolvingId === item.id}
                            onClick={() => resolve(item.id, "hide")}
                          >
                            Ẩn
                          </button>
                          <button
                            type="button"
                            className="btn-secondary text-xs"
                            disabled={resolvingId === item.id}
                            onClick={() => resolve(item.id, "delete")}
                          >
                            Xóa
                          </button>
                        </>
                      )}
                      {item.targetType === "thread" && (
                        <button
                          type="button"
                          className="btn-secondary text-xs"
                          disabled={resolvingId === item.id}
                          onClick={() => resolve(item.id, "lock_thread")}
                        >
                          Khóa thread
                        </button>
                      )}
                      <button
                        type="button"
                        className="btn-secondary text-xs"
                        disabled={resolvingId === item.id}
                        onClick={() => resolve(item.id, "reject")}
                      >
                        Bác bỏ
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </AdminShell>
  );
}
