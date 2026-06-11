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
      <p className={`font-display text-3xl ${tone}`}>{value}</p>
      <p className="mt-1 text-[11px] font-semibold uppercase tracking-wide text-ink-500">{label}</p>
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  const colors: Record<string, string> = {
    active: "bg-emerald-100 text-emerald-800",
    expired: "bg-ink-100 text-ink-600",
    refunded: "bg-amber-100 text-amber-800",
    cancelled: "bg-ink-100 text-ink-500",
  };
  return (
    <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${colors[status] ?? "bg-ink-100"}`}>
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
    return <p className="text-sm text-ink-500">Đang tải...</p>;
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="space-y-1">
          <p className="eyebrow">Bộ sưu tập của bạn</p>
          <h1 className="font-display text-3xl text-ink-900">Suộc đã mua</h1>
          <p className="text-sm text-ink-600">Quản lý tài liệu ôn thi bạn đã sở hữu.</p>
        </div>
        <Link href="/suoc" className="btn-accent">
          + Mua thêm Suộc
        </Link>
      </div>

      {stats && (
        <div className="grid grid-cols-3 gap-3">
          <StatCard label="Tổng đã mua" value={stats.total} tone="text-ink-900" />
          <StatCard label="Còn hạn" value={stats.active} tone="text-emerald-600" />
          <StatCard label="Hết hạn" value={stats.expired} tone="text-ink-500" />
        </div>
      )}

      <div className="card overflow-hidden">
        {loading ? (
          <div className="space-y-2 p-5">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="skeleton h-12 w-full rounded-lg" />
            ))}
          </div>
        ) : items.length === 0 ? (
          <div className="flex flex-col items-center gap-3 py-14 text-center">
            <p className="text-sm font-medium text-ink-700">Bạn chưa mua tài liệu nào</p>
            <p className="text-xs text-ink-500">Khám phá ngân hàng câu hỏi ôn thi ngay.</p>
            <Link href="/suoc" className="btn-accent mt-2">
              Khám phá tài liệu
            </Link>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-ink-200 text-xs uppercase tracking-wide text-ink-500">
                  <th className="px-5 py-3">Tài liệu</th>
                  <th className="px-5 py-3">Giá</th>
                  <th className="px-5 py-3">Trạng thái</th>
                  <th className="px-5 py-3">Hết hạn</th>
                  <th className="px-5 py-3">Ngày mua</th>
                </tr>
              </thead>
              <tbody>
                {items.map((row) => (
                  <tr key={row.id} className="border-b border-ink-100 transition hover:bg-ink-50">
                    <td className="px-5 py-3">
                      <Link href={`/suoc/${row.code}`} className="font-medium text-fuo-700 hover:underline">
                        {row.code}
                      </Link>
                      <p className="max-w-[220px] truncate text-xs text-ink-500">{row.title}</p>
                    </td>
                    <td className="px-5 py-3 text-ink-700">{formatPoints(row.unitPricePoints)}</td>
                    <td className="px-5 py-3">
                      <StatusBadge status={row.status} />
                    </td>
                    <td className="px-5 py-3 text-ink-600">
                      {new Date(row.endsAt).toLocaleDateString("vi-VN")}
                    </td>
                    <td className="px-5 py-3 text-ink-600">
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