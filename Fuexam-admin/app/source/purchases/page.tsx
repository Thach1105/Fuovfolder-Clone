"use client";

import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { AdminShell } from "@/components/admin/AdminShell";
import { StatCard } from "@/components/admin/StatCard";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ApiError } from "@/lib/api/client";
import { useAuth } from "@/lib/auth/AuthProvider";
import { canRefundSource } from "@/lib/auth/roles";
import {
  type AdminSourcePurchase,
  type SourceOverview,
  SOURCE_PURCHASE_STATUS_LABELS,
  formatPoints,
  getSourceOverview,
  listAdminSourcePurchases,
  refundSourcePurchase,
} from "@/lib/api/source";
import { formatDateTime } from "@/lib/format-datetime";

const ALL = "all";
const STATUS_FILTER_OPTIONS = [
  { value: ALL, label: "Tất cả" },
  { value: "active", label: "Còn hạn" },
  { value: "expired", label: "Hết hạn" },
  { value: "refunded", label: "Đã hoàn" },
  { value: "cancelled", label: "Đã hủy" },
];

export default function AdminSourcePurchasesPage() {
  const { user } = useAuth();
  const canRefund = canRefundSource(user);

  const [overview, setOverview] = useState<SourceOverview | null>(null);
  const [items, setItems] = useState<AdminSourcePurchase[]>([]);
  const [statusFilter, setStatusFilter] = useState(ALL);
  const [codeFilter, setCodeFilter] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [refundTarget, setRefundTarget] = useState<AdminSourcePurchase | null>(null);
  const [refundReason, setRefundReason] = useState("");
  const [refunding, setRefunding] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [ov, page] = await Promise.all([
        getSourceOverview(),
        listAdminSourcePurchases({
          status: statusFilter === ALL ? undefined : statusFilter,
          code: codeFilter.trim() || undefined,
          size: 50,
        }),
      ]);
      setOverview(ov);
      setItems(page.items);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được dữ liệu.");
    } finally {
      setLoading(false);
    }
  }, [statusFilter, codeFilter]);

  useEffect(() => {
    load();
  }, [load]);

  async function submitRefund() {
    if (!refundTarget) return;
    setRefunding(true);
    try {
      await refundSourcePurchase(refundTarget.id, refundReason.trim() || undefined);
      toast.success("Đã hoàn tiền.");
      setRefundTarget(null);
      setRefundReason("");
      await load();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Hoàn tiền thất bại.");
    } finally {
      setRefunding(false);
    }
  }

  return (
    <AdminShell title="Source — Đơn mua" description="Theo dõi giao dịch và hoàn tiền FUO Point">
      {overview && (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
          <StatCard label="Tổng đơn" value={overview.totalPurchases} />
          <StatCard label="Còn hiệu lực" value={overview.activePurchases} accent="success" />
          <StatCard label="Đã hoàn" value={overview.refundedPurchases} accent="warning" />
          <StatCard label="Tài liệu đang bán" value={overview.activeCatalogItems} />
          <StatCard label="Điểm đã thu" value={overview.paidPoints.toLocaleString("vi-VN")} />
          <StatCard label="Điểm đã hoàn" value={overview.refundedPoints.toLocaleString("vi-VN")} />
        </div>
      )}

      <div className="mt-6 flex flex-wrap items-end gap-3">
        <div className="space-y-1">
          <Label>Trạng thái</Label>
          <Select value={statusFilter} onValueChange={setStatusFilter}>
            <SelectTrigger className="w-[160px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {STATUS_FILTER_OPTIONS.map((o) => (
                <SelectItem key={o.value} value={o.value}>
                  {o.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-1">
          <Label htmlFor="code">Mã môn</Label>
          <Input
            id="code"
            className="w-[160px]"
            placeholder="MLN111"
            value={codeFilter}
            onChange={(e) => setCodeFilter(e.target.value)}
          />
        </div>
        <Button onClick={() => load()}>Lọc</Button>
        {!canRefund && (
          <span className="text-xs text-muted-foreground">Chỉ ADMIN mới được hoàn tiền.</span>
        )}
      </div>

      {error && <p className="mt-4 text-sm text-destructive">{error}</p>}

      <div className="mt-4 rounded-xl border border-border">
        {loading ? (
          <p className="p-4 text-sm text-muted-foreground">Đang tải...</p>
        ) : items.length === 0 ? (
          <p className="p-4 text-sm text-muted-foreground">Chưa có đơn mua nào.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Người mua</TableHead>
                <TableHead>Tài liệu</TableHead>
                <TableHead>Giá</TableHead>
                <TableHead>Trạng thái</TableHead>
                <TableHead>Hết hạn</TableHead>
                <TableHead className="text-right">Hành động</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((row) => (
                <TableRow key={row.id}>
                  <TableCell>
                    <p className="text-foreground">{row.displayName ?? "—"}</p>
                    <p className="text-xs text-muted-foreground">
                      @{row.username ?? row.userId.slice(0, 8)}
                    </p>
                  </TableCell>
                  <TableCell>
                    <span className="font-mono text-primary">{row.code}</span>
                  </TableCell>
                  <TableCell>{formatPoints(row.unitPricePoints)}</TableCell>
                  <TableCell className="text-xs">
                    {SOURCE_PURCHASE_STATUS_LABELS[row.status] ?? row.status}
                  </TableCell>
                  <TableCell className="text-muted-foreground">{formatDateTime(row.endsAt)}</TableCell>
                  <TableCell className="text-right">
                    {canRefund && row.status === "active" && !row.refunded ? (
                      <button
                        type="button"
                        className="text-sm text-destructive hover:underline"
                        onClick={() => {
                          setRefundTarget(row);
                          setRefundReason("");
                        }}
                      >
                        Hoàn tiền
                      </button>
                    ) : row.refunded ? (
                      <span className="text-xs text-amber-500">Đã hoàn</span>
                    ) : null}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </div>

      <Dialog open={refundTarget != null} onOpenChange={(o) => !o && setRefundTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Hoàn tiền đơn mua</DialogTitle>
            <DialogDescription>
              {refundTarget &&
                `Hoàn ${formatPoints(refundTarget.unitPricePoints)} cho @${refundTarget.username ?? ""} (${refundTarget.code}).`}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="reason">Lý do (tùy chọn)</Label>
            <Input
              id="reason"
              value={refundReason}
              onChange={(e) => setRefundReason(e.target.value)}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRefundTarget(null)} disabled={refunding}>
              Hủy
            </Button>
            <Button variant="destructive" onClick={submitRefund} disabled={refunding}>
              {refunding ? "Đang hoàn..." : "Xác nhận hoàn tiền"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </AdminShell>
  );
}
