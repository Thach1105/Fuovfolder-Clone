"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Badge } from "@/components/ui/badge";
import { useAuth } from "@/lib/auth/AuthProvider";
import { ApiError } from "@/lib/api/client";
import { getPointsBalance } from "@/lib/api/points";
import {
  type SourceCatalogItem,
  browseSourceCatalog,
  formatPoints,
  getFeaturedSource,
} from "@/lib/api/source";
import { resolveMediaUrl } from "@/lib/api/media";

const SORT_OPTIONS = [
  { value: "default", label: "Mặc định" },
  { value: "newest", label: "Mới nhất" },
  { value: "price_asc", label: "Giá thấp → cao" },
  { value: "price_desc", label: "Giá cao → thấp" },
  { value: "popular", label: "Phổ biến" },
];

const PAGE_SIZE = 24;

function SourceCard({ item }: { item: SourceCatalogItem }) {
  const coverUrl = resolveMediaUrl(item.coverImageUrl);
  return (
    <Link
      href={`/suoc/${item.code}`}
      className="group flex flex-col overflow-hidden rounded-2xl border border-foreground/10 bg-background/60 backdrop-blur transition-all duration-300 hover:-translate-y-1 hover:border-foreground/25 hover:shadow-lg"
    >
      <div className="relative h-32 w-full overflow-hidden">
        {coverUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={coverUrl} alt="" className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105" />
        ) : (
          <div
            className="flex h-full items-center justify-center font-display text-2xl text-white transition-transform duration-500 group-hover:scale-105"
            style={{ background: item.cardColor ?? "#1a1712" }}
          >
            {item.code}
          </div>
        )}
        {item.featured && (
          <Badge className="absolute left-2 top-2 bg-background/90 text-foreground hover:bg-background/90">Nổi bật</Badge>
        )}
      </div>
      <div className="flex flex-1 flex-col gap-1 p-4">
        <span className="font-medium">{item.code}</span>
        <p className="line-clamp-2 text-xs text-muted-foreground">{item.title}</p>
        <div className="mt-auto flex flex-wrap items-center gap-1.5 pt-2 font-mono text-[11px] text-muted-foreground">
          <span className="rounded-full bg-foreground/5 px-2 py-0.5">{item.questionCount} câu</span>
          {item.duplicationRatePercent > 0 && (
            <span className="rounded-full bg-emerald-500/10 px-2 py-0.5 text-emerald-600">{item.duplicationRatePercent}% trùng</span>
          )}
        </div>
        <p className="pt-2 font-display text-lg">{formatPoints(item.pricePoints)}</p>
      </div>
    </Link>
  );
}

function CardSkeleton() {
  return (
    <div className="flex flex-col overflow-hidden rounded-2xl border border-foreground/10">
      <div className="app-skeleton h-32 w-full" />
      <div className="space-y-2 p-4">
        <div className="app-skeleton h-4 w-16 rounded" />
        <div className="app-skeleton h-3 w-full rounded" />
        <div className="app-skeleton h-4 w-20 rounded" />
      </div>
    </div>
  );
}

export default function SuocPage() {
  const { user } = useAuth();
  const [featured, setFeatured] = useState<SourceCatalogItem[]>([]);
  const [items, setItems] = useState<SourceCatalogItem[]>([]);
  const [search, setSearch] = useState("");
  const [sort, setSort] = useState("default");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [balance, setBalance] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadCatalog = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await browseSourceCatalog({
        q: search.trim() || undefined,
        sort: sort !== "default" ? sort : undefined,
        page,
        size: PAGE_SIZE,
      });
      setItems(result.items);
      setTotalPages(result.totalPages);
      setTotalElements(result.totalElements);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được danh sách Source");
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, [search, sort, page]);

  useEffect(() => {
    getFeaturedSource(8).then(setFeatured).catch(() => setFeatured([]));
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => loadCatalog(), 250);
    return () => clearTimeout(timer);
  }, [loadCatalog]);

  useEffect(() => {
    if (!user) { setBalance(null); return; }
    getPointsBalance().then((r) => setBalance(r.balance)).catch(() => setBalance(null));
  }, [user]);

  const showFeatured = featured.length > 0 && !search.trim();

  return (
    <div className="space-y-8">
      <section className="space-y-4">
        <p className="app-eyebrow">Ngân hàng câu hỏi ôn thi</p>
        <h1 className="font-display text-[clamp(2.5rem,6vw,4.5rem)] leading-[0.95]">
          Source — ôn thi <br className="hidden sm:block" />đúng trọng tâm
        </h1>
        <div className="flex flex-wrap items-center gap-4">
          <p className="max-w-xl text-lg text-muted-foreground">
            Tài liệu ôn thi theo mã môn, luyện câu hỏi tương tác, thanh toán bằng Fuexam Point.
          </p>
          <div className="ml-auto flex items-center gap-3">
            {balance !== null && (
              <span className="rounded-full border border-foreground/15 px-3.5 py-1.5 font-mono text-sm">
                {formatPoints(balance)}
              </span>
            )}
            <Link href="/suoc/my-purchases" className="text-sm font-medium hover:underline">
              Source của tôi →
            </Link>
          </div>
        </div>
      </section>

      {showFeatured && (
        <section className="space-y-3">
          <h2 className="app-eyebrow">Nổi bật</h2>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {featured.map((item) => <SourceCard key={item.id} item={item} />)}
          </div>
        </section>
      )}

      <div className="sticky top-[88px] z-30 flex flex-wrap items-center gap-3 rounded-2xl border border-foreground/10 bg-background/80 p-3 backdrop-blur-xl">
        <Input
          className="h-10 min-w-[220px] flex-1"
          placeholder="Tìm theo mã môn — VD: MLN111, CSI106"
          value={search}
          onChange={(e) => { setPage(0); setSearch(e.target.value); }}
        />
        <Select value={sort} onValueChange={(v) => { setPage(0); setSort(v); }}>
          <SelectTrigger className="h-10 w-[180px]"><SelectValue /></SelectTrigger>
          <SelectContent>
            {SORT_OPTIONS.map((o) => <SelectItem key={o.value} value={o.value}>{o.label}</SelectItem>)}
          </SelectContent>
        </Select>
      </div>

      <section className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="app-eyebrow">Danh sách Source</h2>
          <span className="font-mono text-xs text-muted-foreground">{totalElements} tài liệu</span>
        </div>

        {error && <p className="rounded-xl bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}

        {loading ? (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {Array.from({ length: 8 }).map((_, i) => <CardSkeleton key={i} />)}
          </div>
        ) : items.length === 0 ? (
          <div className="rounded-2xl border border-foreground/10 py-14 text-center">
            <p className="font-medium">Không có tài liệu phù hợp</p>
            <p className="mt-1 text-sm text-muted-foreground">Thử từ khóa khác hoặc xóa bộ lọc.</p>
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {items.map((item) => <SourceCard key={item.id} item={item} />)}
          </div>
        )}

        {totalPages > 1 && (
          <div className="flex items-center justify-center gap-4 pt-2 text-sm">
            <button className="rounded-full border border-foreground/15 px-4 py-1.5 disabled:opacity-40" disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>← Trước</button>
            <span className="font-mono text-muted-foreground">{page + 1} / {totalPages}</span>
            <button className="rounded-full border border-foreground/15 px-4 py-1.5 disabled:opacity-40" disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>Sau →</button>
          </div>
        )}
      </section>
    </div>
  );
}