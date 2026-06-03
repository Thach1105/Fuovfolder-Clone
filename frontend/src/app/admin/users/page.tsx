"use client";

import { useCallback, useEffect, useState } from "react";
import { AdminShell } from "@/components/admin/AdminShell";
import { AdminUsersTable } from "@/components/admin/AdminUsersTable";
import { ApiError } from "@/lib/api/client";
import * as adminApi from "@/lib/api/admin";
import type { AdminUserPageResponse } from "@/types/api";

export default function AdminUsersPage() {
  const [data, setData] = useState<AdminUserPageResponse | null>(null);
  const [page, setPage] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async (pageIndex: number) => {
    setLoading(true);
    setError(null);
    try {
      const response = await adminApi.listAdminUsers(pageIndex, 20);
      setData(response);
      setPage(response.page);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("Không tải được danh sách người dùng.");
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load(page);
  }, [load, page]);

  return (
    <AdminShell
      title="Người dùng"
      description="Danh sách tài khoản đã đăng ký trên hệ thống"
    >
      {error && (
        <div className="mb-6 rounded-lg border border-red-800 bg-red-950/50 px-4 py-3 text-sm text-red-300">
          {error}
        </div>
      )}

      {loading && !data && (
        <p className="text-sm text-slate-400">Đang tải danh sách...</p>
      )}

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
