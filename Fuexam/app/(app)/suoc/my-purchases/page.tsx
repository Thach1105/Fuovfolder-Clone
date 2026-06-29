"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useAuth } from "@/lib/auth/AuthProvider";
import { AuthGuard } from "@/components/shared/auth-guard";
import {
  type SourcePurchase,
  type SourcePurchaseStats,
  SOURCE_PURCHASE_STATUS_LABELS,
  formatPoints,
  getMyPurchaseStats,
  listMyPurchases,
} from "@/lib/api/source";

function StatCard({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-2xl border border-foreground/10 bg-background/60 p-5 text-center backdrop-blur">
      <p className="font-display text-4xl">{value}</p>
      <p className="mt-1 app-eyebrow">{label}</p>
    </div>
  );
}

const STATUS_CLASS: Record<string, string> = {
  active: "bg-emerald-500/15 text-emerald-700 hover:bg-emerald-500/15",
  expired: "bg-foreground/10 text-muted-foreground hover:bg-foreground/10",
  refunded: "bg-amber-500/15 text-amber-700 hover:bg-amber-500/15",
  cancelled: "bg-foreground/10 text-muted-foreground hover:bg-foreground/10",
};

export default function MySuocPage() {
  const { user } = useAuth();
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

  useEffect(() => { if (user) load(); }, [user, load]);

  return (
    <AuthGuard>
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="space-y-1">
          <p className="app-eyebrow">Bộ sưu tập của bạn</p>
          <h1 className="font-display text-4xl">Source đã mua</h1>
        </div>
        <Button asChild className="rounded-full bg-foreground text-background hover:bg-foreground/90">
          <Link href="/suoc">+ Mua thêm Source</Link>
        </Button>
      </div>

      {stats && (
        <div className="grid grid-cols-3 gap-3">
          <StatCard label="Tổng đã mua" value={stats.total} />
          <StatCard label="Còn hạn" value={stats.active} />
          <StatCard label="Hết hạn" value={stats.expired} />
        </div>
      )}

      <div className="overflow-hidden rounded-2xl border border-foreground/10 bg-background/60 backdrop-blur">
        {loading ? (
          <div className="space-y-2 p-5">
            {Array.from({ length: 4 }).map((_, i) => <div key={i} className="app-skeleton h-12 w-full rounded-lg" />)}
          </div>
        ) : items.length === 0 ? (
          <div className="flex flex-col items-center gap-3 py-14 text-center">
            <p className="font-medium">Bạn chưa mua tài liệu nào</p>
            <p className="text-sm text-muted-foreground">Khám phá ngân hàng câu hỏi ôn thi ngay.</p>
            <Button asChild className="mt-2 rounded-full bg-foreground text-background hover:bg-foreground/90">
              <Link href="/suoc">Khám phá tài liệu</Link>
            </Button>
          </div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Tài liệu</TableHead>
                <TableHead>Giá</TableHead>
                <TableHead>Trạng thái</TableHead>
                <TableHead>Hết hạn</TableHead>
                <TableHead>Ngày mua</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((row) => (
                <TableRow key={row.id}>
                  <TableCell>
                    <Link href={`/suoc/${row.code}`} className="font-medium hover:underline">{row.code}</Link>
                    <p className="max-w-[220px] truncate text-xs text-muted-foreground">{row.title}</p>
                  </TableCell>
                  <TableCell className="font-mono">{formatPoints(row.unitPricePoints)}</TableCell>
                  <TableCell>
                    <Badge className={STATUS_CLASS[row.status] ?? "bg-foreground/10"}>
                      {SOURCE_PURCHASE_STATUS_LABELS[row.status] ?? row.status}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-muted-foreground">{new Date(row.endsAt).toLocaleDateString("vi-VN")}</TableCell>
                  <TableCell className="text-muted-foreground">{new Date(row.createdAt).toLocaleDateString("vi-VN")}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </div>
    </div>
    </AuthGuard>
  );
}