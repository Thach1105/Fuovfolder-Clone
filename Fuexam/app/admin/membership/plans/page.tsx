"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { AdminShell } from "@/components/admin/AdminShell";
import { ErrorBanner } from "@/components/ui/error-banner";
import { LoadingState } from "@/components/ui/loading-state";
import { useAsyncAction } from "@/hooks/use-async-action";
import { useSubmit } from "@/hooks/use-submit";
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
};

const inputClass =
  "w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none transition focus:border-amber-500/60 focus:ring-2 focus:ring-amber-500/20";

import { MEMBERSHIP_PLAN_STATUS_LABELS, toSelectOptions } from "@/lib/constants/status-labels";

export default function AdminMembershipPlansPage() {
  const { user } = useAuth();
  const canUpdate = can(user, "membership.admin:update");

  const [plans, setPlans] = useState<AdminMembershipPlanResponse[]>([]);
  const [roles, setRoles] = useState<MembershipRoleOptionResponse[]>([]);
  const [form, setForm] = useState(EMPTY_FORM);
  const [editingId, setEditingId] = useState<string | null>(null);
  const { loading, error: loadError, run } = useAsyncAction("Không tải được gói membership.");
  const { submitting, error: submitError, submit } = useSubmit("Lưu thất bại.");

  const error = loadError || submitError;

  const load = useCallback(() => run(async () => {
    const [planData, roleData] = await Promise.all([
      adminMembershipApi.listAdminMembershipPlans(),
      adminMembershipApi.listMembershipRoleOptions(),
    ]);
    setPlans(planData);
    setRoles(roleData);
    setForm((prev) =>
      prev.roleSlug || editingId ? prev : { ...prev, roleSlug: roleData[0]?.slug ?? "" },
    );
  }), [run, editingId]);

  useEffect(() => {
    load();
  }, [load]);

  const resetForm = () => {
    setForm({
      ...EMPTY_FORM,
      roleSlug: roles[0]?.slug ?? "",
    });
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
    });
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!canUpdate) {
      return;
    }
    const pricePoints = parseInt(form.pricePoints, 10);
    const durationDays = parseInt(form.durationDays, 10);
    if (!form.name.trim() || Number.isNaN(pricePoints) || pricePoints < 1 || Number.isNaN(durationDays) || durationDays < 1) {
      return;
    }
    if (!editingId && !form.slug.trim()) {
      return;
    }
    if (!form.roleSlug) {
      return;
    }

    const result = await submit(async () => {
      const body = {
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        pricePoints,
        billingInterval: form.billingInterval,
        roleSlug: form.roleSlug,
        durationDays,
        status: form.status,
      };
      if (editingId) {
        await adminMembershipApi.updateMembershipPlan(editingId, body);
      } else {
        await adminMembershipApi.createMembershipPlan({
          slug: form.slug.trim(),
          ...body,
        });
      }
      return true;
    });
    if (result) {
      resetForm();
      await load();
    }
  };

  return (
    <AdminShell
      title="Membership — Gói bán"
      description="Cấu hình giá Fuexam Point, role gắn kèm và thời hạn từng gói"
    >
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-slate-400">
          Role membership khai báo tại{" "}
          <Link href="/admin/rbac/roles" className="text-amber-400 hover:underline">
            Phân quyền
          </Link>
          ; trang này chỉ map gói → role + giá.
        </p>
        {!canUpdate && (
          <span className="text-xs text-amber-400">Chế độ chỉ xem (thiếu membership.admin:update)</span>
        )}
      </div>

      <ErrorBanner message={error} className="mb-6" />

      {loading && <LoadingState />}

      {!loading && (
        <div className="grid gap-8 lg:grid-cols-2">
          {canUpdate && (
            <form onSubmit={handleSubmit} className="space-y-3 rounded-xl border border-slate-800 bg-slate-900/50 p-5">
              <h2 className="text-sm font-semibold text-white">
                {editingId ? "Sửa gói membership" : "Thêm gói membership"}
              </h2>
              {!editingId && (
                <label className="block text-xs text-slate-400">
                  Slug (URL)
                  <input
                    className={`${inputClass} mt-1`}
                    placeholder="fuo-member"
                    value={form.slug}
                    onChange={(e) => setForm({ ...form, slug: e.target.value })}
                    required
                  />
                </label>
              )}
              {editingId && (
                <p className="text-xs text-slate-500">
                  Slug: <span className="font-mono text-slate-300">{form.slug}</span> (không đổi)
                </p>
              )}
              <label className="block text-xs text-slate-400">
                Tên gói
                <input
                  className={`${inputClass} mt-1`}
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  required
                />
              </label>
              <label className="block text-xs text-slate-400">
                Mô tả
                <textarea
                  className={`${inputClass} mt-1`}
                  rows={2}
                  value={form.description}
                  onChange={(e) => setForm({ ...form, description: e.target.value })}
                />
              </label>
              <div className="grid grid-cols-2 gap-3">
                <label className="text-xs text-slate-400">
                  Giá Fuexam Point
                  <input
                    className={`${inputClass} mt-1`}
                    type="number"
                    min={1}
                    value={form.pricePoints}
                    onChange={(e) => setForm({ ...form, pricePoints: e.target.value })}
                    required
                  />
                </label>
                <label className="text-xs text-slate-400">
                  Thời hạn (ngày)
                  <input
                    className={`${inputClass} mt-1`}
                    type="number"
                    min={1}
                    value={form.durationDays}
                    onChange={(e) => setForm({ ...form, durationDays: e.target.value })}
                    required
                  />
                </label>
                <label className="text-xs text-slate-400">
                  Role membership
                  <select
                    className={`${inputClass} mt-1`}
                    value={form.roleSlug}
                    onChange={(e) => setForm({ ...form, roleSlug: e.target.value })}
                    required
                  >
                    {roles.length === 0 && <option value="">Chưa có role MEMBERSHIP</option>}
                    {roles.map((role) => (
                      <option key={role.id} value={role.slug}>
                        {role.name} ({role.slug})
                      </option>
                    ))}
                  </select>
                </label>
                <label className="text-xs text-slate-400">
                  Chu kỳ hiển thị
                  <select
                    className={`${inputClass} mt-1`}
                    value={form.billingInterval}
                    onChange={(e) => setForm({ ...form, billingInterval: e.target.value })}
                  >
                    <option value="month">Tháng</option>
                    <option value="year">Năm</option>
                    <option value="lifetime">Trọn đời</option>
                  </select>
                </label>
                <label className="text-xs text-slate-400 md:col-span-2">
                  Trạng thái
                  <select
                    className={`${inputClass} mt-1`}
                    value={form.status}
                    onChange={(e) => setForm({ ...form, status: e.target.value })}
                  >
                    {toSelectOptions(MEMBERSHIP_PLAN_STATUS_LABELS).map((o) => (
                      <option key={o.value} value={o.value}>{o.label}</option>
                    ))}
                  </select>
                </label>
              </div>
              <div className="flex gap-2 pt-2">
                <button
                  type="submit"
                  disabled={submitting}
                  className="rounded-lg bg-amber-500 px-4 py-2 text-sm font-semibold text-slate-950 disabled:opacity-50"
                >
                  {submitting ? "Đang lưu..." : editingId ? "Cập nhật" : "Tạo gói"}
                </button>
                {editingId && (
                  <button
                    type="button"
                    className="rounded-lg border border-slate-600 px-4 py-2 text-sm text-slate-300"
                    onClick={resetForm}
                  >
                    Hủy
                  </button>
                )}
              </div>
            </form>
          )}

          <div className="space-y-3">
            <h2 className="text-sm font-semibold text-white">Danh sách gói ({plans.length})</h2>
            {plans.length === 0 && (
              <p className="text-sm text-slate-500">Chưa có gói nào.</p>
            )}
            {plans.map((plan) => (
              <article
                key={plan.id}
                className="rounded-xl border border-slate-800 bg-slate-950 p-4"
              >
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <p className="font-semibold text-white">{plan.name}</p>
                    <p className="font-mono text-xs text-slate-500">{plan.slug}</p>
                  </div>
                  <span
                    className={`rounded px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${
                      plan.status === "active"
                        ? "bg-emerald-500/15 text-emerald-300"
                        : plan.status === "inactive"
                          ? "bg-amber-500/15 text-amber-300"
                          : "bg-slate-700 text-slate-400"
                    }`}
                  >
                    {MEMBERSHIP_PLAN_STATUS_LABELS[plan.status] ?? plan.status}
                  </span>
                </div>
                <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-slate-400">
                  <div>
                    <dt>Giá</dt>
                    <dd className="text-amber-300">{plan.pricePoints.toLocaleString("vi-VN")} Fuexam</dd>
                  </div>
                  <div>
                    <dt>Thời hạn</dt>
                    <dd className="text-slate-200">{plan.durationDays} ngày</dd>
                  </div>
                  <div>
                    <dt>Role</dt>
                    <dd className="font-mono text-slate-200">{plan.roleSlug}</dd>
                  </div>
                  <div>
                    <dt>Chu kỳ</dt>
                    <dd className="text-slate-200">{plan.billingInterval}</dd>
                  </div>
                </dl>
                {plan.description && (
                  <p className="mt-2 text-sm text-slate-500">{plan.description}</p>
                )}
                {canUpdate && (
                  <button
                    type="button"
                    className="mt-3 text-sm text-amber-400 hover:underline"
                    onClick={() => startEdit(plan)}
                  >
                    Sửa gói
                  </button>
                )}
              </article>
            ))}
          </div>
        </div>
      )}
    </AdminShell>
  );
}
