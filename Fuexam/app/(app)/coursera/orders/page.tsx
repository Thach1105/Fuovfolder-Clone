"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { useAuth } from "@/lib/auth/AuthProvider";
import {
  REQUEST_STATUS_LABELS,
  formatPoints,
  getMyRequestStats,
  listMyRequests,
  type RequestStats,
  type RequestSummary,
} from "@/lib/api/coursera";
import { useRouter } from "next/navigation";

const STATUS_OPTIONS = [
  { value: "", label: "Tất cả" },
  { value: "pending", label: "Chờ xử lý" },
  { value: "in_progress", label: "Đang thực hiện" },
  { value: "completed", label: "Hoàn thành" },
  { value: "cancelled", label: "Đã hủy" },
];

export default function CourseraOrdersPage() {
  const router = useRouter();
  const { user, loading: authLoading } = useAuth();
  const [stats, setStats] = useState<RequestStats | null>(null);
  const [items, setItems] = useState<RequestSummary[]>([]);
  const [statusFilter, setStatusFilter] = useState("");
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [s, page] = await Promise.all([
        getMyRequestStats(),
        listMyRequests(statusFilter || undefined, 0, 50),
      ]);
      setStats(s);
      setItems(page.items);
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    if (!authLoading && !user) router.push("/login");
  }, [authLoading, user, router]);

  useEffect(() => {
    if (user) load();
  }, [user, load]);

  if (authLoading || !user) {
    return <p className="text-sm text-slate-500">Đang tải...</p>;
  }

  return (
    <div className="space-y-6">
      <div>
        <p className="text-sm text-fuo-600">
          <Link href="/coursera" className="hover:underline">
            Coursera
          </Link>
        </p>
        <h1 className="text-2xl font-bold text-slate-900">Lịch sử yêu cầu</h1>
        <p className="text-sm text-slate-600">
          Quản lý và theo dõi tất cả yêu cầu dịch vụ Coursera của bạn
        </p>
      </div>

      {stats && (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-5">
          <StatCard label="Tổng yêu cầu" value={stats.total} />
          <StatCard label="Chờ xử lý" value={stats.pending} />
          <StatCard label="Đang thực hiện" value={stats.inProgress} />
          <StatCard label="Hoàn thành" value={stats.completed} />
          <StatCard label="Đã hủy" value={stats.cancelled} />
        </div>
      )}

      <div className="card p-4">
        <div className="flex flex-wrap items-end gap-3">
          <div>
            <label className="text-xs font-medium text-slate-600">Trạng thái</label>
            <select
              className="input-field mt-1 min-w-[140px]"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
            >
              {STATUS_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </div>
          <button type="button" className="btn-primary" onClick={() => load()}>
            Lọc
          </button>
          <button
            type="button"
            className="btn-secondary"
            onClick={() => {
              setStatusFilter("");
            }}
          >
            Reset
          </button>
        </div>

        <p className="mt-4 text-sm font-medium text-slate-700">
          Kết quả: {items.length} yêu cầu
        </p>

        {loading ? (
          <p className="mt-4 text-sm text-slate-500">Đang tải...</p>
        ) : items.length === 0 ? (
          <div className="mt-8 text-center text-slate-500">
            <p>Không có yêu cầu nào.</p>
            <p className="mt-1 text-sm">Bạn chưa tạo yêu cầu dịch vụ Coursera nào.</p>
            <Link href="/coursera" className="mt-4 inline-block text-sm font-medium text-fuo-600 hover:underline">
              + Tạo yêu cầu đầu tiên
            </Link>
          </div>
        ) : (
          <div className="mt-4 overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-xs text-slate-500">
                  <th className="py-2 pr-4">Khóa học</th>
                  <th className="py-2 pr-4">Mã</th>
                  <th className="py-2 pr-4">Giá</th>
                  <th className="py-2 pr-4">Trạng thái</th>
                  <th className="py-2">Ngày tạo</th>
                </tr>
              </thead>
              <tbody>
                {items.map((row) => (
                  <tr key={row.id} className="border-b border-slate-100">
                    <td className="py-3 pr-4 font-medium text-slate-800">{row.catalogTitle}</td>
                    <td className="py-3 pr-4 text-fuo-700">{row.catalogCode}</td>
                    <td className="py-3 pr-4">{formatPoints(row.totalPoints)}</td>
                    <td className="py-3 pr-4">
                      <StatusBadge status={row.status} />
                    </td>
                    <td className="py-3 text-slate-600">
                      {new Date(row.createdAt).toLocaleString("vi-VN")}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <div className="mt-6 flex flex-wrap gap-3">
          <Link href="/coursera" className="btn-secondary">
            Về trang chủ Coursera
          </Link>
          <Link href="/coursera" className="btn-primary">
            + Tạo yêu cầu mới
          </Link>
        </div>
      </div>
    </div>
  );
}

function StatCard({ label, value }: { label: string; value: number }) {
  return (
    <div className="card p-3 text-center">
      <p className="text-[10px] font-semibold uppercase tracking-wide text-slate-500">{label}</p>
      <p className="mt-1 text-xl font-bold text-slate-900">{value}</p>
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  const label = REQUEST_STATUS_LABELS[status] ?? status;
  const colors: Record<string, string> = {
    pending: "bg-amber-100 text-amber-800",
    in_progress: "bg-sky-100 text-sky-800",
    completed: "bg-emerald-100 text-emerald-800",
    cancelled: "bg-slate-100 text-slate-600",
  };
  return (
    <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${colors[status] ?? "bg-slate-100"}`}>
      {label}
    </span>
  );
}
