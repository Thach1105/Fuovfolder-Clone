"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { useAuth } from "@/lib/auth/AuthProvider";
import {
  type SourcePurchase,
  type SourcePurchaseStats,
  SOURCE_PURCHASE_STATUS_LABELS,
  formatPoints,
  getMyPurchaseStats,
  listMyPurchases,
} from "@/lib/api/source";

function StatCard({ label, value, tone }: { label: string; value: number; tone: string }) {
  return (
    <div className="card p-4 text-center">
      <p className={`text-2xl font-bold ${tone}`}>{value}</p>
      <p className="mt-1 text-[11px] font-semibold uppercase tracking-wide text-slate-500">{label}</p>
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  const colors: Record<string, string> = {
    active: "bg-emerald-100 text-emerald-800",
    expired: "bg-slate-100 text-slate-600",
    refunded: "bg-amber-100 text-amber-800",
    cancelled: "bg-slate-100 text-slate-500",
  };
  return (
    <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${colors[status] ?? "bg-slate-100"}`}>
      {SOURCE_PURCHASE_STATUS_LABELS[status] ?? status}
    </span>
  );
}

export default function MySuocPage() {
  const router = useRouter();
  const { user, loading: authLoading } = useAuth();
  const [stats, setStats] = useState<SourcePurchaseStats | null>(null);
  const [items, setItems] = useState<SourcePurchase[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [s, page] = await Promise.all([getMyPurchaseStats(), listMyPurchases("all", 0, 50)]);
      setStats(s);
      setItems(page.items);
    } finally {
      setLoading(false);
    }
  }, []);

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
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Suộc đã mua</h1>
          <p className="text-sm text-slate-600">Quản lý tài liệu ôn thi bạn đã sở hữu.</p>
        </div>
        <Link href="/suoc" className="btn-primary">
          + Mua thêm Suộc
        </Link>
      </div>

      {stats && (
        <div className="grid grid-cols-3 gap-3">
          <StatCard label="Tổng tài liệu đã mua" value={stats.total} tone="text-slate-900" />
          <StatCard label="Tài liệu còn hạn" value={stats.active} tone="text-emerald-600" />
          <StatCard label="Tài liệu hết hạn" value={stats.expired} tone="text-slate-500" />
        </div>
      )}

      <div className="card p-4">
        {loading ? (
          <p className="text-sm text-slate-500">Đang tải...</p>
        ) : items.length === 0 ? (
          <div className="py-8 text-center text-slate-500">
            <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
              Bạn chưa mua tài liệu nào.
            </p>
            <Link href="/suoc" className="btn-primary mt-4 inline-flex">
              Khám phá tài liệu
            </Link>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-xs text-slate-500">
                  <th className="py-2 pr-4">Tài liệu</th>
                  <th className="py-2 pr-4">Giá</th>
                  <th className="py-2 pr-4">Trạng thái</th>
                  <th className="py-2 pr-4">Hết hạn</th>
                  <th className="py-2">Ngày mua</th>
                </tr>
              </thead>
              <tbody>
                {items.map((row) => (
                  <tr key={row.id} className="border-b border-slate-100">
                    <td className="py-3 pr-4">
                      <Link href={`/suoc/${row.code}`} className="font-medium text-fuo-700 hover:underline">
                        {row.code}
                      </Link>
                      <p className="max-w-[220px] truncate text-xs text-slate-500">{row.title}</p>
                    </td>
                    <td className="py-3 pr-4">{formatPoints(row.unitPricePoints)}</td>
                    <td className="py-3 pr-4">
                      <StatusBadge status={row.status} />
                    </td>
                    <td className="py-3 pr-4 text-slate-600">
                      {new Date(row.endsAt).toLocaleDateString("vi-VN")}
                    </td>
                    <td className="py-3 text-slate-600">
                      {new Date(row.createdAt).toLocaleDateString("vi-VN")}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
