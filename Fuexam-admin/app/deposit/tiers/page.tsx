"use client";

import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { AdminShell } from "@/components/admin/AdminShell";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { ApiError } from "@/lib/api/client";
import * as adminDepositApi from "@/lib/api/admin-deposit";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";
import type { AdminDepositTierResponse } from "@/types/api";

const EMPTY_FORM = {
  label: "",
  amountVnd: "100000",
  points: "100",
  bonusPercent: "0",
  sortOrder: "0",
  active: true,
};

const ACTIVE_LABEL: Record<string, string> = {
  true: "Đang hiện",
  false: "Đã ẩn",
};

const ACTIVE_COLOR: Record<string, string> = {
  true: "bg-emerald-500/15 text-emerald-500",
  false: "bg-muted text-muted-foreground",
};

const formatVnd = (n: number) => `${n.toLocaleString("vi-VN")} ₫`;
const formatPoints = (n: number) => `${n.toLocaleString("vi-VN")} Fuexam`;

export default function AdminDepositTiersPage() {
  const { user } = useAuth();
  const canUpdate = can(user, "deposit.admin:update");

  const [tiers, setTiers] = useState<AdminDepositTierResponse[]>([]);
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
      const data = await adminDepositApi.listAdminDepositTiers();
      const sorted = [...data].sort((a, b) => {
        if (a.sortOrder !== b.sortOrder) return a.sortOrder - b.sortOrder;
        return a.amountVnd - b.amountVnd;
      });
      setTiers(sorted);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được danh sách tier.");
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

  const startEdit = (tier: AdminDepositTierResponse) => {
    setEditingId(tier.id);
    setForm({
      label: tier.label,
      amountVnd: String(tier.amountVnd),
      points: String(tier.points),
      bonusPercent: String(tier.bonusPercent),
      sortOrder: String(tier.sortOrder),
      active: tier.active,
    });
  };

  const parsed = {
    label: form.label.trim(),
    amountVnd: parseInt(form.amountVnd, 10),
    points: parseInt(form.points, 10),
    bonusPercent: parseInt(form.bonusPercent, 10),
    sortOrder: parseInt(form.sortOrder, 10),
  };
  const formValid =
    parsed.label.length > 0 &&
    Number.isFinite(parsed.amountVnd) &&
    parsed.amountVnd >= 1000 &&
    parsed.amountVnd <= 100_000_000 &&
    Number.isFinite(parsed.points) &&
    parsed.points >= 0 &&
    Number.isFinite(parsed.bonusPercent) &&
    parsed.bonusPercent >= 0 &&
    parsed.bonusPercent <= 100 &&
    Number.isFinite(parsed.sortOrder) &&
    parsed.sortOrder >= 0;

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!canUpdate || !formValid) return;

    setSubmitting(true);
    setError(null);
    try {
      if (editingId) {
        await adminDepositApi.updateDepositTier(editingId, {
          label: parsed.label,
          amountVnd: parsed.amountVnd,
          points: parsed.points,
          bonusPercent: parsed.bonusPercent,
          sortOrder: parsed.sortOrder,
          active: form.active,
        });
        toast.success("Đã cập nhật tier.");
      } else {
        await adminDepositApi.createDepositTier({
          label: parsed.label,
          amountVnd: parsed.amountVnd,
          points: parsed.points,
          bonusPercent: parsed.bonusPercent,
          sortOrder: parsed.sortOrder,
          active: form.active,
        });
        toast.success("Đã tạo tier.");
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

  const handleToggle = async (tier: AdminDepositTierResponse) => {
    if (!canUpdate) return;
    setTogglingId(tier.id);
    setError(null);
    try {
      await adminDepositApi.toggleDepositTier(tier.id);
      toast.success(tier.active ? "Đã ẩn tier." : "Đã hiện tier.");
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
      title="Deposit — Mệnh giá nạp"
      description="Cấu hình các gói nạp VND cố định và số Fuexam Point thưởng tương ứng"
    >
      {!canUpdate && (
        <p className="mb-4 text-xs text-amber-500">
          Chế độ chỉ xem (thiếu deposit.admin:update)
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
                  {editingId ? "Sửa tier" : "Thêm tier"}
                </CardTitle>
              </CardHeader>
              <CardContent>
                <form onSubmit={handleSubmit} className="space-y-4">
                  <div className="space-y-2">
                    <Label htmlFor="label">Tên hiển thị</Label>
                    <Input
                      id="label"
                      placeholder="Gói 100k"
                      value={form.label}
                      onChange={(e) => setForm({ ...form, label: e.target.value })}
                      required
                    />
                  </div>
                  <div className="grid grid-cols-2 gap-4">
                    <div className="space-y-2">
                      <Label htmlFor="amount">Mệnh giá (VND)</Label>
                      <Input
                        id="amount"
                        type="number"
                        min={1000}
                        max={100_000_000}
                        step={1000}
                        value={form.amountVnd}
                        onChange={(e) => setForm({ ...form, amountVnd: e.target.value })}
                        required
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="points">Fuexam Point cơ bản</Label>
                      <Input
                        id="points"
                        type="number"
                        min={0}
                        value={form.points}
                        onChange={(e) => setForm({ ...form, points: e.target.value })}
                        required
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="bonus">Bonus (%)</Label>
                      <Input
                        id="bonus"
                        type="number"
                        min={0}
                        max={100}
                        value={form.bonusPercent}
                        onChange={(e) => setForm({ ...form, bonusPercent: e.target.value })}
                        required
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="sort">Thứ tự</Label>
                      <Input
                        id="sort"
                        type="number"
                        min={0}
                        value={form.sortOrder}
                        onChange={(e) => setForm({ ...form, sortOrder: e.target.value })}
                        required
                      />
                    </div>
                  </div>
                  <div className="flex items-center justify-between rounded-md border border-border bg-muted/40 px-3 py-2">
                    <div>
                      <p className="text-sm font-medium text-foreground">Hiện cho user</p>
                      <p className="text-xs text-muted-foreground">
                        Nếu tắt, tier không xuất hiện ở trang nạp nhưng đơn cũ vẫn giữ nguyên.
                      </p>
                    </div>
                    <Switch
                      checked={form.active}
                      onCheckedChange={(v) => setForm({ ...form, active: v })}
                    />
                  </div>
                  {formValid && parsed.points > 0 && (
                    <p className="text-xs text-muted-foreground">
                      User nhận được: <strong>{formatPoints(computeTotal(parsed.points, parsed.bonusPercent))}</strong>
                      {parsed.bonusPercent > 0 && ` (gồm ${parsed.bonusPercent}% bonus)`}
                    </p>
                  )}
                  <div className="flex gap-2">
                    <Button type="submit" disabled={submitting || !formValid}>
                      {submitting ? "Đang lưu..." : editingId ? "Cập nhật" : "Tạo tier"}
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
              Danh sách tier ({tiers.length})
            </h2>
            {tiers.length === 0 && (
              <p className="text-sm text-muted-foreground">Chưa có tier nào.</p>
            )}
            {tiers.map((tier) => (
              <Card key={tier.id}>
                <CardContent className="p-4">
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div>
                      <p className="font-semibold text-foreground">{tier.label}</p>
                      <p className="text-xs text-muted-foreground">
                        Thứ tự: {tier.sortOrder}
                      </p>
                    </div>
                    <span
                      className={`rounded px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${
                        ACTIVE_COLOR[String(tier.active)] ?? "bg-muted text-muted-foreground"
                      }`}
                    >
                      {ACTIVE_LABEL[String(tier.active)] ?? String(tier.active)}
                    </span>
                  </div>
                  <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-muted-foreground">
                    <div>
                      <dt>Mệnh giá</dt>
                      <dd className="text-primary">{formatVnd(tier.amountVnd)}</dd>
                    </div>
                    <div>
                      <dt>Point cơ bản</dt>
                      <dd className="text-foreground">{formatPoints(tier.points)}</dd>
                    </div>
                    {tier.bonusPercent > 0 && (
                      <div>
                        <dt>Bonus</dt>
                        <dd className="text-foreground">+{tier.bonusPercent}%</dd>
                      </div>
                    )}
                    <div>
                      <dt>Tổng nhận</dt>
                      <dd className="text-emerald-500 font-semibold">
                        {formatPoints(tier.totalPoints)}
                      </dd>
                    </div>
                  </dl>
                  {canUpdate && (
                    <div className="mt-3 flex items-center gap-3 text-sm">
                      <button
                        type="button"
                        className="text-primary hover:underline"
                        onClick={() => startEdit(tier)}
                      >
                        Sửa
                      </button>
                      <button
                        type="button"
                        className="text-muted-foreground hover:underline"
                        onClick={() => handleToggle(tier)}
                        disabled={togglingId === tier.id}
                      >
                        {togglingId === tier.id
                          ? "Đang đổi..."
                          : tier.active
                            ? "Ẩn tier"
                            : "Hiện tier"}
                      </button>
                    </div>
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

function computeTotal(points: number, bonusPercent: number): number {
  return points + Math.floor((points * bonusPercent) / 100);
}
