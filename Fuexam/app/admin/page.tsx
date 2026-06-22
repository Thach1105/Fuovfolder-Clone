"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { AdminShell } from "@/components/admin/AdminShell";
import { StatCard } from "@/components/admin/StatCard";
import { ApiError } from "@/lib/api/client";
import * as adminApi from "@/lib/api/admin";
import type { AdminOverviewResponse } from "@/types/api";

export default function AdminDashboardPage() {
  const [overview, setOverview] = useState<AdminOverviewResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    adminApi
      .getAdminOverview()
      .then(setOverview)
      .catch((err) => {
        if (err instanceof ApiError) {
          setError(err.message);
        } else {
          setError("Không tải được dữ liệu tổng quan.");
        }
      })
      .finally(() => setLoading(false));
  }, []);

  return (
    <AdminShell
      title="Tổng quan"
      description="Thống kê người dùng và trạng thái hệ thống"
    >
      {loading && (
        <p className="text-sm text-slate-400">Đang tải thống kê...</p>
      )}
      {error && (
        <div className="mb-6 rounded-lg border border-red-800 bg-red-950/50 px-4 py-3 text-sm text-red-300">
          {error}
        </div>
      )}
      {overview && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            <StatCard label="Tổng người dùng" value={overview.totalUsers} />
            <StatCard
              label="Đang hoạt động"
              value={overview.activeUsers}
              accent="success"
            />
            <StatCard
              label="Chờ xác minh email"
              value={overview.pendingVerificationUsers}
              accent="warning"
            />
            <StatCard
              label="Đã vô hiệu"
              value={overview.disabledUsers}
              accent="danger"
            />
            <StatCard label="Super Admin" value={overview.superAdminUsers} accent="danger" />
            <StatCard label="Quản trị (ADMIN)" value={overview.adminUsers} />
            <StatCard label="Phó quản trị (SUB_ADMIN)" value={overview.subAdminUsers} />
          </div>

          <section className="mt-10 grid gap-4 lg:grid-cols-3">
            <div className="rounded-xl border border-slate-800 bg-slate-950 p-6">
              <h2 className="text-sm font-semibold text-white">Quản lý nhanh</h2>
              <p className="mt-1 text-sm text-slate-500">
                Xem danh sách tài khoản, vai trò và trạng thái xác minh email.
              </p>
              <Link
                href="/admin/users"
                className="btn-primary mt-4 inline-flex bg-amber-500 hover:bg-amber-400"
              >
                Danh sách người dùng
              </Link>
            </div>
            <div className="rounded-xl border border-dashed border-slate-700 bg-slate-950/50 p-6">
              <h2 className="text-sm font-semibold text-slate-300">Gói Membership</h2>
              <p className="mt-1 text-sm text-slate-500">
                Cấu hình giá Fuexam Point, role gắn kèm và thời hạn từng gói bán.
              </p>
              <Link
                href="/admin/membership/plans"
                className="btn-secondary mt-4 inline-flex border-slate-700 bg-slate-900 text-slate-300"
              >
                Quản lý gói membership
              </Link>
            </div>
            <div className="rounded-xl border border-dashed border-slate-700 bg-slate-950/50 p-6">
              <h2 className="text-sm font-semibold text-slate-300">Phân quyền RBAC</h2>
              <p className="mt-1 text-sm text-slate-500">
                Chỉnh ma trận permission theo role hoặc gán quyền cho từng user.
              </p>
              <Link
                href="/admin/rbac/roles"
                className="btn-secondary mt-4 inline-flex border-slate-700 bg-slate-900 text-slate-300"
              >
                Ma trận phân quyền
              </Link>
            </div>
          </section>
        </>
      )}
    </AdminShell>
  );
}
