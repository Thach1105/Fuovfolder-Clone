"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { AdminShell } from "@/components/admin/AdminShell";
import { ErrorBanner } from "@/components/ui/error-banner";
import { LoadingState } from "@/components/ui/loading-state";
import { useAsyncAction } from "@/hooks/use-async-action";
import { useSubmit } from "@/hooks/use-submit";
import * as rbacApi from "@/lib/api/rbac";
import type { EffectivePermissions, RoleSummaryResponse } from "@/types/api";

export default function AdminUserPermissionsPage() {
  const params = useParams<{ userId: string }>();
  const userId = params.userId;

  const [rolesCatalog, setRolesCatalog] = useState<RoleSummaryResponse[]>([]);
  const [assignedRoles, setAssignedRoles] = useState<string[]>([]);
  const [effective, setEffective] = useState<EffectivePermissions | null>(null);
  const { loading, error: loadError, run } = useAsyncAction("Không tải được quyền user.");
  const { submitting: saving, error: saveError, submit } = useSubmit("Không lưu được role.");

  const error = loadError || saveError;

  const load = useCallback(() => run(async () => {
    const [catalog, assigned, permissions] = await Promise.all([
      rbacApi.listRoles(),
      rbacApi.getUserRoles(userId),
      rbacApi.getUserEffectivePermissions(userId),
    ]);
    setRolesCatalog(catalog);
    setAssignedRoles(assigned);
    setEffective(permissions);
  }), [run, userId]);

  useEffect(() => {
    load();
  }, [load]);

  const toggleRole = (slug: string) => {
    setAssignedRoles((prev) =>
      prev.includes(slug) ? prev.filter((s) => s !== slug) : [...prev, slug],
    );
  };

  const saveRoles = async () => {
    await submit(async () => {
      const updated = await rbacApi.updateUserRoles(userId, assignedRoles);
      setAssignedRoles(updated);
      setEffective(await rbacApi.getUserEffectivePermissions(userId));
    });
  };

  const assignableRoles = rolesCatalog.filter((r) => r.roleType !== "system" || r.slug === "USER");

  return (
    <AdminShell title="Quyền người dùng" description={`User ID: ${userId}`}>
      <Link href="/admin/users" className="mb-6 inline-block text-sm text-amber-400 hover:underline">
        ← Danh sách người dùng
      </Link>

      <ErrorBanner message={error} className="mb-6" />

      {loading && <LoadingState />}

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
