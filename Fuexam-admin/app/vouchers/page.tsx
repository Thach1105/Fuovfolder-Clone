"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { toast } from "sonner";
import { AdminShell } from "@/components/admin/AdminShell";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { ApiError } from "@/lib/api/client";
import * as voucherApi from "@/lib/api/admin-vouchers";
import type { VoucherResponse } from "@/lib/api/admin-vouchers";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";

const APPLICABLE_OPTIONS = [
  { value: "source", label: "Source (tài liệu)" },
  { value: "check_score", label: "Check điểm" },
  { value: "membership", label: "Membership (gói)" },
  { value: "coursera", label: "Coursera (khóa học)" },
];

function toLocalDatetime(iso: string): string {
  if (!iso) return "";
  // Convert ISO string to datetime-local input value
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` +
    `T${pad(d.getHours())}:${pad(d.getMinutes())}`
  );
}

function toIso(local: string): string {
  if (!local) return "";
  return new Date(local).toISOString();
}

const EMPTY_FORM = {
  code: "",
  description: "",
  discountType: "percentage" as "percentage" | "fixed",
  discountValue: "10",
  maxDiscountPoints: "",
  minOrderPoints: "0",
  maxUsage: "100",
  maxUsagePerUser: "1",
  applicableTypes: ["source"] as string[],
  requiredMembershipSlugs: "",
  startsAt: "",
  endsAt: "",
  active: true,
};

function voucherStatus(v: VoucherResponse): { label: string; cls: string } {
  const now = Date.now();
  const ends = new Date(v.endsAt).getTime();
  if (ends < now) return { label: "Hết hạn", cls: "bg-muted text-muted-foreground" };
  if (!v.active) return { label: "Tắt", cls: "bg-amber-500/15 text-amber-500" };
  return { label: "Hoạt động", cls: "bg-emerald-500/15 text-emerald-500" };
}

export default function AdminVouchersPage() {
  const { user } = useAuth();
  const canCreate = can(user, "voucher.admin:create");
  const canUpdate = can(user, "voucher.admin:update");
  const canWrite = canCreate || canUpdate;

  const [vouchers, setVouchers] = useState<VoucherResponse[]>([]);
  const [form, setForm] = useState(EMPTY_FORM);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [togglingId, setTogglingId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const page = await voucherApi.listVouchers();
      setVouchers(page.content);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được danh sách voucher.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const resetForm = () => {
    setForm(EMPTY_FORM);
    setEditingId(null);
  };

  const startEdit = (v: VoucherResponse) => {
    setEditingId(v.id);
    setForm({
      code: v.code,
      description: v.description ?? "",
      discountType: v.discountType,
      discountValue: String(v.discountValue),
      maxDiscountPoints: v.maxDiscountPoints != null ? String(v.maxDiscountPoints) : "",
      minOrderPoints: String(v.minOrderPoints),
      maxUsage: String(v.maxUsage),
      maxUsagePerUser: String(v.maxUsagePerUser),
      applicableTypes: v.applicableTypes
        ? v.applicableTypes.split(",").map((s) => s.trim()).filter(Boolean)
        : [],
      requiredMembershipSlugs: v.requiredMembershipSlugs ?? "",
      startsAt: toLocalDatetime(v.startsAt),
      endsAt: toLocalDatetime(v.endsAt),
      active: v.active,
    });
  };

  const toggleApplicable = (val: string) => {
    setForm((prev) => {
      const has = prev.applicableTypes.includes(val);
      return {
        ...prev,
        applicableTypes: has
          ? prev.applicableTypes.filter((t) => t !== val)
          : [...prev.applicableTypes, val],
      };
    });
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!canWrite) return;

    const discountValue = parseFloat(form.discountValue);
    const maxDiscountPoints =
      form.discountType === "percentage" && form.maxDiscountPoints
        ? parseInt(form.maxDiscountPoints, 10)
        : null;
    const minOrderPoints = parseInt(form.minOrderPoints, 10);
    const maxUsage = parseInt(form.maxUsage, 10);
    const maxUsagePerUser = parseInt(form.maxUsagePerUser, 10);

    if (!form.code.trim()) {
      setError("Vui lòng nhập mã voucher.");
      return;
    }
    if (Number.isNaN(discountValue) || discountValue <= 0) {
      setError("Giá trị giảm giá phải > 0.");
      return;
    }
    if (form.applicableTypes.length === 0) {
      setError("Chọn ít nhất một loại áp dụng.");
      return;
    }
    if (!form.startsAt || !form.endsAt) {
      setError("Vui lòng chọn thời gian bắt đầu và kết thúc.");
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const body: voucherApi.CreateVoucherBody = {
        code: form.code.trim().toUpperCase(),
        description: form.description.trim() || undefined,
        discountType: form.discountType,
        discountValue,
        maxDiscountPoints,
        minOrderPoints: Number.isNaN(minOrderPoints) ? 0 : minOrderPoints,
        maxUsage: Number.isNaN(maxUsage) ? 1 : maxUsage,
        maxUsagePerUser: Number.isNaN(maxUsagePerUser) ? 1 : maxUsagePerUser,
        applicableTypes: form.applicableTypes.join(","),
        requiredMembershipSlugs: form.requiredMembershipSlugs.trim() || null,
        startsAt: toIso(form.startsAt),
        endsAt: toIso(form.endsAt),
      };

      if (editingId) {
        await voucherApi.updateVoucher(editingId, body);
        toast.success("Đã cập nhật voucher.");
      } else {
        await voucherApi.createVoucher(body);
        toast.success("Đã tạo voucher.");
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

  const handleToggle = async (v: VoucherResponse) => {
    if (!canUpdate) return;
    setTogglingId(v.id);
    setError(null);
    try {
      await voucherApi.toggleVoucher(v.id);
      toast.success(v.active ? "Đã tắt voucher." : "Đã bật voucher.");
      await load();
    } catch (err) {
      const message = err instanceof ApiError ? err.message : "Không đổi được trạng thái.";
      setError(message);
      toast.error(message);
    } finally {
      setTogglingId(null);
    }
  };

  return (
    <AdminShell
      title="Voucher"
      description="Quản lý mã giảm giá Fuexam Point cho các giao dịch"
    >
      {!canWrite && (
        <p className="mb-4 text-xs text-amber-500">
          Chế độ chỉ xem (thiếu voucher.admin:create hoặc voucher.admin:update)
        </p>
      )}

      {error && (
        <div className="mb-6 rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      )}

      {loading && <p className="text-sm text-muted-foreground">Đang tải...</p>}

      {!loading && (
        <div className="grid gap-6 lg:grid-cols-[1fr_1.5fr]">
          {canWrite && (
            <Card>
              <CardHeader>
                <CardTitle className="text-base">
                  {editingId ? "Sửa voucher" : "Thêm voucher"}
                </CardTitle>
              </CardHeader>
              <CardContent>
                <form onSubmit={handleSubmit} className="space-y-4">
                  {/* Code */}
                  <div className="space-y-2">
                    <Label htmlFor="code">Mã voucher</Label>
                    <Input
                      id="code"
                      placeholder="SUMMER2025"
                      value={form.code}
                      onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })}
                      disabled={!!editingId}
                      required
                    />
                    {editingId && (
                      <p className="text-xs text-muted-foreground">Mã không thể thay đổi sau khi tạo.</p>
                    )}
                  </div>

                  {/* Description */}
                  <div className="space-y-2">
                    <Label htmlFor="desc">Mô tả</Label>
                    <Textarea
                      id="desc"
                      value={form.description}
                      onChange={(e) => setForm({ ...form, description: e.target.value })}
                      rows={2}
                    />
                  </div>

                  {/* Discount Type */}
                  <div className="space-y-2">
                    <Label>Loại giảm giá</Label>
                    <div className="flex gap-2">
                      <Button
                        type="button"
                        variant={form.discountType === "percentage" ? "default" : "outline"}
                        size="sm"
                        onClick={() => setForm({ ...form, discountType: "percentage" })}
                      >
                        % Phần trăm
                      </Button>
                      <Button
                        type="button"
                        variant={form.discountType === "fixed" ? "default" : "outline"}
                        size="sm"
                        onClick={() => setForm({ ...form, discountType: "fixed" })}
                      >
                        Cố định (Point)
                      </Button>
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-4">
                    {/* Discount Value */}
                    <div className="space-y-2">
                      <Label htmlFor="dvalue">
                        {form.discountType === "percentage" ? "Giảm (%)" : "Giảm (Point)"}
                      </Label>
                      <Input
                        id="dvalue"
                        type="number"
                        min={1}
                        step={1}
                        max={form.discountType === "percentage" ? 100 : undefined}
                        value={form.discountValue}
                        onChange={(e) => setForm({ ...form, discountValue: e.target.value })}
                        required
                      />
                    </div>

                    {/* Max Discount (only for %) */}
                    {form.discountType === "percentage" && (
                      <div className="space-y-2">
                        <Label htmlFor="maxdisc">Giảm tối đa (Point)</Label>
                        <Input
                          id="maxdisc"
                          type="number"
                          min={0}
                          placeholder="Không giới hạn"
                          value={form.maxDiscountPoints}
                          onChange={(e) => setForm({ ...form, maxDiscountPoints: e.target.value })}
                        />
                      </div>
                    )}

                    {/* Min Order */}
                    <div className="space-y-2">
                      <Label htmlFor="minorder">Đơn tối thiểu (Point)</Label>
                      <Input
                        id="minorder"
                        type="number"
                        min={0}
                        value={form.minOrderPoints}
                        onChange={(e) => setForm({ ...form, minOrderPoints: e.target.value })}
                      />
                    </div>

                    {/* Max Usage */}
                    <div className="space-y-2">
                      <Label htmlFor="maxusage">Tổng lượt dùng</Label>
                      <Input
                        id="maxusage"
                        type="number"
                        min={1}
                        value={form.maxUsage}
                        onChange={(e) => setForm({ ...form, maxUsage: e.target.value })}
                        required
                      />
                    </div>

                    {/* Max Usage Per User */}
                    <div className="space-y-2">
                      <Label htmlFor="maxperuser">Lượt/user</Label>
                      <Input
                        id="maxperuser"
                        type="number"
                        min={1}
                        value={form.maxUsagePerUser}
                        onChange={(e) => setForm({ ...form, maxUsagePerUser: e.target.value })}
                        required
                      />
                    </div>
                  </div>

                  {/* Applicable Types */}
                  <div className="space-y-2">
                    <Label>Loại áp dụng</Label>
                    <div className="flex flex-col gap-2">
                      {APPLICABLE_OPTIONS.map((opt) => (
                        <div key={opt.value} className="flex items-center gap-2">
                          <Checkbox
                            id={`app-${opt.value}`}
                            checked={form.applicableTypes.includes(opt.value)}
                            onCheckedChange={() => toggleApplicable(opt.value)}
                          />
                          <label
                            htmlFor={`app-${opt.value}`}
                            className="text-sm text-foreground cursor-pointer"
                          >
                            {opt.label}
                          </label>
                        </div>
                      ))}
                    </div>
                  </div>

                  {/* Required Membership Slugs */}
                  <div className="space-y-2">
                    <Label htmlFor="membslugs">Yêu cầu membership (slug, cách nhau bởi dấu phẩy)</Label>
                    <Input
                      id="membslugs"
                      placeholder="fuo-member,fuo-premium"
                      value={form.requiredMembershipSlugs}
                      onChange={(e) => setForm({ ...form, requiredMembershipSlugs: e.target.value })}
                    />
                  </div>

                  {/* Date Range */}
                  <div className="grid grid-cols-2 gap-4">
                    <div className="space-y-2">
                      <Label htmlFor="startsat">Bắt đầu</Label>
                      <Input
                        id="startsat"
                        type="datetime-local"
                        value={form.startsAt}
                        onChange={(e) => setForm({ ...form, startsAt: e.target.value })}
                        required
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="endsat">Kết thúc</Label>
                      <Input
                        id="endsat"
                        type="datetime-local"
                        value={form.endsAt}
                        onChange={(e) => setForm({ ...form, endsAt: e.target.value })}
                        required
                      />
                    </div>
                  </div>

                  {/* Active toggle (edit only) */}
                  {editingId && (
                    <div className="flex items-center justify-between rounded-md border border-border bg-muted/40 px-3 py-2">
                      <div>
                        <p className="text-sm font-medium text-foreground">Kích hoạt</p>
                        <p className="text-xs text-muted-foreground">
                          Bật để voucher có hiệu lực trong khoảng thời gian đã chọn.
                        </p>
                      </div>
                      <Switch
                        checked={form.active}
                        onCheckedChange={(v) => setForm({ ...form, active: v })}
                      />
                    </div>
                  )}

                  <div className="flex gap-2">
                    <Button type="submit" disabled={submitting}>
                      {submitting ? "Đang lưu..." : editingId ? "Cập nhật" : "Tạo voucher"}
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

          {/* Table / list */}
          <div className="space-y-3">
            <h2 className="text-sm font-semibold text-foreground">
              Danh sách voucher ({vouchers.length})
            </h2>
            {vouchers.length === 0 && (
              <p className="text-sm text-muted-foreground">Chưa có voucher nào.</p>
            )}
            <div className="overflow-x-auto rounded-lg border border-border">
              {vouchers.length > 0 && (
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-border bg-muted/40">
                      <th className="px-3 py-2 text-left font-semibold text-muted-foreground">Mã</th>
                      <th className="px-3 py-2 text-left font-semibold text-muted-foreground">Loại</th>
                      <th className="px-3 py-2 text-left font-semibold text-muted-foreground">Giá trị</th>
                      <th className="px-3 py-2 text-left font-semibold text-muted-foreground">Lượt dùng</th>
                      <th className="px-3 py-2 text-left font-semibold text-muted-foreground">Trạng thái</th>
                      <th className="px-3 py-2 text-left font-semibold text-muted-foreground">Áp dụng</th>
                      <th className="px-3 py-2 text-left font-semibold text-muted-foreground">Thời hạn</th>
                      <th className="px-3 py-2 text-left font-semibold text-muted-foreground">
                        Hành động
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {vouchers.map((v) => {
                      const status = voucherStatus(v);
                      return (
                        <tr
                          key={v.id}
                          className="border-b border-border last:border-0 hover:bg-muted/20 transition-colors cursor-pointer"
                          onClick={() => canWrite && startEdit(v)}
                        >
                          <td className="px-3 py-2 font-mono font-semibold text-foreground">
                            {v.code}
                          </td>
                          <td className="px-3 py-2">
                            <span
                              className={`rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${
                                v.discountType === "percentage"
                                  ? "bg-blue-500/15 text-blue-500"
                                  : "bg-purple-500/15 text-purple-500"
                              }`}
                            >
                              {v.discountType === "percentage" ? "%" : "Cố định"}
                            </span>
                          </td>
                          <td className="px-3 py-2 text-foreground">
                            {v.discountType === "percentage"
                              ? `${v.discountValue}%${v.maxDiscountPoints ? ` (tối đa ${v.maxDiscountPoints})` : ""}`
                              : `${v.discountValue.toLocaleString("vi-VN")} pt`}
                          </td>
                          <td className="px-3 py-2 text-foreground">
                            {v.usedCount}/{v.maxUsage}
                          </td>
                          <td className="px-3 py-2">
                            <span
                              className={`rounded px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${status.cls}`}
                            >
                              {status.label}
                            </span>
                          </td>
                          <td className="px-3 py-2 text-muted-foreground text-xs">
                            {v.applicableTypes}
                          </td>
                          <td className="px-3 py-2 text-muted-foreground text-xs whitespace-nowrap">
                            {new Date(v.startsAt).toLocaleDateString("vi-VN")} –{" "}
                            {new Date(v.endsAt).toLocaleDateString("vi-VN")}
                          </td>
                          <td
                            className="px-3 py-2"
                            onClick={(e) => e.stopPropagation()}
                          >
                            <div className="flex items-center gap-2 flex-wrap">
                              {canWrite && (
                                <button
                                  type="button"
                                  className="text-primary hover:underline text-xs"
                                  onClick={() => startEdit(v)}
                                >
                                  Sửa
                                </button>
                              )}
                              {canUpdate && (
                                <button
                                  type="button"
                                  className="text-muted-foreground hover:underline text-xs"
                                  onClick={() => handleToggle(v)}
                                  disabled={togglingId === v.id}
                                >
                                  {togglingId === v.id
                                    ? "..."
                                    : v.active
                                      ? "Tắt"
                                      : "Bật"}
                                </button>
                              )}
                              <Link
                                href={`/vouchers/${v.id}/assignments`}
                                className="text-blue-500 hover:underline text-xs"
                              >
                                Assigns
                              </Link>
                              <Link
                                href={`/vouchers/${v.id}/redemptions`}
                                className="text-violet-500 hover:underline text-xs"
                              >
                                History
                              </Link>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        </div>
      )}
    </AdminShell>
  );
}
