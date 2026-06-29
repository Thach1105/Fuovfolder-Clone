"use client";

import { useCallback, useEffect, useState } from "react";
import { AdminShell } from "@/components/admin/AdminShell";
import { AdminUsersTable } from "@/components/admin/AdminUsersTable";
import { ErrorBanner } from "@/components/ui/error-banner";
import { LoadingState } from "@/components/ui/loading-state";
import { useAsyncAction } from "@/hooks/use-async-action";
import * as adminApi from "@/lib/api/admin";
import type { AdminUserPageResponse } from "@/types/api";

export default function AdminUsersPage() {
  const [data, setData] = useState<AdminUserPageResponse | null>(null);
  const [page, setPage] = useState(0);
  const { loading, error, run } = useAsyncAction("Không tải được danh sách người dùng.");

  const load = useCallback((pageIndex: number) => run(async () => {
    const response = await adminApi.listAdminUsers(pageIndex, 20);
    setData(response);
    setPage(response.page);
  }), [run]);

  useEffect(() => {
    load(page);
  }, [load, page]);

  return (
    <AdminShell
      title="Người dùng"
      description="Danh sách tài khoản đã đăng ký trên hệ thống"
    >
      <ErrorBanner message={error} className="mb-6" />

      {loading && !data && <LoadingState message="Đang tải danh sách..." />}

      {data && (
        <>
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm text-slate-400">
              {data.totalElements} tài khoản · trang {data.page + 1}/{Math.max(data.totalPages, 1)}
            </p>
            <div className="flex gap-2">
              <button
                type="button"
                disabled={page <= 0 || loading}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="btn-secondary border-slate-700 bg-slate-900 text-slate-300 hover:bg-slate-800 disabled:opacity-40"
              >
                Trước
              </button>
              <button
                type="button"
                disabled={loading || data.page + 1 >= data.totalPages}
                onClick={() => setPage((p) => p + 1)}
                className="btn-secondary border-slate-700 bg-slate-900 text-slate-300 hover:bg-slate-800 disabled:opacity-40"
              >
                Sau
              </button>
            </div>
          </div>
          <AdminUsersTable users={data.items} />
        </>
      )}
    </AdminShell>
  );
}
