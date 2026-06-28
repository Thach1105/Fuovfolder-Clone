"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { AdminShell } from "@/components/admin/AdminShell";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { ApiError } from "@/lib/api/client";
import * as adminPaymentApi from "@/lib/api/admin-payment";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";
import type {
  AdminOrderPageResponse,
  AdminOrderDetailResponse,
  PaymentAnalyticsResponse,
} from "@/types/api";

const formatVnd = (n: number) => `${n.toLocaleString("vi-VN")} ₫`;
const formatPoints = (n: number) => n.toLocaleString("vi-VN");
const formatDate = (s: string) =>
  new Date(s).toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "short" });
const formatPercent = (n: number) => `${(n * 100).toFixed(1)}%`;

const STATUS_CONFIG: Record<string, { label: string; className: string }> = {
  paid: { label: "Đã thanh toán", className: "bg-emerald-500/15 text-emerald-500" },
  pending: { label: "Chờ thanh toán", className: "bg-yellow-500/15 text-yellow-500" },
  expired: { label: "Hết hạn", className: "bg-muted text-muted-foreground" },
  failed: { label: "Thất bại", className: "bg-red-500/15 text-red-500" },
};

const PERIODS = [
  { label: "7 ngày", days: 7 },
  { label: "30 ngày", days: 30 },
  { label: "90 ngày", days: 90 },
];

