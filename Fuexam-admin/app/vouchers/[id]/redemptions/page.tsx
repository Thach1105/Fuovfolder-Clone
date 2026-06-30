"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { AdminShell } from "@/components/admin/AdminShell";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { ApiError } from "@/lib/api/client";
import * as voucherApi from "@/lib/api/admin-vouchers";
import type { VoucherResponse, VoucherRedemptionResponse } from "@/lib/api/admin-vouchers";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";

const PAGE_SIZE = 20;

function transactionBadgeVariant(type: string): "default" | "secondary" | "outline" {
  if (type.toLowerCase().includes("source")) return "default";
  if (type.toLowerCase().includes("membership")) return "secondary";
  return "outline";
}

export default function VoucherRedemptionsPage() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const canRead = can(user, "voucher.admin:read");

  const [voucher, setVoucher] = useState<VoucherResponse | null>(null);
  const [redemptions, setRedemptions] = useState<VoucherRedemptionResponse[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(
    async (p: number) => {
      setLoading(true);
      setLoadError(null);
      try {
        const [v, redemptionPage] = await Promise.all([
          voucher ? Promise.resolve(voucher) : voucherApi.getVoucher(id),
          voucherApi.listVoucherRedemptions(id, p, PAGE_SIZE),
        ]);
        setVoucher(v as VoucherResponse);
        setRedemptions(redemptionPage.content);
        setTotalPages(redemptionPage.totalPages);
        setTotalElements(redemptionPage.totalElements);
      } catch (err) {
        setLoadError(err instanceof ApiError ? err.message : "Không tải được dữ liệu.");
      } finally {
        setLoading(false);
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [id]
  );

  useEffect(() => {
    load(page);
  }, [load, page]);

  const title = voucher ? `Lịch sử dùng: ${voucher.code}` : "Lịch sử dùng voucher";

  return (
    <AdminShell
      title={title}
      description="Xem lịch sử các giao dịch đã áp dụng voucher này"
      actions={
        <Link
          href="/vouchers"
          className="text-sm text-muted-foreground hover:text-foreground transition-colors"
        >
          ← Quay lại Voucher
        </Link>
      }
    >
      {!canRead && (
        <p className="mb-4 text-xs text-amber-500">
          Thiếu quyền voucher.admin:read để xem trang này.
        </p>
      )}

      {loadError && (
        <div className="mb-6 rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {loadError}
        </div>
      )}

      {loading && <p className="text-sm text-muted-foreground">Đang tải...</p>}

      {!loading && (
        <div className="space-y-4">
          {/* Summary */}
          {voucher && (
            <div className="flex items-center gap-4 text-sm text-muted-foreground">
              <span className="font-mono font-semibold text-foreground">{voucher.code}</span>
              <span>·</span>
              <span>Tổng lượt dùng: <strong className="text-foreground">{voucher.usedCount}</strong></span>
              <span>·</span>
              <span>Kết quả: <strong className="text-foreground">{totalElements}</strong></span>
            </div>
          )}

          <Card>
            <CardContent className="p-0">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-border bg-muted/40">
                      <th className="px-4 py-3 text-left font-semibold text-muted-foreground">User ID</th>
                      <th className="px-4 py-3 text-left font-semibold text-muted-foreground">Loại giao dịch</th>
                      <th className="px-4 py-3 text-right font-semibold text-muted-foreground">Điểm gốc</th>
                      <th className="px-4 py-3 text-right font-semibold text-muted-foreground">Giảm giá</th>
                      <th className="px-4 py-3 text-right font-semibold text-muted-foreground">Điểm thanh toán</th>
                      <th className="px-4 py-3 text-left font-semibold text-muted-foreground">Thời gian</th>
                    </tr>
                  </thead>
                  <tbody>
                    {redemptions.length === 0 && (
                      <tr>
                        <td colSpan={6} className="px-4 py-8 text-center text-muted-foreground">
                          Chưa có lượt dùng nào.
                        </td>
                      </tr>
                    )}
                    {redemptions.map((r) => (
                      <tr
                        key={r.id}
                        className="border-b border-border last:border-0 hover:bg-muted/20 transition-colors"
                      >
                        <td className="px-4 py-3 font-mono text-xs text-foreground">
                          {r.userId}
                        </td>
                        <td className="px-4 py-3">
                          <Badge variant={transactionBadgeVariant(r.transactionType)}>
                            {r.transactionType}
                          </Badge>
                        </td>
                        <td className="px-4 py-3 text-right text-foreground">
                          {r.originalPoints.toLocaleString("vi-VN")}
                        </td>
                        <td className="px-4 py-3 text-right text-emerald-500 font-medium">
                          -{r.discountPoints.toLocaleString("vi-VN")}
                        </td>
                        <td className="px-4 py-3 text-right font-semibold text-foreground">
                          {r.finalPoints.toLocaleString("vi-VN")}
                        </td>
                        <td className="px-4 py-3 text-muted-foreground text-xs whitespace-nowrap">
                          {new Date(r.createdAt).toLocaleString("vi-VN")}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </CardContent>
          </Card>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between text-sm">
              <span className="text-muted-foreground">
                Trang {page + 1} / {totalPages}
              </span>
              <div className="flex gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0}
                >
                  ← Trước
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                  disabled={page >= totalPages - 1}
                >
                  Sau →
                </Button>
              </div>
            </div>
          )}
        </div>
      )}
    </AdminShell>
  );
}
