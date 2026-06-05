"use client";

import { useCallback, useEffect, useState } from "react";
import { AdminShell } from "@/components/admin/AdminShell";
import { ApiError } from "@/lib/api/client";
import {
  type SyncRun,
  listSyncRuns,
  triggerForumSync,
} from "@/lib/api/forum";
import { formatDateTime } from "@/lib/format-datetime";

const STATUS_LABELS: Record<string, string> = {
  running: "Đang chạy",
  succeeded: "Thành công",
  failed: "Thất bại",
};

const selectClass =
  "rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white min-w-[160px]";

export default function AdminForumSyncPage() {
  const [runs, setRuns] = useState<SyncRun[]>([]);
  const [loading, setLoading] = useState(true);
  const [syncing, setSyncing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [mode, setMode] = useState<"public" | "authenticated">("public");
  const [scope, setScope] = useState<"incremental" | "full">("incremental");
  const [cookie, setCookie] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const page = await listSyncRuns(0, 30);
      setRuns(page.items);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được lịch sử đồng bộ");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function handleSync() {
    setSyncing(true);
    setError(null);
    setMessage(null);
    try {
      const run = await triggerForumSync({
        mode,
        scope,
        cookie: mode === "authenticated" && cookie.trim() ? cookie.trim() : undefined,
      });
      setMessage(
        `Đồng bộ ${run.status}: ${run.threadsSynced} chủ đề, ${run.postsSynced} bài viết.`,
      );
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không thể chạy đồng bộ");
    } finally {
      setSyncing(false);
    }
  }

  return (
    <AdminShell title="Đồng bộ Diễn đàn">
      <div className="space-y-6">
        <section className="rounded-xl border border-slate-800 bg-slate-900/50 p-5">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
            Chạy đồng bộ
          </h2>
          <p className="mt-1 text-sm text-slate-500">
            Crawl dữ liệu từ fuoverflow.com và lưu vào forum / thread / post.
          </p>

          <div className="mt-4 flex flex-wrap items-end gap-4">
            <div>
              <label className="text-xs font-medium text-slate-500" htmlFor="sync-mode">
                Chế độ
              </label>
              <select
                id="sync-mode"
                className={`${selectClass} mt-1`}
                value={mode}
                onChange={(e) => setMode(e.target.value as "public" | "authenticated")}
              >
                <option value="public">Public (không đăng nhập)</option>
                <option value="authenticated">Authenticated (có cookie)</option>
              </select>
            </div>
            <div>
              <label className="text-xs font-medium text-slate-500" htmlFor="sync-scope">
                Phạm vi
              </label>
              <select
                id="sync-scope"
                className={`${selectClass} mt-1`}
                value={scope}
                onChange={(e) => setScope(e.target.value as "incremental" | "full")}
              >
                <option value="incremental">Incremental (trang đầu)</option>
                <option value="full">Full (nhiều trang hơn)</option>
              </select>
            </div>
            <button
              type="button"
              className="rounded-lg bg-amber-500 px-4 py-2 text-sm font-semibold text-slate-950 transition hover:bg-amber-400 disabled:opacity-50"
              disabled={syncing}
              onClick={handleSync}
            >
              {syncing ? "Đang đồng bộ..." : "Bắt đầu đồng bộ"}
            </button>
          </div>

          {mode === "authenticated" && (
            <div className="mt-4">
              <label className="text-xs font-medium text-slate-500" htmlFor="sync-cookie">
                Session cookie (tùy chọn nếu đã cấu hình env)
              </label>
              <input
                id="sync-cookie"
                className="mt-1 w-full max-w-xl rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white"
                placeholder="xf_user=...; xf_session=..."
                value={cookie}
                onChange={(e) => setCookie(e.target.value)}
              />
            </div>
          )}

          {message && (
            <p className="mt-4 rounded-lg bg-emerald-950/50 px-3 py-2 text-sm text-emerald-300">
              {message}
            </p>
          )}
          {error && (
            <p className="mt-4 rounded-lg bg-red-950/50 px-3 py-2 text-sm text-red-300">
              {error}
            </p>
          )}
        </section>

        <section className="rounded-xl border border-slate-800 bg-slate-900/50 p-5">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
            Lịch sử đồng bộ
          </h2>
          {loading ? (
            <p className="mt-4 text-sm text-slate-500">Đang tải...</p>
          ) : runs.length === 0 ? (
            <p className="mt-4 text-sm text-slate-500">Chưa có lần đồng bộ nào.</p>
          ) : (
            <div className="mt-4 overflow-x-auto">
              <table className="w-full min-w-[720px] text-sm">
                <thead>
                  <tr className="border-b border-slate-800 text-left text-xs uppercase text-slate-500">
                    <th className="py-2 pr-3">Thời gian</th>
                    <th className="py-2 pr-3">Mode</th>
                    <th className="py-2 pr-3">Scope</th>
                    <th className="py-2 pr-3">Trạng thái</th>
                    <th className="py-2 pr-3 text-right">Forum</th>
                    <th className="py-2 pr-3 text-right">Thread</th>
                    <th className="py-2 pr-3 text-right">Post</th>
                  </tr>
                </thead>
                <tbody>
                  {runs.map((run) => (
                    <tr key={run.id} className="border-b border-slate-800/60 text-slate-300">
                      <td className="py-2.5 pr-3">{formatDateTime(run.startedAt)}</td>
                      <td className="py-2.5 pr-3">{run.mode}</td>
                      <td className="py-2.5 pr-3">{run.scope}</td>
                      <td className="py-2.5 pr-3">
                        <span
                          className={
                            run.status === "succeeded"
                              ? "text-emerald-400"
                              : run.status === "failed"
                                ? "text-red-400"
                                : "text-amber-400"
                          }
                        >
                          {STATUS_LABELS[run.status] ?? run.status}
                        </span>
                        {run.errorMessage && (
                          <p className="mt-0.5 max-w-xs truncate text-xs text-red-400/80">
                            {run.errorMessage}
                          </p>
                        )}
                      </td>
                      <td className="py-2.5 pr-3 text-right">{run.forumsSynced}</td>
                      <td className="py-2.5 pr-3 text-right">{run.threadsSynced}</td>
                      <td className="py-2.5 pr-3 text-right">{run.postsSynced}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>
    </AdminShell>
  );
}