export default function AdminPaymentsPage() {
  const { user } = useAuth();
  const canViewAnalytics = can(user, "payment.admin:analytics");

  const [analytics, setAnalytics] = useState<PaymentAnalyticsResponse | null>(null);
  const [orders, setOrders] = useState<AdminOrderPageResponse | null>(null);
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState("");
  const [period, setPeriod] = useState(30);
  const [selectedOrder, setSelectedOrder] = useState<AdminOrderDetailResponse | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);

  const fromDate = useMemo(() => {
    const d = new Date();
    d.setDate(d.getDate() - period);
    return d.toISOString();
  }, [period]);

  const loadAnalytics = useCallback(async () => {
    if (!canViewAnalytics) return;
    try {
      const data = await adminPaymentApi.getAnalytics({ fromDate });
      setAnalytics(data);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Không tải được thống kê");
    }
  }, [canViewAnalytics, fromDate]);

  const loadOrders = useCallback(async () => {
    setLoading(true);
    try {
      const data = await adminPaymentApi.listOrders({
        page,
        size: 20,
        status: statusFilter || undefined,
        fromDate,
      });
      setOrders(data);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Không tải được danh sách đơn");
    } finally {
      setLoading(false);
    }
  }, [page, statusFilter, fromDate]);

  useEffect(() => {
    loadAnalytics();
  }, [loadAnalytics]);

  useEffect(() => {
    loadOrders();
  }, [loadOrders]);

  async function openDetail(orderId: string) {
    setDetailLoading(true);
    setDetailOpen(true);
    try {
      const data = await adminPaymentApi.getOrder(orderId);
      setSelectedOrder(data);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Không tải được chi tiết đơn");
      setDetailOpen(false);
    } finally {
      setDetailLoading(false);
    }
  }

  return (
    <AdminShell
      title="Quản lý nạp tiền"
      description="Xem danh sách đơn nạp tiền, thống kê doanh thu và quản lý thanh toán"
    >
      {/* Period selector */}
      <div className="mb-6 flex items-center gap-2">
        <span className="text-sm text-muted-foreground">Thời gian:</span>
        {PERIODS.map((p) => (
          <Button
            key={p.days}
            variant={period === p.days ? "default" : "outline"}
            size="sm"
            onClick={() => { setPeriod(p.days); setPage(0); }}
          >
            {p.label}
          </Button>
        ))}
      </div>

      {/* Analytics section */}
      {canViewAnalytics && analytics && (
        <div className="mb-8 space-y-4">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-xs font-medium text-muted-foreground">Tổng doanh thu</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-2xl font-bold text-emerald-500">{formatVnd(analytics.totalRevenue)}</p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-xs font-medium text-muted-foreground">Số giao dịch</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-2xl font-bold">{analytics.totalTransactions}</p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-xs font-medium text-muted-foreground">Tổng points cấp</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-2xl font-bold text-primary">{formatPoints(analytics.totalPointsIssued)}</p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-xs font-medium text-muted-foreground">Tỷ lệ chuyển đổi</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-2xl font-bold">{formatPercent(analytics.conversionRate)}</p>
                <p className="text-xs text-muted-foreground">
                  Trung bình: {formatVnd(analytics.averageDepositAmount)}
                </p>
              </CardContent>
            </Card>
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            {/* Revenue by tier */}
            {analytics.revenueByTier.length > 0 && (
              <Card>
                <CardHeader>
                  <CardTitle className="text-sm">Doanh thu theo gói</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="space-y-2">
                    {analytics.revenueByTier.map((t, i) => (
                      <div key={i} className="flex items-center justify-between text-sm">
                        <span className="text-muted-foreground">{t.tierLabel ?? "Không xác định"}</span>
                        <div className="text-right">
                          <span className="font-medium">{formatVnd(t.totalRevenue)}</span>
                          <span className="ml-2 text-xs text-muted-foreground">({t.count} đơn)</span>
                        </div>
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>
            )}

            {/* Top users */}
            {analytics.topUsers.length > 0 && (
              <Card>
                <CardHeader>
                  <CardTitle className="text-sm">Top người nạp nhiều nhất</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="space-y-2">
                    {analytics.topUsers.map((u, i) => (
                      <div key={i} className="flex items-center justify-between text-sm">
                        <span className="text-muted-foreground">
                          {u.username ?? u.userId.slice(0, 8)}
                        </span>
                        <div className="text-right">
                          <span className="font-medium">{formatVnd(u.totalDeposited)}</span>
                          <span className="ml-2 text-xs text-muted-foreground">({u.transactionCount} đơn)</span>
                        </div>
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>
            )}
          </div>
        </div>
      )}

      {/* Status breakdown badges */}
      {canViewAnalytics && analytics && (
        <div className="mb-4 flex flex-wrap gap-2">
          {Object.entries(analytics.statusBreakdown).map(([status, count]) => {
            const cfg = STATUS_CONFIG[status];
            if (!cfg || count === 0) return null;
            return (
              <button
                key={status}
                type="button"
                onClick={() => { setStatusFilter(statusFilter === status ? "" : status); setPage(0); }}
                className={`rounded-full px-3 py-1 text-xs font-medium transition ${
                  statusFilter === status ? "ring-2 ring-primary" : ""
                } ${cfg.className}`}
              >
                {cfg.label}: {count}
              </button>
            );
          })}
          {statusFilter && (
            <button
              type="button"
              onClick={() => { setStatusFilter(""); setPage(0); }}
              className="rounded-full bg-muted px-3 py-1 text-xs text-muted-foreground hover:text-foreground"
            >
              Xóa bộ lọc
            </button>
          )}
        </div>
      )}

      {/* Orders table */}
      <Card>
        <CardHeader>
          <CardTitle className="text-sm">
            Danh sách đơn nạp tiền {orders ? `(${orders.totalElements})` : ""}
          </CardTitle>
        </CardHeader>
        <CardContent>
          {loading && <p className="text-sm text-muted-foreground">Đang tải...</p>}

          {!loading && orders && orders.items.length === 0 && (
            <p className="text-sm text-muted-foreground">Không có đơn nào.</p>
          )}

          {!loading && orders && orders.items.length > 0 && (
            <>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-border text-left text-xs text-muted-foreground">
                      <th className="pb-2 pr-3">Mã đơn</th>
                      <th className="pb-2 pr-3">User</th>
                      <th className="pb-2 pr-3">Số tiền</th>
                      <th className="pb-2 pr-3">Gói</th>
                      <th className="pb-2 pr-3">Points</th>
                      <th className="pb-2 pr-3">Trạng thái</th>
                      <th className="pb-2">Ngày tạo</th>
                    </tr>
                  </thead>
                  <tbody>
                    {orders.items.map((order) => {
                      const cfg = STATUS_CONFIG[order.status] ?? {
                        label: order.status,
                        className: "bg-muted text-muted-foreground",
                      };
                      return (
                        <tr
                          key={order.orderId}
                          className="cursor-pointer border-b border-border/50 transition hover:bg-muted/50"
                          onClick={() => openDetail(order.orderId)}
                        >
                          <td className="py-2.5 pr-3 font-mono text-xs">{order.orderCode}</td>
                          <td className="py-2.5 pr-3">
                            <p className="font-medium">{order.username ?? "—"}</p>
                            <p className="text-xs text-muted-foreground">{order.email ?? ""}</p>
                          </td>
                          <td className="py-2.5 pr-3 font-medium">{formatVnd(order.amount)}</td>
                          <td className="py-2.5 pr-3 text-muted-foreground">{order.tierLabel ?? "—"}</td>
                          <td className="py-2.5 pr-3">{order.pointsAwarded != null ? formatPoints(order.pointsAwarded) : "—"}</td>
                          <td className="py-2.5 pr-3">
                            <span className={`rounded px-2 py-0.5 text-[10px] font-semibold uppercase ${cfg.className}`}>
                              {cfg.label}
                            </span>
                          </td>
                          <td className="py-2.5 text-muted-foreground">{formatDate(order.createdAt)}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>

              {/* Pagination */}
              <div className="mt-4 flex items-center justify-between text-sm">
                <p className="text-muted-foreground">
                  Trang {orders.page + 1} / {orders.totalPages} ({orders.totalElements} đơn)
                </p>
                <div className="flex gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={page === 0}
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                  >
                    Trước
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={page >= orders.totalPages - 1}
                    onClick={() => setPage((p) => p + 1)}
                  >
                    Sau
                  </Button>
                </div>
              </div>
            </>
          )}
        </CardContent>
      </Card>

      {/* Order detail dialog */}
      <AlertDialog open={detailOpen} onOpenChange={(open) => { if (!open) { setDetailOpen(false); setSelectedOrder(null); } }}>
        <AlertDialogContent className="max-w-lg">
          <AlertDialogHeader>
            <AlertDialogTitle>Chi tiết đơn hàng</AlertDialogTitle>
          </AlertDialogHeader>

          {detailLoading && <p className="text-sm text-muted-foreground">Đang tải...</p>}

          {!detailLoading && selectedOrder && (
            <div className="space-y-4 text-sm">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <p className="text-xs text-muted-foreground">Mã đơn</p>
                  <p className="font-mono">{selectedOrder.orderCode}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">Trạng thái</p>
                  <span className={`rounded px-2 py-0.5 text-[10px] font-semibold uppercase ${(STATUS_CONFIG[selectedOrder.status] ?? { className: "bg-muted text-muted-foreground" }).className}`}>
                    {(STATUS_CONFIG[selectedOrder.status] ?? { label: selectedOrder.status }).label}
                  </span>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">Số tiền</p>
                  <p className="font-medium">{formatVnd(selectedOrder.amount)}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">Points</p>
                  <p>{selectedOrder.pointsAwarded != null ? formatPoints(selectedOrder.pointsAwarded) : "—"}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">Gói nạp</p>
                  <p>{selectedOrder.tierLabel ?? "—"}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">Ngày tạo</p>
                  <p>{formatDate(selectedOrder.createdAt)}</p>
                </div>
              </div>

              {/* User info */}
              <div className="rounded-lg border border-border p-3">
                <p className="mb-1 text-xs font-medium text-muted-foreground">Người dùng</p>
                <p className="font-medium">{selectedOrder.user.displayName ?? selectedOrder.user.username ?? "—"}</p>
                <p className="text-xs text-muted-foreground">{selectedOrder.user.email ?? ""}</p>
              </div>

              {/* Payment records */}
              {selectedOrder.payments.length > 0 && (
                <div>
                  <p className="mb-2 text-xs font-medium text-muted-foreground">Thanh toán</p>
                  {selectedOrder.payments.map((p) => (
                    <div key={p.paymentId} className="flex items-center justify-between rounded border border-border/50 px-3 py-2 text-xs">
                      <span>{formatVnd(p.amountCents)}</span>
                      <span className={`rounded px-2 py-0.5 font-semibold uppercase ${(STATUS_CONFIG[p.status] ?? { className: "bg-muted text-muted-foreground" }).className}`}>
                        {(STATUS_CONFIG[p.status] ?? { label: p.status }).label}
                      </span>
                      <span className="text-muted-foreground">{p.paidAt ? formatDate(p.paidAt) : "—"}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          <div className="mt-4 flex justify-end">
            <AlertDialogCancel>Đóng</AlertDialogCancel>
          </div>
        </AlertDialogContent>
      </AlertDialog>
    </AdminShell>
  );
}
