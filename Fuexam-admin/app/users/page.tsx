"use client";

import { useCallback, useEffect, useState } from "react";
import { AdminShell } from "@/components/admin/AdminShell";
import { AdminUsersTable } from "@/components/admin/AdminUsersTable";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api/client";
import { listAdminUsers } from "@/lib/api/admin";
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
      const response = await listAdminUsers(pageIndex, 20);
      setData(response);
      setPage(response.page);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được danh sách người dùng.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load(page);
  }, [load, page]);

  return (
    <AdminShell title="Người dùng" description="Danh sách tài khoản đã đăng ký trên hệ thống">
      {error && (
        <div className="mb-6 rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      )}

      {loading && !data && <p className="text-sm text-muted-foreground">Đang tải danh sách...</p>}

      {data && (
        <>
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm text-muted-foreground">
              {data.totalElements.toLocaleString("vi-VN")} tài khoản · trang {data.page + 1}/
              {Math.max(data.totalPages, 1)}
            </p>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={page <= 0 || loading}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                Trước
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={loading || data.page + 1 >= data.totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                Sau
              </Button>
            </div>
          </div>
          <AdminUsersTable users={data.items} />
        </>
      )}
    </AdminShell>
  );
}
