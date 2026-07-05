"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { AdminShell } from "@/components/admin/AdminShell";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
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
import * as adminApi from "@/lib/api/admin";
import * as rbacApi from "@/lib/api/rbac";
import { adjustUserPoints } from "@/lib/api/points";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";
import type {
  EffectivePermissions,
  PermissionCatalogResponse,
  RoleSummaryResponse,
  SessionListResponse,
} from "@/types/api";

interface OverrideDraft {
  permissionSlug: string;
  effect: string;
  reason: string;
}

export default function AdminUserPermissionsPage() {
  const params = useParams<{ userId: string }>();
  const userId = params.userId;
  const { user } = useAuth();

  const canEditRoles = can(user, "rbac.assignment:update");
  const canAdjustPoints = can(user, "points.admin:update");
  const canReadOverrides = can(user, "rbac.user_override:read");
  const canEditOverrides = can(user, "rbac.user_override:update");
  const canUpdateUser = can(user, "admin.user:update");
  const canReadUser = can(user, "admin.user:read");

  const [rolesCatalog, setRolesCatalog] = useState<RoleSummaryResponse[]>([]);
  const [assignedRoles, setAssignedRoles] = useState<string[]>([]);
  const [effective, setEffective] = useState<EffectivePermissions | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const [permCatalog, setPermCatalog] = useState<PermissionCatalogResponse | null>(null);
  const [overrides, setOverrides] = useState<OverrideDraft[]>([]);
  const [savingOverrides, setSavingOverrides] = useState(false);
  const [newSlug, setNewSlug] = useState("");
  const [newEffect, setNewEffect] = useState("ALLOW");

  const [pointsOpen, setPointsOpen] = useState(false);
  const [delta, setDelta] = useState("");
  const [reason, setReason] = useState("");
  const [adjusting, setAdjusting] = useState(false);

  const [sessions, setSessions] = useState<SessionListResponse | null>(null);
  const [sessionsLoading, setSessionsLoading] = useState(false);
  const [deviceLimitMode, setDeviceLimitMode] = useState<"global" | "unlimited" | "custom">("global");
  const [customLimit, setCustomLimit] = useState("");
  const [savingLimit, setSavingLimit] = useState(false);
  const [revokingSession, setRevokingSession] = useState<string | null>(null);
  const [revokingAll, setRevokingAll] = useState(false);

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

      if (canReadOverrides) {
        const [cat, ov] = await Promise.all([
          rbacApi.listPermissions(),
          rbacApi.getUserOverrides(userId),
        ]);
        setPermCatalog(cat);
        setOverrides(ov.map((o) => ({ permissionSlug: o.permissionSlug, effect: o.effect, reason: o.reason ?? "" })));
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được quyền người dùng.");
    } finally {
      setLoading(false);
    }
  }, [userId, canReadOverrides]);

  useEffect(() => {
    load();
  }, [load]);

  const loadSessions = useCallback(async () => {
    if (!canReadUser) return;
    setSessionsLoading(true);
    try {
      const data = await adminApi.getUserSessions(userId);
      setSessions(data);
      if (data.deviceLimitSource === "UNLIMITED") {
        setDeviceLimitMode("unlimited");
      } else if (data.deviceLimitSource === "CUSTOM") {
        setDeviceLimitMode("custom");
        setCustomLimit(String(data.maxDevices));
      } else {
        setDeviceLimitMode("global");
      }
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Không tải được phiên đăng nhập.");
    } finally {
      setSessionsLoading(false);
    }
  }, [userId, canReadUser]);

  useEffect(() => {
    loadSessions();
  }, [loadSessions]);

  const allPermissionSlugs = permCatalog
    ? Object.values(permCatalog.modules).flat().map((p) => p.slug)
    : [];

  function addOverride() {
    if (!newSlug) return;
    if (overrides.some((o) => o.permissionSlug === newSlug)) {
      toast.error("Quyền này đã có trong danh sách override.");
      return;
    }
    setOverrides((prev) => [...prev, { permissionSlug: newSlug, effect: newEffect, reason: "" }]);
    setNewSlug("");
    setNewEffect("ALLOW");
  }

  function removeOverride(slug: string) {
    setOverrides((prev) => prev.filter((o) => o.permissionSlug !== slug));
  }

  function setOverrideEffect(slug: string, effect: string) {
    setOverrides((prev) =>
      prev.map((o) => (o.permissionSlug === slug ? { ...o, effect } : o)),
    );
  }

  function setOverrideReason(slug: string, reason: string) {
    setOverrides((prev) =>
      prev.map((o) => (o.permissionSlug === slug ? { ...o, reason } : o)),
    );
  }

  async function saveOverrides() {
    setSavingOverrides(true);
    try {
      const updated = await rbacApi.updateUserOverrides(
        userId,
        overrides.map((o) => ({
          permissionSlug: o.permissionSlug,
          effect: o.effect,
          reason: o.reason.trim() || undefined,
        })),
      );
      setOverrides(
        updated.map((o) => ({
          permissionSlug: o.permissionSlug,
          effect: o.effect,
          reason: o.reason ?? "",
        })),
      );
      setEffective(await rbacApi.getUserEffectivePermissions(userId));
      toast.success("Đã lưu override quyền.");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Không lưu được override.");
    } finally {
      setSavingOverrides(false);
    }
  }

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
      toast.success("Đã cập nhật vai trò.");
    } catch (err) {
      const message = err instanceof ApiError ? err.message : "Không lưu được vai trò.";
      setError(message);
      toast.error(message);
    } finally {
      setSaving(false);
    }
  };

  const submitAdjust = async () => {
    const value = Number(delta);
    if (!Number.isFinite(value) || value === 0) {
      toast.error("Nhập số điểm khác 0 (dương để cộng, âm để trừ).");
      return;
    }
    setAdjusting(true);
    try {
      await adjustUserPoints(userId, value, reason || undefined);
      toast.success(`Đã điều chỉnh ${value > 0 ? "+" : ""}${value.toLocaleString("vi-VN")} điểm.`);
      setPointsOpen(false);
      setDelta("");
      setReason("");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Không điều chỉnh được điểm.");
    } finally {
      setAdjusting(false);
    }
  };

  const saveDeviceLimit = async () => {
    setSavingLimit(true);
    try {
      let maxDevices: number | null = null;
      if (deviceLimitMode === "unlimited") maxDevices = 0;
      else if (deviceLimitMode === "custom") maxDevices = Number(customLimit);
      await adminApi.setUserDeviceLimit(userId, maxDevices);
      toast.success("Đã cập nhật giới hạn thiết bị.");
      loadSessions();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Không lưu được giới hạn.");
    } finally {
      setSavingLimit(false);
    }
  };

  const handleRevokeSession = async (familyId: string) => {
    setRevokingSession(familyId);
    try {
      await adminApi.revokeUserSession(userId, familyId);
      toast.success("Đã đăng xuất phiên.");
      loadSessions();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Không đăng xuất được.");
    } finally {
      setRevokingSession(null);
    }
  };

  const handleRevokeAll = async () => {
    setRevokingAll(true);
    try {
      await adminApi.revokeAllUserSessions(userId);
      toast.success("Đã đăng xuất tất cả phiên.");
      loadSessions();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Không đăng xuất được.");
    } finally {
      setRevokingAll(false);
    }
  };

  // System roles are managed by the backend; only USER is admin-assignable among them.
  const assignableRoles = rolesCatalog.filter(
    (r) => r.roleType !== "SYSTEM" || r.slug === "USER",
  );

  return (
    <AdminShell
      title="Quyền người dùng"
      description={`User ID: ${userId}`}
      actions={
        canAdjustPoints ? (
          <Button variant="outline" size="sm" onClick={() => setPointsOpen(true)}>
            Điều chỉnh điểm Fuexam
          </Button>
        ) : undefined
      }
    >
      <Link href="/users" className="mb-6 inline-block text-sm text-primary hover:underline">
        ← Danh sách người dùng
      </Link>

      {error && (
        <div className="mb-6 rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      )}

      {loading && <p className="text-sm text-muted-foreground">Đang tải...</p>}

      {!loading && (
        <div className="grid gap-6 lg:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Gán vai trò</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-3">
                {assignableRoles.map((role) => (
                  <label
                    key={role.id}
                    className="flex cursor-pointer items-center gap-3 text-sm text-foreground"
                  >
                    <Checkbox
                      checked={assignedRoles.includes(role.slug)}
                      onCheckedChange={() => toggleRole(role.slug)}
                      disabled={!canEditRoles}
                    />
                    <span>
                      {role.name}{" "}
                      <span className="text-xs text-muted-foreground">({role.slug})</span>
                    </span>
                  </label>
                ))}
                {assignableRoles.length === 0 && (
                  <p className="text-sm text-muted-foreground">Không có vai trò nào.</p>
                )}
              </div>
              {canEditRoles && (
                <Button onClick={saveRoles} disabled={saving} className="mt-4">
                  {saving ? "Đang lưu..." : "Lưu vai trò"}
                </Button>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">Quyền hiệu lực</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-xs text-muted-foreground">
                {effective?.permissions.length ?? 0} quyền · phiên bản v{effective?.permVersion ?? 0}
                {effective?.superAdmin && " · SUPER_ADMIN (toàn quyền)"}
              </p>
              <ul className="mt-4 max-h-96 space-y-1 overflow-auto font-mono text-xs text-muted-foreground">
                {effective?.permissions.map((p) => (
                  <li key={p}>{p}</li>
                ))}
              </ul>
            </CardContent>
          </Card>
        </div>
      )}

      {!loading && canReadOverrides && (
        <Card className="mt-6">
          <CardHeader>
            <CardTitle className="text-base">Override quyền theo người dùng</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="mb-4 text-xs text-muted-foreground">
              ALLOW = cấp thêm quyền lẻ; DENY = chặn quyền dù vai trò có. Override đè lên vai trò.
            </p>

            {overrides.length === 0 && (
              <p className="text-sm text-muted-foreground">Chưa có override nào.</p>
            )}

            <div className="space-y-2">
              {overrides.map((o) => (
                <div
                  key={o.permissionSlug}
                  className="flex flex-wrap items-center gap-2 rounded-lg border border-border px-3 py-2"
                >
                  <span className="flex-1 font-mono text-xs text-primary">{o.permissionSlug}</span>
                  <Select
                    value={o.effect}
                    onValueChange={(v) => setOverrideEffect(o.permissionSlug, v)}
                    disabled={!canEditOverrides}
                  >
                    <SelectTrigger className="w-[110px]">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="ALLOW">ALLOW</SelectItem>
                      <SelectItem value="DENY">DENY</SelectItem>
                    </SelectContent>
                  </Select>
                  <Input
                    className="w-[180px]"
                    placeholder="Lý do"
                    value={o.reason}
                    onChange={(e) => setOverrideReason(o.permissionSlug, e.target.value)}
                    disabled={!canEditOverrides}
                  />
                  {canEditOverrides && (
                    <button
                      type="button"
                      className="text-sm text-destructive hover:underline"
                      onClick={() => removeOverride(o.permissionSlug)}
                    >
                      Xóa
                    </button>
                  )}
                </div>
              ))}
            </div>

            {canEditOverrides && (
              <>
                <div className="mt-4 flex flex-wrap items-end gap-2 border-t border-border pt-4">
                  <div className="flex-1 space-y-1" style={{ minWidth: 220 }}>
                    <Label>Thêm quyền</Label>
                    <Select value={newSlug} onValueChange={setNewSlug}>
                      <SelectTrigger>
                        <SelectValue placeholder="Chọn permission" />
                      </SelectTrigger>
                      <SelectContent>
                        {allPermissionSlugs
                          .filter((s) => !overrides.some((o) => o.permissionSlug === s))
                          .map((s) => (
                            <SelectItem key={s} value={s}>
                              {s}
                            </SelectItem>
                          ))}
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="space-y-1">
                    <Label>Hiệu lực</Label>
                    <Select value={newEffect} onValueChange={setNewEffect}>
                      <SelectTrigger className="w-[110px]">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="ALLOW">ALLOW</SelectItem>
                        <SelectItem value="DENY">DENY</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                  <Button type="button" variant="outline" onClick={addOverride}>
                    + Thêm
                  </Button>
                </div>
                <Button onClick={saveOverrides} disabled={savingOverrides} className="mt-4">
                  {savingOverrides ? "Đang lưu..." : "Lưu override"}
                </Button>
              </>
            )}
          </CardContent>
        </Card>
      )}

      {!loading && canReadUser && (
        <Card className="mt-6">
          <CardHeader>
            <CardTitle className="text-base">Giới hạn thiết bị</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex flex-wrap items-end gap-3">
              <div className="space-y-1">
                <Label>Chế độ</Label>
                <Select value={deviceLimitMode} onValueChange={(v) => setDeviceLimitMode(v as "global" | "unlimited" | "custom")} disabled={!canUpdateUser}>
                  <SelectTrigger className="w-[200px]">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="global">Mặc định hệ thống ({sessions?.deviceLimitSource === "GLOBAL" ? sessions.maxDevices : "2"})</SelectItem>
                    <SelectItem value="unlimited">Không giới hạn</SelectItem>
                    <SelectItem value="custom">Tùy chỉnh</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              {deviceLimitMode === "custom" && (
                <div className="space-y-1">
                  <Label>Số thiết bị tối đa</Label>
                  <Input type="number" min={1} max={100} className="w-[100px]" value={customLimit} onChange={(e) => setCustomLimit(e.target.value)} disabled={!canUpdateUser} />
                </div>
              )}
              {canUpdateUser && (
                <Button onClick={saveDeviceLimit} disabled={savingLimit}>
                  {savingLimit ? "Đang lưu..." : "Lưu"}
                </Button>
              )}
            </div>
          </CardContent>
        </Card>
      )}

      {!loading && canReadUser && (
        <Card className="mt-6">
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle className="text-base">Phiên đăng nhập ({sessions?.sessions.length ?? 0})</CardTitle>
            {canUpdateUser && sessions && sessions.sessions.length > 0 && (
              <Button variant="destructive" size="sm" onClick={handleRevokeAll} disabled={revokingAll}>
                {revokingAll ? "Đang xử lý..." : "Đăng xuất tất cả"}
              </Button>
            )}
          </CardHeader>
          <CardContent>
            {sessionsLoading && <p className="text-sm text-muted-foreground">Đang tải...</p>}
            {!sessionsLoading && sessions && sessions.sessions.length === 0 && (
              <p className="text-sm text-muted-foreground">Không có phiên đăng nhập nào.</p>
            )}
            {!sessionsLoading && sessions && sessions.sessions.length > 0 && (
              <div className="space-y-2">
                {sessions.sessions.map((s) => (
                  <div key={s.id} className="flex items-center justify-between rounded-lg border border-border px-3 py-2">
                    <div>
                      <p className="text-sm font-medium">{s.deviceLabel}</p>
                      <p className="text-xs text-muted-foreground">
                        IP: {s.ipAddress ?? "—"} · {new Date(s.issuedAt).toLocaleString("vi-VN")}
                        {s.lastUsedAt && ` · Hoạt động: ${new Date(s.lastUsedAt).toLocaleString("vi-VN")}`}
                      </p>
                    </div>
                    {canUpdateUser && (
                      <Button variant="outline" size="sm" onClick={() => handleRevokeSession(s.id)} disabled={revokingSession === s.id}>
                        {revokingSession === s.id ? "..." : "Đăng xuất"}
                      </Button>
                    )}
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      )}

      <Dialog open={pointsOpen} onOpenChange={setPointsOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Điều chỉnh điểm Fuexam</DialogTitle>
            <DialogDescription>
              Nhập số dương để cộng, số âm để trừ. Hành động này được ghi vào sổ điểm.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="delta">Số điểm (delta)</Label>
              <Input
                id="delta"
                type="number"
                placeholder="VD: 500000 hoặc -100000"
                value={delta}
                onChange={(e) => setDelta(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="reason">Lý do</Label>
              <Input
                id="reason"
                placeholder="VD: thưởng sự kiện"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPointsOpen(false)} disabled={adjusting}>
              Hủy
            </Button>
            <Button onClick={submitAdjust} disabled={adjusting}>
              {adjusting ? "Đang xử lý..." : "Xác nhận"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </AdminShell>
  );
}
