"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { AdminShell } from "@/components/admin/AdminShell";
import { ApiError } from "@/lib/api/client";
import * as rbacApi from "@/lib/api/rbac";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";
import type { RoleSummaryResponse } from "@/types/api";

export default function AdminRolesPage() {
  const { user } = useAuth();
  const [roles, setRoles] = useState<RoleSummaryResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const canCreate = can(user, "rbac.role:update");

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setRoles(await rbacApi.listRoles());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được danh sách role.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const grouped = useMemo(() => {
    const map = new Map<string, RoleSummaryResponse[]>();
    for (const role of roles) {
      const key = role.roleType;
      if (!map.has(key)) {
        map.set(key, []);
      }
      map.get(key)!.push(role);
    }
    return map;
  }, [roles]);

  return (
    <AdminShell title="Phân quyền" description="Quản lý role và ma trận permission">
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-slate-400">
          {roles.length} role hiển thị (ẩn SUPER_ADMIN)
        </p>
        {canCreate && (
          <Link
            href="/admin/rbac/roles/new"
            className="btn-primary bg-amber-500 hover:bg-amber-400"
          >
            + Tạo role mới
          </Link>
        )}
      </div>

      {error && (
        <div className="mb-6 rounded-lg border border-red-800 bg-red-950/50 px-4 py-3 text-sm text-red-300">
          {error}
        </div>
      )}
      {loading && <p className="text-sm text-slate-400">Đang tải...</p>}
      {!loading &&
        Array.from(grouped.entries()).map(([type, items]) => (
          <section key={type} className="mb-8">
            <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-400">{type}</h2>
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
              {items.map((role) => (
                <Link
                  key={role.id}
                  href={`/admin/rbac/roles/${role.id}`}
                  className="rounded-xl border border-slate-800 bg-slate-950 p-4 transition hover:border-amber-500/40"
                >
                  <p className="font-semibold text-white">{role.name}</p>
                  <p className="text-xs text-slate-500">{role.slug}</p>
                  <div className="mt-2 flex flex-wrap gap-2">
                    <p className="text-sm text-amber-300/90">{role.permissionCount} permission trực tiếp</p>
                    {!role.system && (
                      <span className="rounded bg-slate-800 px-2 py-0.5 text-[10px] uppercase tracking-wide text-slate-400">
                        Tùy chỉnh
                      </span>
                    )}
                  </div>
                </Link>
              ))}
            </div>
          </section>
        ))}
    </AdminShell>
  );
}
