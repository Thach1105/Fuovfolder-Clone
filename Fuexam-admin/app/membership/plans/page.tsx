"use client";

import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { AdminShell } from "@/components/admin/AdminShell";
import { ImageUploader } from "@/components/admin/ImageUploader";
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
import { Textarea } from "@/components/ui/textarea";
import { ApiError } from "@/lib/api/client";
import * as adminMembershipApi from "@/lib/api/admin-membership";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";
import type { AdminMembershipPlanResponse, MembershipRoleOptionResponse } from "@/types/api";

const EMPTY_FORM = {
  slug: "",
  name: "",
  description: "",
  pricePoints: "",
  billingInterval: "month",
  roleSlug: "",
  durationDays: "30",
  status: "active",
  imageUrl: "",
};

const STATUS_LABEL: Record<string, string> = {
  active: "Đang bán",
  inactive: "Tạm ẩn",
  archived: "Lưu trữ",
};

const STATUS_COLOR: Record<string, string> = {
  active: "bg-emerald-500/15 text-emerald-500",
  inactive: "bg-amber-500/15 text-amber-500",
  archived: "bg-muted text-muted-foreground",
};

export default function AdminMembershipPlansPage() {
  const { user } = useAuth();
  const canUpdate = can(user, "membership.admin:update");

  const [plans, setPlans] = useState<AdminMembershipPlanResponse[]>([]);
  const [roles, setRoles] = useState<MembershipRoleOptionResponse[]>([]);
  const [form, setForm] = useState(EMPTY_FORM);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [planData, roleData] = await Promise.all([
        adminMembershipApi.listAdminMembershipPlans(),
        adminMembershipApi.listMembershipRoleOptions(),
      ]);
      setPlans(planData);
      setRoles(roleData);
      setForm((prev) =>
        prev.roleSlug || editingId ? prev : { ...prev, roleSlug: roleData[0]?.slug ?? "" },
      );
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được gói membership.");
    } finally {
      setLoading(false);
    }
  }, [editingId]);

  useEffect(() => {
    load();
  }, [load]);

  const resetForm = () => {
    setForm({ ...EMPTY_FORM, roleSlug: roles[0]?.slug ?? "" });
    setEditingId(null);
  };

  const startEdit = (plan: AdminMembershipPlanResponse) => {
    setEditingId(plan.id);
    setForm({
      slug: plan.slug,
      name: plan.name,
      description: plan.description ?? "",
      pricePoints: String(plan.pricePoints),
      billingInterval: plan.billingInterval,
      roleSlug: plan.roleSlug,
      durationDays: String(plan.durationDays),
      status: plan.status,
      imageUrl: plan.imageUrl ?? "",
    });
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!canUpdate) return;
    const pricePoints = parseInt(form.pricePoints, 10);
    const durationDays = parseInt(form.durationDays, 10);
    if (
      !form.name.trim() ||
      Number.isNaN(pricePoints) ||
      pricePoints < 1 ||
      Number.isNaN(durationDays) ||
      durationDays < 1
    ) {
      setError("Vui lòng điền đầy đủ thông tin hợp lệ.");
      return;
    }
    if (!editingId && !form.slug.trim()) {
      setError("Vui lòng nhập slug gói.");
      return;
    }
    if (!form.roleSlug) {
      setError("Vui lòng chọn vai trò membership.");
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const body = {
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        pricePoints,
        billingInterval: form.billingInterval,
        roleSlug: form.roleSlug,
        durationDays,
        status: form.status,
        imageUrl: form.imageUrl || undefined,
      };
      if (editingId) {
        await adminMembershipApi.updateMembershipPlan(editingId, body);
        toast.success("Đã cập nhật gói.");
      } else {
        await adminMembershipApi.createMembershipPlan({ slug: form.slug.trim(), ...body });
        toast.success("Đã tạo gói.");
      }
      resetForm();
      await load();
    } catch (err) {
      const message = err instanceof ApiError ? err.message : "Lưu thất bại.";
      setError(message);
      toast.error(message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AdminShell
      title="Membership — Gói"
      description="Cấu hình giá FUO Point, vai trò gắn kèm và thời hạn từng gói"
    >
      {!canUpdate && (
        <p className="mb-4 text-xs text-amber-500">
          Chế độ chỉ xem (thiếu membership.admin:update)
        </p>
      )}

      {error && (
        <div className="mb-6 rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      )}

      {loading && <p className="text-sm text-muted-foreground">Đang tải...</p>}

      {!loading && (
        <div className="grid gap-6 lg:grid-cols-2">
          {canUpdate && (
            <Card>
              <CardHeader>
                <CardTitle className="text-base">
                  {editingId ? "Sửa gói" : "Thêm gói"}
                </CardTitle>
              </CardHeader>
              <CardContent>
                <form onSubmit={handleSubmit} className="space-y-4">
                  {!editingId ? (
                    <div className="space-y-2">
                      <Label htmlFor="slug">Slug (URL)</Label>
                      <Input
                        id="slug"
                        placeholder="fuo-member"
                        value={form.slug}
                        onChange={(e) => setForm({ ...form, slug: e.target.value })}
                        required
                      />
                    </div>
                  ) : (
                    <p className="text-xs text-muted-foreground">
                      Slug: <span className="font-mono text-foreground">{form.slug}</span> (không đổi)
                    </p>
                  )}
                  <div className="space-y-2">
                    <Label htmlFor="name">Tên gói</Label>
                    <Input
                      id="name"
                      value={form.name}
                      onChange={(e) => setForm({ ...form, name: e.target.value })}
                      required
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="desc">Mô tả</Label>
                    <Textarea
                      id="desc"
                      value={form.description}
                      onChange={(e) => setForm({ ...form, description: e.target.value })}
                    />
                  </div>
                  <ImageUploader
                    label="Ảnh gói"
                    purpose="membership_plan"
                    value={form.imageUrl || null}
                    onChange={(url) => setForm({ ...form, imageUrl: url ?? "" })}
                  />
                  <div className="grid grid-cols-2 gap-4">
                    <div className="space-y-2">
                      <Label htmlFor="price">Giá (FUO Point)</Label>
                      <Input
                        id="price"
                        type="number"
                        min={1}
                        value={form.pricePoints}
                        onChange={(e) => setForm({ ...form, pricePoints: e.target.value })}
                        required
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="duration">Thời hạn (ngày)</Label>
                      <Input
                        id="duration"
                        type="number"
                        min={1}
                        value={form.durationDays}
                        onChange={(e) => setForm({ ...form, durationDays: e.target.value })}
                        required
                      />
                    </div>
                    <div className="space-y-2">
                      <Label>Vai trò membership</Label>
                      <Select
                        value={form.roleSlug}
                        onValueChange={(v) => setForm({ ...form, roleSlug: v })}
                      >
                        <SelectTrigger>
                          <SelectValue placeholder="Chọn vai trò" />
                        </SelectTrigger>
                        <SelectContent>
                          {roles.map((role) => (
                            <SelectItem key={role.id} value={role.slug}>
                              {role.name} ({role.slug})
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      {roles.length === 0 && (
                        <p className="text-xs text-amber-500">
                          Chưa có vai trò MEMBERSHIP. Tạo ở mục Vai trò & quyền trước.
                        </p>
                      )}
                    </div>
                    <div className="space-y-2">
                      <Label>Chu kỳ hiển thị</Label>
                      <Select
                        value={form.billingInterval}
                        onValueChange={(v) => setForm({ ...form, billingInterval: v })}
                      >
                        <SelectTrigger>
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="month">Tháng</SelectItem>
                          <SelectItem value="year">Năm</SelectItem>
                          <SelectItem value="lifetime">Trọn đời</SelectItem>
                        </SelectContent>
                      </Select>
                    </div>
                    <div className="space-y-2 md:col-span-2">
                      <Label>Trạng thái</Label>
                      <Select value={form.status} onValueChange={(v) => setForm({ ...form, status: v })}>
                        <SelectTrigger>
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="active">Đang bán</SelectItem>
                          <SelectItem value="inactive">Tạm ẩn</SelectItem>
                          <SelectItem value="archived">Lưu trữ</SelectItem>
                        </SelectContent>
                      </Select>
                    </div>
                  </div>
                  <div className="flex gap-2">
                    <Button type="submit" disabled={submitting}>
                      {submitting ? "Đang lưu..." : editingId ? "Cập nhật" : "Tạo gói"}
                    </Button>
                    {editingId && (
                      <Button type="button" variant="outline" onClick={resetForm}>
                        Hủy
                      </Button>
                    )}
                  </div>
                </form>
              </CardContent>
            </Card>
          )}

          <div className="space-y-3">
            <h2 className="text-sm font-semibold text-foreground">
              Danh sách gói ({plans.length})
            </h2>
            {plans.length === 0 && <p className="text-sm text-muted-foreground">Chưa có gói nào.</p>}
            {plans.map((plan) => (
              <Card key={plan.id}>
                <CardContent className="p-4">
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div>
                      <p className="font-semibold text-foreground">{plan.name}</p>
                      <p className="font-mono text-xs text-muted-foreground">{plan.slug}</p>
                    </div>
                    <span
                      className={`rounded px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${
                        STATUS_COLOR[plan.status] ?? "bg-muted text-muted-foreground"
                      }`}
                    >
                      {STATUS_LABEL[plan.status] ?? plan.status}
                    </span>
                  </div>
                  <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-muted-foreground">
                    <div>
                      <dt>Giá</dt>
                      <dd className="text-primary">{plan.pricePoints.toLocaleString("vi-VN")} FUO</dd>
                    </div>
                    <div>
                      <dt>Thời hạn</dt>
                      <dd className="text-foreground">{plan.durationDays} ngày</dd>
                    </div>
                    <div>
                      <dt>Vai trò</dt>
                      <dd className="font-mono text-foreground">{plan.roleSlug}</dd>
                    </div>
                    <div>
                      <dt>Chu kỳ</dt>
                      <dd className="text-foreground">{plan.billingInterval}</dd>
                    </div>
                  </dl>
                  {plan.description && (
                    <p className="mt-2 text-sm text-muted-foreground">{plan.description}</p>
                  )}
                  {canUpdate && (
                    <button
                      type="button"
                      className="mt-3 text-sm text-primary hover:underline"
                      onClick={() => startEdit(plan)}
                    >
                      Sửa gói
                    </button>
                  )}
                </CardContent>
              </Card>
            ))}
          </div>
        </div>
      )}
    </AdminShell>
  );
}
