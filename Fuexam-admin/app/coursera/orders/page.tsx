"use client";

import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { AdminShell } from "@/components/admin/AdminShell";
import {
  ConfirmStatusDialog,
  type StatusActionTarget,
} from "@/components/admin/ConfirmStatusDialog";
import { StatCard } from "@/components/admin/StatCard";
import { Button } from "@/components/ui/button";
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
import { listAdminUsers } from "@/lib/api/admin";
import {
  type AdminCatalogItem,
  type AdminRequestDetail,
  type CourseraOverview,
  type RequestSummary,
  REQUEST_STATUS_LABELS,
  formatPoints,
  getAdminRequest,
  getCourseraOverview,
  listAdminCatalog,
  listAdminRequests,
  resolveAllowedNextStatuses,
  updateRequestStatus,
} from "@/lib/api/coursera";
import { ApiError } from "@/lib/api/client";
import { formatDateTime } from "@/lib/format-datetime";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";
import type { AdminUserSummary } from "@/types/api";

const ALL = "all";
const STATUS_OPTIONS = ["pending", "in_progress", "completed", "cancelled"];
const PERIOD_OPTIONS = [
  { value: ALL, label: "Mọi thời gian" },
  { value: "7d", label: "7 ngày qua" },
  { value: "30d", label: "30 ngày qua" },
  { value: "90d", label: "90 ngày qua" },
];

const ACTION_BUTTONS: { status: StatusActionTarget; label: string; destructive?: boolean }[] = [
  { status: "in_progress", label: "Bắt đầu xử lý" },
  { status: "completed", label: "Hoàn thành" },
  { status: "cancelled", label: "Hủy đơn", destructive: true },
];

