"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { AdminShell } from "@/components/admin/AdminShell";
import { PermissionMatrix } from "@/components/admin/PermissionMatrix";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ApiError } from "@/lib/api/client";
import * as rbacApi from "@/lib/api/rbac";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";
import type { PermissionCatalogResponse, RoleSummaryResponse } from "@/types/api";

const NO_PARENT = "none";

export default function AdminCreateRolePage() {
  const router = useRouter();
  const { user } = useAuth();
  const canCreate = can(user, "rbac.role:update");

  const [roles, setRoles] = useState<RoleSummaryResponse[]>([]);
  const [catalog, setCatalog] = useState<PermissionCatalogResponse | null>(null);
  const [slug, setSlug] = useState("");
  const [name, setName] = useState("");
  const [parentRoleSlug, setParentRoleSlug] = useState<string>("USER");
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [rolesData, catalogData] = await Promise.all([
        rbacApi.listRoles(),
        rbacApi.listPermissions(),
      ]);
      setRoles(rolesData);
      setCatalog(catalogData);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được dữ liệu.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const toggle = (permissionSlug: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(permissionSlug)) next.delete(permissionSlug);
      else next.add(permissionSlug);
      return next;
    });
  };

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!canCreate) return;
    const trimmedSlug = slug.trim();
    const trimmedName = name.trim();
    if (!trimmedSlug || !trimmedName) {
      setError("Vui lòng nhập slug và tên vai trò.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const created = await rbacApi.createRole({
        slug: trimmedSlug,
        name: trimmedName,
        parentRoleSlug: parentRoleSlug === NO_PARENT ? null : parentRoleSlug,
        permissions: Array.from(selected).sort(),
      });
      toast.success("Đã tạo vai trò.");
      router.push(`/rbac/roles/${created.id}`);
    } catch (err) {
      const message = err instanceof ApiError ? err.message : "Không tạo được vai trò.";
      setError(message);
      toast.error(message);
      setSubmitting(false);
    }
  };

  if (!canCreate) {
    return (
      <AdminShell title="Tạo vai trò" description="Thêm vai trò STAFF tùy chỉnh">
        <Link href="/rbac/roles" className="mb-6 inline-block text-sm text-primary hover:underline">
          ← Danh sách vai trò
        </Link>
        <p className="text-sm text-muted-foreground">
          Bạn không có quyền tạo vai trò (rbac.role:update).
        </p>
      </AdminShell>
    );
  }

  return (
    <AdminShell
      title="Tạo vai trò"
      description="Vai trò tùy chỉnh thuộc loại STAFF; quyền có thể kế thừa từ vai trò cha"
    >
      <Link href="/rbac/roles" className="mb-6 inline-block text-sm text-primary hover:underline">
        ← Danh sách vai trò
      </Link>

      {error && (
        <div className="mb-6 rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      )}

      {loading && <p className="text-sm text-muted-foreground">Đang tải...</p>}

      {!loading && (
        <form onSubmit={submit} className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Thông tin cơ bản</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="grid gap-4 md:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="slug">Slug</Label>
                  <Input
                    id="slug"
                    value={slug}
                    onChange={(e) => setSlug(e.target.value)}
                    placeholder="MODERATOR"
                    required
                  />
                  <p className="text-xs text-muted-foreground">
                    Hệ thống tự chuẩn hóa thành UPPER_SNAKE_CASE
                  </p>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="name">Tên hiển thị</Label>
                  <Input
                    id="name"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="Moderator"
                    required
                  />
                </div>
                <div className="space-y-2 md:col-span-2">
                  <Label>Vai trò cha (kế thừa quyền)</Label>
                  <Select value={parentRoleSlug} onValueChange={setParentRoleSlug}>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NO_PARENT}>Không có vai trò cha</SelectItem>
                      {roles.map((role) => (
                        <SelectItem key={role.id} value={role.slug}>
                          {role.name} ({role.slug})
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>
            </CardContent>
          </Card>

          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground">
                Quyền trực tiếp
              </h2>
              <p className="mt-1 text-sm text-muted-foreground">
                Đã chọn {selected.size} quyền — có thể chỉnh thêm sau khi tạo
              </p>
            </div>
            <Button type="submit" disabled={submitting}>
              {submitting ? "Đang tạo..." : "Tạo vai trò"}
            </Button>
          </div>

          <PermissionMatrix catalog={catalog} selected={selected} onToggle={toggle} />
        </form>
      )}
    </AdminShell>
  );
}
