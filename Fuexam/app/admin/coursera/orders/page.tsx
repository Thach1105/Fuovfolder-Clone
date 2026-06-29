"use client";

import { useCallback, useEffect, useState } from "react";
import { AdminShell } from "@/components/admin/AdminShell";
import {
  ConfirmStatusDialog,
  type StatusActionTarget,
} from "@/components/admin/ConfirmStatusDialog";
import { listAdminUsers } from "@/lib/api/admin";
import {
  formatPoints,
  getAdminRequest,
  getCourseraOverview,
  listAdminCatalog,
  listAdminRequests,
  resolveAllowedNextStatuses,
  updateRequestStatus,
  type AdminCatalogItem,
  type AdminRequestDetail,
  type CourseraOverview,
  type RequestSummary,
} from "@/lib/api/coursera";
import { ApiError } from "@/lib/api/client";
import { ADMIN_PAGE_SIZE } from "@/lib/constants/pagination";
import { REQUEST_STATUS_LABELS, toFilterOptions } from "@/lib/constants/status-labels";
import { formatDateTime } from "@/lib/format-datetime";
import type { AdminUserSummary } from "@/types/api";

const STATUS_FILTER_OPTIONS = toFilterOptions(REQUEST_STATUS_LABELS);

const PERIOD_OPTIONS: { value: string; label: string }[] = [
  { value: "", label: "Mọi thời gian" },
  { value: "7d", label: "7 ngày qua" },
  { value: "30d", label: "30 ngày qua" },
  { value: "90d", label: "90 ngày qua" },
];

const ACTION_BUTTONS: {
  status: StatusActionTarget;
  label: string;
  className: string;
}[] = [
  {
    status: "in_progress",
    label: "Bắt đầu xử lý",
    className: "bg-sky-600 text-white hover:bg-sky-500",
  },
  {
    status: "completed",
    label: "Hoàn thành",
    className: "bg-emerald-600 text-white hover:bg-emerald-500",
  },
  {
    status: "cancelled",
    label: "Hủy đơn",
    className: "bg-red-700 text-white hover:bg-red-600",
  },
];

const selectClass =
  "rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white min-w-[140px]";

