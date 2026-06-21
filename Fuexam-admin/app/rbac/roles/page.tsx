"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { AdminShell } from "@/components/admin/AdminShell";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
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
      setError(err instanceof ApiError ? err.message : "Không tải được danh sách vai trò.");
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
      if (!map.has(role.roleType)) map.set(role.roleType, []);
      map.get(role.roleType)!.push(role);
    }
    return map;
  }, [roles]);

  return (
    <AdminShell
      title="Vai trò & quyền"
      description="Quản lý vai trò và ma trận permission của hệ thống"
      actions={
        canCreate ? (
          <Button asChild size="sm">
            <Link href="/rbac/roles/new">+ Tạo vai trò</Link>
          </Button>
        ) : undefined
      }
    >
      {error && (
        <div className="mb-6 rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      )}

      <p className="mb-6 text-sm text-muted-foreground">
        {roles.length} vai trò hiển thị (ẩn SUPER_ADMIN)
      </p>

      {loading && <p className="text-sm text-muted-foreground">Đang tải...</p>}

      {!loading &&
        Array.from(grouped.entries()).map(([type, items]) => (
          <section key={type} className="mb-8">
            <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-muted-foreground">
              {type}
            </h2>
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
              {items.map((role) => (
                <Link key={role.id} href={`/rbac/roles/${role.id}`}>
                  <Card className="h-full transition-colors hover:border-primary/50">
                    <CardContent className="p-4">
                      <p className="font-semibold text-foreground">{role.name}</p>
                      <p className="text-xs text-muted-foreground">{role.slug}</p>
                      <div className="mt-2 flex flex-wrap items-center gap-2">
                        <span className="text-sm text-primary">
                          {role.permissionCount} quyền trực tiếp
                        </span>
                        {!role.system && (
                          <span className="rounded bg-muted px-2 py-0.5 text-[10px] uppercase tracking-wide text-muted-foreground">
                            Tùy chỉnh
                          </span>
                        )}
                        {!role.editable && (
                          <span className="rounded bg-muted px-2 py-0.5 text-[10px] uppercase tracking-wide text-muted-foreground">
                            Chỉ đọc
                          </span>
                        )}
                      </div>
                    </CardContent>
                  </Card>
                </Link>
              ))}
            </div>
          </section>
        ))}
    </AdminShell>
  );
}
