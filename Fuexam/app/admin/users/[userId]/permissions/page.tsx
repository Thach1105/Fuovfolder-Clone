"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { AdminShell } from "@/components/admin/AdminShell";
import { ApiError } from "@/lib/api/client";
import * as rbacApi from "@/lib/api/rbac";
import type { EffectivePermissions, RoleSummaryResponse } from "@/types/api";

export default function AdminUserPermissionsPage() {
  const params = useParams<{ userId: string }>();
  const userId = params.userId;

  const [rolesCatalog, setRolesCatalog] = useState<RoleSummaryResponse[]>([]);
  const [assignedRoles, setAssignedRoles] = useState<string[]>([]);
  const [effective, setEffective] = useState<EffectivePermissions | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [catalog, assigned, permissions] = await Promise.all([
        rbacApi.listRoles(),
        rbacApi.getUserRoles(userId),
        rbacApi.getUserEffectivePermissions(userId),
      ]);
      setRolesCatalog(catalog);
      setAssignedRoles(assigned);
      setEffective(permissions);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được quyền user.");
    } finally {
      setLoading(false);
    }
  }, [userId]);

  useEffect(() => {
    load();
  }, [load]);

  const toggleRole = (slug: string) => {
    setAssignedRoles((prev) =>
      prev.includes(slug) ? prev.filter((s) => s !== slug) : [...prev, slug],
    );
  };

  const saveRoles = async () => {
    setSaving(true);
    setError(null);
    try {
      const updated = await rbacApi.updateUserRoles(userId, assignedRoles);
      setAssignedRoles(updated);
      setEffective(await rbacApi.getUserEffectivePermissions(userId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không lưu được role.");
    } finally {
      setSaving(false);
    }
  };

  const assignableRoles = rolesCatalog.filter((r) => r.roleType !== "system" || r.slug === "USER");

  return (
    <AdminShell title="Quyền người dùng" description={`User ID: ${userId}`}>
      <Link href="/admin/users" className="mb-6 inline-block text-sm text-amber-400 hover:underline">
        ← Danh sách người dùng
      </Link>

      {error && (
        <div className="mb-6 rounded-lg border border-red-800 bg-red-950/50 px-4 py-3 text-sm text-red-300">
          {error}
        </div>
      )}

      {loading && <p className="text-sm text-slate-400">Đang tải...</p>}

      {!loading && (
        <div className="grid gap-8 lg:grid-cols-2">
          <section className="rounded-xl border border-slate-800 bg-slate-950 p-6">
            <h2 className="text-sm font-semibold text-white">Gán role</h2>
            <div className="mt-4 space-y-2">
              {assignableRoles.map((role) => (
                <label key={role.id} className="flex items-center gap-2 text-sm text-slate-300">
                  <input
                    type="checkbox"
                    checked={assignedRoles.includes(role.slug)}
                    onChange={() => toggleRole(role.slug)}
                  />
                  {role.name} ({role.slug})
                </label>
              ))}
            </div>
            <button type="button" onClick={saveRoles} disabled={saving} className="btn-primary mt-4 bg-amber-500">
              {saving ? "Đang lưu..." : "Lưu role"}
            </button>
          </section>

          <section className="rounded-xl border border-slate-800 bg-slate-950 p-6">
            <h2 className="text-sm font-semibold text-white">Effective permissions</h2>
            <p className="mt-1 text-xs text-slate-500">
              {effective?.permissions.length ?? 0} quyền · v{effective?.permVersion}
            </p>
            <ul className="mt-4 max-h-96 space-y-1 overflow-auto text-xs font-mono text-slate-400">
              {effective?.permissions.map((p) => (
                <li key={p}>{p}</li>
              ))}
            </ul>
          </section>
        </div>
      )}
    </AdminShell>
  );
}
