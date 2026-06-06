"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import { AdminShell } from "@/components/admin/AdminShell";
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
      setError(err instanceof ApiError ? err.message : "Không tải được role.");
    } finally {
      setLoading(false);
    }
  }, [roleId]);

  useEffect(() => {
    load();
  }, [load]);

  const modules = useMemo(() => catalog?.modules ?? {}, [catalog]);

  const toggle = (slug: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(slug)) {
        next.delete(slug);
      } else {
        next.add(slug);
      }
      return next;
    });
  };

  const save = async () => {
    if (!role?.editable) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const updated = await rbacApi.updateRolePermissions(roleId, Array.from(selected).sort());
      setRole(updated);
      setSelected(new Set(updated.permissions));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không lưu được permission.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <AdminShell
      title={role ? `Ma trận: ${role.name}` : "Ma trận permission"}
      description="Tick các quyền gán trực tiếp cho role (kế thừa từ role cha vẫn áp dụng)"
    >
      <Link href="/admin/rbac/roles" className="mb-6 inline-block text-sm text-amber-400 hover:underline">
        ← Danh sách role
      </Link>

      {error && (
        <div className="mb-6 rounded-lg border border-red-800 bg-red-950/50 px-4 py-3 text-sm text-red-300">
          {error}
        </div>
      )}

      {loading && <p className="text-sm text-slate-400">Đang tải ma trận...</p>}

      {role && !loading && (
        <>
          <div className="mb-6 flex flex-wrap items-center gap-3">
            <span className="rounded bg-slate-800 px-2 py-1 text-xs text-slate-300">{role.slug}</span>
            {!role.editable && (
              <span className="text-xs text-amber-400">Role chỉ đọc — không thể sửa</span>
            )}
            {role.editable && (
              <button type="button" onClick={save} disabled={saving} className="btn-primary bg-amber-500">
                {saving ? "Đang lưu..." : "Lưu ma trận"}
              </button>
            )}
          </div>

          {Object.entries(modules).map(([module, permissions]) => (
            <section key={module} className="mb-8 overflow-hidden rounded-xl border border-slate-800">
              <h2 className="border-b border-slate-800 bg-slate-950 px-4 py-3 text-sm font-semibold text-white">
                {module}
              </h2>
              <div className="divide-y divide-slate-800">
                {permissions.map((permission) => (
                  <label
                    key={permission.slug}
                    className="flex cursor-pointer items-start gap-3 px-4 py-3 hover:bg-slate-900/50"
                  >
                    <input
                      type="checkbox"
                      className="mt-1"
                      checked={selected.has(permission.slug)}
                      disabled={!role.editable}
                      onChange={() => toggle(permission.slug)}
                    />
                    <span>
                      <span className="block font-mono text-xs text-amber-300/90">{permission.slug}</span>
                      <span className="block text-sm text-slate-300">{permission.description}</span>
                    </span>
                  </label>
                ))}
              </div>
            </section>
          ))}
        </>
      )}
    </AdminShell>
  );
}
