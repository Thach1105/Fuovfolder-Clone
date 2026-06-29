"use client";

import { useCallback, useEffect, useState } from "react";
import { AdminShell } from "@/components/admin/AdminShell";
import { ErrorBanner } from "@/components/ui/error-banner";
import { LoadingState } from "@/components/ui/loading-state";
import { useAsyncAction } from "@/hooks/use-async-action";
import { ApiError } from "@/lib/api/client";
import { ADMIN_PAGE_SIZE } from "@/lib/constants/pagination";
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
import { toFilterOptions } from "@/lib/constants/status-labels";

const STATUS_FILTER_OPTIONS = toFilterOptions(SOURCE_PURCHASE_STATUS_LABELS);

const selectClass =
  "rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white min-w-[140px]";

function OverviewCard({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-4">
      <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">{label}</p>
      <p className="mt-1 text-xl font-bold text-white">{value}</p>
    </div>
  );
}

export default function AdminSourcePurchasesPage() {
  const { user } = useAuth();
  const canRefund = canRefundSource(user);
  const [overview, setOverview] = useState<SourceOverview | null>(null);
  const [items, setItems] = useState<AdminSourcePurchase[]>([]);
  const [statusFilter, setStatusFilter] = useState("");
  const [codeFilter, setCodeFilter] = useState("");
  const { loading, error: loadError, run } = useAsyncAction("Không tải được dữ liệu");
  const [actionError, setActionError] = useState<string | null>(null);
  const [refundingId, setRefundingId] = useState<string | null>(null);

  const error = loadError || actionError;

  const load = useCallback(() => run(async () => {
    const [ov, page] = await Promise.all([
      getSourceOverview(),
      listAdminSourcePurchases({
        status: statusFilter || undefined,
        code: codeFilter.trim() || undefined,
        size: ADMIN_PAGE_SIZE,
      }),
    ]);
    setOverview(ov);
    setItems(page.items);
  }), [run, statusFilter, codeFilter]);

  useEffect(() => {
    load();
  }, [load]);

  async function handleRefund(purchase: AdminSourcePurchase) {
    const reason = prompt(`Lý do hoàn tiền cho ${purchase.code}? (tùy chọn)`);
    if (reason === null) return;
    setRefundingId(purchase.id);
    setActionError(null);
    try {
      await refundSourcePurchase(purchase.id, reason.trim() || undefined);
      await load();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Hoàn tiền thất bại");
    } finally {
      setRefundingId(null);
    }
  }

  return (
    <AdminShell title="Source — Đơn mua" description="Theo dõi giao dịch và hoàn tiền Fuexam Point">
      {overview && (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
          <OverviewCard label="Tổng đơn" value={overview.totalPurchases} />
          <OverviewCard label="Còn hiệu lực" value={overview.activePurchases} />
          <OverviewCard label="Đã hoàn" value={overview.refundedPurchases} />
          <OverviewCard label="Tài liệu đang bán" value={overview.activeCatalogItems} />
          <OverviewCard label="Điểm đã thu" value={overview.paidPoints.toLocaleString("vi-VN")} />
          <OverviewCard label="Điểm đã hoàn" value={overview.refundedPoints.toLocaleString("vi-VN")} />
        </div>
      )}

      <div className="mt-6 flex flex-wrap items-end gap-3">
        <div>
          <label className="block text-xs text-slate-400">Trạng thái</label>
          <select
            className={`mt-1 ${selectClass}`}
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
          >
            {STATUS_FILTER_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-xs text-slate-400">Mã môn</label>
          <input
            className={`mt-1 ${selectClass}`}
            placeholder="MLN111"
            value={codeFilter}
            onChange={(e) => setCodeFilter(e.target.value)}
          />
        </div>
        <button type="button" className="rounded-lg bg-amber-500 px-4 py-2 text-sm font-semibold text-slate-950" onClick={() => load()}>
          Lọc
        </button>
        {!canRefund && (
          <span className="text-xs text-slate-500">Chỉ ADMIN mới được hoàn tiền.</span>
        )}
      </div>

      <ErrorBanner message={error} className="mt-4" />

      <div className="mt-4 overflow-hidden rounded-xl border border-slate-800">
        {loading ? (
          <LoadingState className="p-4" />
        ) : items.length === 0 ? (
          <p className="p-4 text-slate-400">Chưa có đơn mua nào.</p>
        ) : (
          <table className="w-full text-left text-sm text-slate-300">
            <thead className="bg-slate-900 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-3 py-2">Người mua</th>
                <th className="px-3 py-2">Tài liệu</th>
                <th className="px-3 py-2">Giá</th>
                <th className="px-3 py-2">Trạng thái</th>
                <th className="px-3 py-2">Hết hạn</th>
                <th className="px-3 py-2" />
              </tr>
            </thead>
            <tbody>
              {items.map((row) => (
                <tr key={row.id} className="border-t border-slate-800">
                  <td className="px-3 py-2">
                    <p className="text-slate-200">{row.displayName ?? "—"}</p>
                    <p className="text-xs text-slate-500">@{row.username ?? row.userId.slice(0, 8)}</p>
                  </td>
                  <td className="px-3 py-2">
                    <span className="font-mono text-amber-400">{row.code}</span>
                  </td>
                  <td className="px-3 py-2">{formatPoints(row.unitPricePoints)}</td>
                  <td className="px-3 py-2 text-xs">
                    {SOURCE_PURCHASE_STATUS_LABELS[row.status] ?? row.status}
                  </td>
                  <td className="px-3 py-2 text-slate-400">
                    {new Date(row.endsAt).toLocaleDateString("vi-VN")}
                  </td>
                  <td className="px-3 py-2 text-right">
                    {canRefund && row.status === "active" && !row.refunded ? (
                      <button
                        type="button"
                        className="text-red-400 hover:underline disabled:opacity-50"
                        disabled={refundingId === row.id}
                        onClick={() => handleRefund(row)}
                      >
                        {refundingId === row.id ? "Đang hoàn..." : "Hoàn tiền"}
                      </button>
                    ) : row.refunded ? (
                      <span className="text-xs text-amber-400">Đã hoàn</span>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </AdminShell>
  );
}