export default function AdminCourseraOrdersPage() {
  const { user } = useAuth();
  const canUpdate = can(user, "coursera.request.admin:update");

  const [overview, setOverview] = useState<CourseraOverview | null>(null);
  const [items, setItems] = useState<RequestSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState(ALL);
  const [periodFilter, setPeriodFilter] = useState(ALL);
  const [userFilter, setUserFilter] = useState(ALL);
  const [catalogFilter, setCatalogFilter] = useState(ALL);
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
          status: statusFilter === ALL ? undefined : statusFilter,
          period: periodFilter === ALL ? undefined : periodFilter,
          userId: userFilter === ALL ? undefined : userFilter,
          catalogItemId: catalogFilter === ALL ? undefined : catalogFilter,
          page: 0,
          size: 50,
        }),
      ]);
      setOverview(ov);
      setItems(page.items);
    } catch (err) {
      setOverview(null);
      setItems([]);
      setError(err instanceof ApiError ? err.message : "Không tải được danh sách đơn.");
    } finally {
      setLoading(false);
    }
  }, [statusFilter, periodFilter, userFilter, catalogFilter]);

  useEffect(() => {
    load();
  }, [load]);

  async function openDetail(id: string) {
    try {
      setDetail(await getAdminRequest(id));
      setPendingAction(null);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Không tải được chi tiết đơn.");
    }
  }

  async function applyStatus(target: StatusActionTarget, note: string) {
    if (!detail) return;
    await updateRequestStatus(detail.id, target, note || undefined);
    toast.success("Đã cập nhật trạng thái đơn.");
    await load();
    await openDetail(detail.id);
  }

  function clearFilters() {
    setStatusFilter(ALL);
    setPeriodFilter(ALL);
    setUserFilter(ALL);
    setCatalogFilter(ALL);
  }

  const allowedNext = detail ? resolveAllowedNextStatuses(detail) : [];
  const isTerminal = detail != null && allowedNext.length === 0;
  const hasActiveFilters =
    statusFilter !== ALL || periodFilter !== ALL || userFilter !== ALL || catalogFilter !== ALL;

  return (
    <AdminShell title="Coursera — Đơn dịch vụ" description="Quản lý yêu cầu và đổi trạng thái">
      {error && (
        <div className="mb-4 rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      )}

      {overview && (
        <div className="mb-6 grid grid-cols-2 gap-3 sm:grid-cols-5">
          <StatCard label="Tổng đơn" value={overview.totalRequests} />
          <StatCard label="Chờ" value={overview.pending} accent="warning" />
          <StatCard label="Đang làm" value={overview.inProgress} />
          <StatCard label="Xong" value={overview.completed} accent="success" />
          <StatCard label="Hủy" value={overview.cancelled} accent="danger" />
        </div>
      )}

      <div className="mb-4 flex flex-wrap items-end gap-2">
        <Select value={periodFilter} onValueChange={setPeriodFilter}>
          <SelectTrigger className="w-[160px]">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {PERIOD_OPTIONS.map((o) => (
              <SelectItem key={o.value} value={o.value}>
                {o.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={userFilter} onValueChange={setUserFilter}>
          <SelectTrigger className="w-[180px]">
            <SelectValue placeholder="Khách hàng" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>Tất cả khách hàng</SelectItem>
            {users.map((u) => (
              <SelectItem key={u.id} value={u.id}>
                @{u.username}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={catalogFilter} onValueChange={setCatalogFilter}>
          <SelectTrigger className="w-[200px]">
            <SelectValue placeholder="Khóa học" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>Tất cả khóa học</SelectItem>
            {catalog.map((c) => (
              <SelectItem key={c.id} value={c.id}>
                {c.code} — {c.title}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={statusFilter} onValueChange={setStatusFilter}>
          <SelectTrigger className="w-[160px]">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>Tất cả trạng thái</SelectItem>
            {STATUS_OPTIONS.map((s) => (
              <SelectItem key={s} value={s}>
                {REQUEST_STATUS_LABELS[s]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {hasActiveFilters && (
          <Button variant="outline" onClick={clearFilters}>
            Xóa lọc
          </Button>
        )}
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <div className="rounded-xl border border-border">
          {loading ? (
            <p className="p-4 text-sm text-muted-foreground">Đang tải...</p>
          ) : items.length === 0 ? (
            <p className="p-4 text-sm text-muted-foreground">Không có đơn phù hợp.</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Tạo lúc</TableHead>
                  <TableHead>Khách</TableHead>
                  <TableHead>Môn</TableHead>
                  <TableHead>Giá</TableHead>
                  <TableHead>TT</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {items.map((row) => (
                  <TableRow
                    key={row.id}
                    className="cursor-pointer"
                    data-state={detail?.id === row.id ? "selected" : undefined}
                    onClick={() => openDetail(row.id)}
                  >
                    <TableCell className="whitespace-nowrap text-xs text-muted-foreground">
                      {formatDateTime(row.createdAt)}
                    </TableCell>
                    <TableCell className="text-xs">@{row.username ?? "—"}</TableCell>
                    <TableCell>
                      <span className="text-primary">{row.catalogCode}</span>
                      <span className="block max-w-[140px] truncate text-[11px] text-muted-foreground">
                        {row.catalogTitle}
                      </span>
                    </TableCell>
                    <TableCell className="whitespace-nowrap">{formatPoints(row.totalPoints)}</TableCell>
                    <TableCell className="whitespace-nowrap text-xs">
                      {REQUEST_STATUS_LABELS[row.status] ?? row.status}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </div>

        <div className="min-h-[280px] rounded-xl border border-border bg-card p-5">
          {!detail ? (
            <p className="text-sm text-muted-foreground">
              Chọn một đơn để xem chi tiết và thông tin tài khoản.
            </p>
          ) : (
            <div className="space-y-3 text-sm">
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="font-semibold text-foreground">@{detail.username}</h3>
                <span className="rounded-full bg-muted px-2.5 py-0.5 text-xs font-medium text-primary">
                  {REQUEST_STATUS_LABELS[detail.status] ?? detail.status}
                </span>
                {detail.refunded && (
                  <span className="rounded-full bg-emerald-500/15 px-2.5 py-0.5 text-xs text-emerald-500">
                    Đã hoàn {formatPoints(detail.totalPoints)}
                  </span>
                )}
              </div>
              <div className="space-y-1 rounded-lg border border-border bg-muted/30 px-3 py-2 text-xs text-muted-foreground">
                <p>Tạo đơn: {formatDateTime(detail.createdAt)}</p>
                <p>Cập nhật TT: {formatDateTime(detail.statusChangedAt)}</p>
              </div>
              {detail.items[0] && (
                <p>
                  <span className="text-muted-foreground">Môn học:</span>{" "}
                  <span className="text-primary">{detail.items[0].title}</span>
                </p>
              )}
              <p>
                <span className="text-muted-foreground">Email Coursera:</span> {detail.courseraEmail}
              </p>
              <p>
                <span className="text-muted-foreground">Mật khẩu:</span>{" "}
                <code className="rounded bg-muted px-1 text-primary">{detail.courseraPassword}</code>
              </p>
              <p>
                <span className="text-muted-foreground">Tổng:</span> {formatPoints(detail.totalPoints)}
              </p>
              {detail.userNotes && (
                <p>
                  <span className="text-muted-foreground">Ghi chú user:</span> {detail.userNotes}
                </p>
              )}
              {canUpdate && (
                <div className="space-y-2 border-t border-border pt-3">
                  <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                    Hành động
                  </p>
                  {isTerminal ? (
                    <p className="text-sm text-muted-foreground">
                      Đơn đã kết thúc — không thể đổi trạng thái.
                    </p>
                  ) : (
                    <div className="flex flex-wrap gap-2">
                      {ACTION_BUTTONS.filter((a) => allowedNext.includes(a.status)).map((action) => (
                        <Button
                          key={action.status}
                          size="sm"
                          variant={action.destructive ? "destructive" : "default"}
                          onClick={() => setPendingAction(action.status)}
                        >
                          {action.label}
                        </Button>
                      ))}
                    </div>
                  )}
                </div>
              )}
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
