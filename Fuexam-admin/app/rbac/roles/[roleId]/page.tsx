"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { AdminShell } from "@/components/admin/AdminShell";
import { PermissionMatrix } from "@/components/admin/PermissionMatrix";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api/client";
import * as rbacApi from "@/lib/api/rbac";
import type { PermissionCatalogResponse, RoleDetailResponse } from "@/types/api";

export default function AdminRoleMatrixPage() {
  const params = useParams<{ roleId: string }>();
  const roleId = params.roleId;

  const [role, setRole] = useState<RoleDetailResponse | null>(null);
  const [catalog, setCatalog] = useState<PermissionCatalogResponse | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [roleData, catalogData] = await Promise.all([
        rbacApi.getRole(roleId),
        rbacApi.listPermissions(),
      ]);
      setRole(roleData);
      setCatalog(catalogData);
      setSelected(new Set(roleData.permissions));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được vai trò.");
    } finally {
      setLoading(false);
    }
  }, [roleId]);

  useEffect(() => {
    load();
  }, [load]);

  const toggle = (slug: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(slug)) next.delete(slug);
      else next.add(slug);
      return next;
    });
  };

  const save = async () => {
    if (!role?.editable) return;
    setSaving(true);
    setError(null);
    try {
      const updated = await rbacApi.updateRolePermissions(roleId, Array.from(selected).sort());
      setRole(updated);
      setSelected(new Set(updated.permissions));
      toast.success("Đã lưu ma trận quyền.");
    } catch (err) {
      const message = err instanceof ApiError ? err.message : "Không lưu được quyền.";
      setError(message);
      toast.error(message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <AdminShell
      title={role ? `Vai trò: ${role.name}` : "Ma trận quyền"}
      description="Tick các quyền gán trực tiếp (quyền kế thừa từ vai trò cha vẫn áp dụng)"
      actions={
        role?.editable ? (
          <Button size="sm" onClick={save} disabled={saving}>
            {saving ? "Đang lưu..." : "Lưu ma trận"}
          </Button>
        ) : undefined
      }
    >
      <Link href="/rbac/roles" className="mb-6 inline-block text-sm text-primary hover:underline">
        ← Danh sách vai trò
      </Link>

      {error && (
        <div className="mb-6 rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      )}

      {loading && <p className="text-sm text-muted-foreground">Đang tải ma trận...</p>}

      {role && !loading && (
        <>
          <div className="mb-6 flex flex-wrap items-center gap-3">
            <span className="rounded bg-muted px-2 py-1 text-xs text-muted-foreground">
              {role.slug}
            </span>
            {!role.editable && (
              <span className="text-xs text-amber-500">Vai trò chỉ đọc — không thể sửa</span>
            )}
          </div>

          <PermissionMatrix
            catalog={catalog}
            selected={selected}
            onToggle={toggle}
            disabled={!role.editable}
          />
        </>
      )}
    </AdminShell>
  );
}
