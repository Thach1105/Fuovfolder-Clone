"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import { AdminShell } from "@/components/admin/AdminShell";
import { ErrorBanner } from "@/components/ui/error-banner";
import { LoadingState } from "@/components/ui/loading-state";
import { useAsyncAction } from "@/hooks/use-async-action";
import { useSubmit } from "@/hooks/use-submit";
import * as rbacApi from "@/lib/api/rbac";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";
import type { PermissionCatalogResponse, RoleSummaryResponse } from "@/types/api";

const inputClass =
  "w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none transition focus:border-amber-500/60 focus:ring-2 focus:ring-amber-500/20";

export default function AdminCreateRolePage() {
  const router = useRouter();
  const { user } = useAuth();

  const [roles, setRoles] = useState<RoleSummaryResponse[]>([]);
  const [catalog, setCatalog] = useState<PermissionCatalogResponse | null>(null);
  const [slug, setSlug] = useState("");
  const [name, setName] = useState("");
  const [parentRoleSlug, setParentRoleSlug] = useState("USER");
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const { loading, error: loadError, run } = useAsyncAction("Không tải được dữ liệu.");
  const { submitting, error: submitError, submit } = useSubmit("Không tạo được role.");

  const error = loadError || submitError;

  const canCreate = can(user, "rbac.role:update");

  const load = useCallback(() => run(async () => {
    const [rolesData, catalogData] = await Promise.all([
      rbacApi.listRoles(),
      rbacApi.listPermissions(),
    ]);
    setRoles(rolesData);
    setCatalog(catalogData);
  }), [run]);

  useEffect(() => {
    load();
  }, [load]);

  const parentOptions = roles;

  const modules = useMemo(() => catalog?.modules ?? {}, [catalog]);

  const toggle = (permissionSlug: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(permissionSlug)) {
        next.delete(permissionSlug);
      } else {
        next.add(permissionSlug);
      }
      return next;
    });
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!canCreate) {
      return;
    }
    const trimmedSlug = slug.trim();
    const trimmedName = name.trim();
    if (!trimmedSlug || !trimmedName) {
      return;
    }

    const created = await submit(async () => {
      return await rbacApi.createRole({
        slug: trimmedSlug,
        name: trimmedName,
        parentRoleSlug: parentRoleSlug || null,
        permissions: Array.from(selected).sort(),
      });
    });
    if (created) {
      router.push(`/admin/rbac/roles/${created.id}`);
    }
  };

  if (!canCreate) {
    return (
      <AdminShell title="Tạo role mới" description="Thêm role STAFF tùy chỉnh">
        <Link href="/admin/rbac/roles" className="mb-6 inline-block text-sm text-amber-400 hover:underline">
          ← Danh sách role
        </Link>
        <p className="text-sm text-slate-400">Bạn không có quyền tạo role mới (`rbac.role:update`).</p>
      </AdminShell>
    );
  }

  return (
    <AdminShell
      title="Tạo role mới"
      description="Role tùy chỉnh thuộc loại STAFF; permission có thể kế thừa từ role cha"
    >
      <Link href="/admin/rbac/roles" className="mb-6 inline-block text-sm text-amber-400 hover:underline">
        ← Danh sách role
      </Link>

      <ErrorBanner message={error} className="mb-6" />

      {loading && <LoadingState />}

      {!loading && (
        <form onSubmit={handleSubmit} className="space-y-8">
          <section className="rounded-xl border border-slate-800 bg-slate-950 p-6">
            <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-400">
              Thông tin cơ bản
            </h2>
            <div className="grid gap-4 md:grid-cols-2">
              <label className="block">
                <span className="mb-1 block text-sm text-slate-300">Slug</span>
                <input
                  className={inputClass}
                  value={slug}
                  onChange={(e) => setSlug(e.target.value)}
                  placeholder="MODERATOR"
                  required
                />
                <span className="mt-1 block text-xs text-slate-500">
                  Hệ thống tự chuẩn hóa thành UPPER_SNAKE_CASE
                </span>
              </label>
              <label className="block">
                <span className="mb-1 block text-sm text-slate-300">Tên hiển thị</span>
                <input
                  className={inputClass}
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="Moderator"
                  required
                />
              </label>
              <label className="block md:col-span-2">
                <span className="mb-1 block text-sm text-slate-300">Role cha (kế thừa permission)</span>
                <select
                  className={inputClass}
                  value={parentRoleSlug}
                  onChange={(e) => setParentRoleSlug(e.target.value)}
                >
                  <option value="">Không có role cha</option>
                  {parentOptions.map((role) => (
                    <option key={role.id} value={role.slug}>
                      {role.name} ({role.slug})
                    </option>
                  ))}
                </select>
              </label>
            </div>
          </section>

          <section>
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                  Permission trực tiếp
                </h2>
                <p className="mt-1 text-sm text-slate-500">
                  Đã chọn {selected.size} quyền — có thể chỉnh thêm sau khi tạo
                </p>
              </div>
              <button type="submit" disabled={submitting} className="btn-primary bg-amber-500">
                {submitting ? "Đang tạo..." : "Tạo role"}
              </button>
            </div>

            {Object.entries(modules).map(([module, permissions]) => (
              <section key={module} className="mb-6 overflow-hidden rounded-xl border border-slate-800">
                <h3 className="border-b border-slate-800 bg-slate-950 px-4 py-3 text-sm font-semibold text-white">
                  {module}
                </h3>
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
          </section>
        </form>
      )}
    </AdminShell>
  );
}