export default function AdminCourseraOrdersPage() {
  const [overview, setOverview] = useState<CourseraOverview | null>(null);
  const [items, setItems] = useState<RequestSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState("");
  const [periodFilter, setPeriodFilter] = useState("");
  const [userFilter, setUserFilter] = useState("");
  const [catalogFilter, setCatalogFilter] = useState("");
  const [users, setUsers] = useState<AdminUserSummary[]>([]);
  const [catalog, setCatalog] = useState<AdminCatalogItem[]>([]);
  const [detail, setDetail] = useState<AdminRequestDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [pendingAction, setPendingAction] = useState<StatusActionTarget | null>(null);

  useEffect(() => {
    Promise.all([
      listAdminUsers(0, 200).then((p) => setUsers(p.items)),
      listAdminCatalog().then(setCatalog),
    ]).catch(() => {
      /* filters degrade gracefully */
    });
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [ov, page] = await Promise.all([
        getCourseraOverview(),
        listAdminRequests({
          status: statusFilter || undefined,
          period: periodFilter || undefined,
          userId: userFilter || undefined,
          catalogItemId: catalogFilter || undefined,
          page: 0,
          size: ADMIN_PAGE_SIZE,
        }),
      ]);
      setOverview(ov);
      setItems(page.items);
    } catch (err) {
      setOverview(null);
      setItems([]);
      setError(
        err instanceof ApiError ? err.message : "Không tải được danh sách đơn dịch vụ.",
      );
    } finally {
      setLoading(false);
    }
  }, [statusFilter, periodFilter, userFilter, catalogFilter]);

  useEffect(() => {
    load();
  }, [load]);

  async function openDetail(id: string) {
    const d = await getAdminRequest(id);
    setDetail(d);
    setPendingAction(null);
  }

  async function applyStatus(target: StatusActionTarget, note: string) {
    if (!detail) return;
    await updateRequestStatus(detail.id, target, note || undefined);
    await load();
    await openDetail(detail.id);
  }

  function clearFilters() {
    setStatusFilter("");
    setPeriodFilter("");
    setUserFilter("");
    setCatalogFilter("");
  }

  const allowedNext = detail ? resolveAllowedNextStatuses(detail) : [];
  const isTerminal = detail != null && allowedNext.length === 0;
  const hasActiveFilters =
    statusFilter || periodFilter || userFilter || catalogFilter;

  return (
    <AdminShell title="Coursera — Đơn dịch vụ" description="Quản lý yêu cầu và đổi trạng thái">
      {error && (
        <p className="mb-4 rounded-lg border border-red-800 bg-red-950/50 px-4 py-3 text-sm text-red-200">
          {error}
        </p>
      )}

      {overview && (
        <div className="mb-6 grid grid-cols-2 gap-3 sm:grid-cols-5">
          <MiniStat label="Tổng đơn" value={overview.totalRequests} />
          <MiniStat label="Chờ" value={overview.pending} />
          <MiniStat label="Đang làm" value={overview.inProgress} />
          <MiniStat label="Xong" value={overview.completed} />
          <MiniStat label="Hủy" value={overview.cancelled} />
        </div>
      )}

      <div className="mb-4 rounded-xl border border-slate-800 bg-slate-900/40 p-4">
        <p className="mb-3 text-xs font-medium uppercase tracking-wide text-slate-500">Bộ lọc</p>
        <div className="flex flex-wrap gap-2">
          <select
            className={selectClass}
            value={periodFilter}
            onChange={(e) => setPeriodFilter(e.target.value)}
            aria-label="Lọc theo thời gian"
          >
            {PERIOD_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
          <select
            className={selectClass}
            value={userFilter}
            onChange={(e) => setUserFilter(e.target.value)}
            aria-label="Lọc theo khách hàng"
          >
            <option value="">Tất cả khách hàng</option>
            {users.map((u) => (
              <option key={u.id} value={u.id}>
                @{u.username}
                {u.displayName ? ` (${u.displayName})` : ""}
              </option>
            ))}
          </select>
          <select
            className={`${selectClass} min-w-[200px]`}
            value={catalogFilter}
            onChange={(e) => setCatalogFilter(e.target.value)}
            aria-label="Lọc theo môn học"
          >
            <option value="">Tất cả môn học</option>
            {catalog.map((c) => (
              <option key={c.id} value={c.id}>
                {c.code} — {c.title}
              </option>
            ))}
          </select>
          <select
            className={selectClass}
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            aria-label="Lọc theo trạng thái"
          >
            {STATUS_FILTER_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
          {hasActiveFilters && (
            <button
              type="button"
              className="rounded-lg border border-slate-600 px-4 py-2 text-sm text-slate-300 hover:bg-slate-800"
              onClick={clearFilters}
            >
              Xóa lọc
            </button>
          )}
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <div className="rounded-xl border border-slate-800 overflow-x-auto">
          {loading ? (
            <p className="p-4 text-slate-400">Đang tải...</p>
          ) : items.length === 0 ? (
            <p className="p-4 text-slate-500 text-sm">Không có đơn phù hợp bộ lọc.</p>
          ) : (
            <table className="w-full min-w-[640px] text-left text-sm text-slate-300">
              <thead className="bg-slate-900 text-xs text-slate-500">
                <tr>
                  <th className="px-3 py-2">Tạo lúc</th>
                  <th className="px-3 py-2">Khách</th>
                  <th className="px-3 py-2">Môn</th>
                  <th className="px-3 py-2">Giá</th>
                  <th className="px-3 py-2">TT</th>
                </tr>
              </thead>
              <tbody>
                {items.map((row) => (
                  <tr
                    key={row.id}
                    className={`border-t border-slate-800 cursor-pointer hover:bg-slate-900/80 ${
                      detail?.id === row.id ? "bg-slate-900/60" : ""
                    }`}
                    onClick={() => openDetail(row.id)}
                  >
                    <td className="px-3 py-2 whitespace-nowrap text-xs text-slate-400">
                      {formatDateTime(row.createdAt)}
                    </td>
                    <td className="px-3 py-2 text-xs">
                      @{row.username ?? "—"}
                    </td>
                    <td className="px-3 py-2">
                      <span className="text-amber-400">{row.catalogCode}</span>
                      <span className="block text-[11px] text-slate-500 line-clamp-1">
                        {row.catalogTitle}
                      </span>
                    </td>
                    <td className="px-3 py-2 whitespace-nowrap">{formatPoints(row.totalPoints)}</td>
                    <td className="px-3 py-2 whitespace-nowrap">
                      {REQUEST_STATUS_LABELS[row.status] ?? row.status}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-5 min-h-[280px]">
          {!detail ? (
            <p className="text-slate-500 text-sm">Chọn một đơn để xem chi tiết và credential.</p>
          ) : (
            <div className="space-y-3 text-sm text-slate-300">
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="font-semibold text-white">@{detail.username}</h3>
                <span className="rounded-full bg-slate-800 px-2.5 py-0.5 text-xs font-medium text-amber-200">
                  {REQUEST_STATUS_LABELS[detail.status] ?? detail.status}
                </span>
                {detail.refunded && (
                  <span className="rounded-full bg-emerald-950 px-2.5 py-0.5 text-xs text-emerald-300">
                    Đã hoàn {formatPoints(detail.totalPoints)}
                  </span>
                )}
              </div>
              <div className="rounded-lg border border-slate-800 bg-slate-950/50 px-3 py-2 text-xs text-slate-400 space-y-1">
                <p>
                  <span className="text-slate-500">Tạo đơn:</span>{" "}
                  {formatDateTime(detail.createdAt)}
                </p>
                <p>
                  <span className="text-slate-500">Cập nhật TT:</span>{" "}
                  {formatDateTime(detail.statusChangedAt)}
                </p>
              </div>
              {detail.items[0] && (
                <p>
                  <span className="text-slate-500">Môn học:</span>{" "}
                  <span className="text-amber-300">{detail.items[0].title}</span>
                </p>
              )}
              <p>
                <span className="text-slate-500">Email Coursera:</span> {detail.courseraEmail}
              </p>
              <p>
                <span className="text-slate-500">Mật khẩu:</span>{" "}
                <code className="rounded bg-slate-950 px-1 text-amber-200">{detail.courseraPassword}</code>
              </p>
              <p>
                <span className="text-slate-500">Tổng:</span> {formatPoints(detail.totalPoints)}
              </p>
              {detail.userNotes && (
                <p>
                  <span className="text-slate-500">Ghi chú user:</span> {detail.userNotes}
                </p>
              )}
              <div className="pt-3 border-t border-slate-800 space-y-2">
                <p className="text-xs font-medium uppercase tracking-wide text-slate-500">Hành động</p>
                {isTerminal ? (
                  <p className="text-sm text-slate-500">Đơn đã kết thúc — không thể đổi trạng thái.</p>
                ) : (
                  <div className="flex flex-wrap gap-2">
                    {ACTION_BUTTONS.filter((a) => allowedNext.includes(a.status)).map((action) => (
                      <button
                        key={action.status}
                        type="button"
                        className={`rounded-lg px-3 py-2 text-sm font-semibold ${action.className}`}
                        onClick={() => setPendingAction(action.status)}
                      >
                        {action.label}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      </div>

      <ConfirmStatusDialog
        open={pendingAction != null}
        targetStatus={pendingAction}
        totalPoints={detail?.totalPoints ?? 0}
        willRefund={
          pendingAction === "cancelled" &&
          detail != null &&
          !detail.refunded &&
          detail.paymentLedgerId != null
        }
        onClose={() => setPendingAction(null)}
        onConfirm={(note) => applyStatus(pendingAction!, note)}
      />
    </AdminShell>
  );
}

function MiniStat({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900/60 px-3 py-2 text-center">
      <p className="text-[10px] uppercase text-slate-500">{label}</p>
      <p className="text-lg font-bold text-white">{value}</p>
    </div>
  );
}
