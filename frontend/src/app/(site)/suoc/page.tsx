"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { PromoBanner } from "@/components/layout/PromoBanner";
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
  { value: "", label: "Mặc định" },
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
      className="card-interactive group flex flex-col overflow-hidden"
    >
      <div className="relative h-32 w-full overflow-hidden">
        {coverUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={coverUrl}
            alt=""
            className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
          />
        ) : (
          <div
            className="flex h-full items-center justify-center px-4 text-center font-display text-2xl text-white transition-transform duration-500 group-hover:scale-105"
            style={{ background: item.cardColor ?? "#6d28d9" }}
          >
            {item.code}
          </div>
        )}
        {item.featured && (
          <span className="absolute left-2 top-2 rounded-full bg-white/90 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-amber-600 shadow-sm">
            Nổi bật
          </span>
        )}
      </div>
      <div className="flex flex-1 flex-col gap-1 p-4">
        <span className="text-sm font-semibold text-ink-900">{item.code}</span>
        <p className="line-clamp-2 text-xs text-ink-500">{item.title}</p>
        <div className="mt-auto flex flex-wrap items-center gap-2 pt-2 text-[11px] text-ink-500">
          <span className="rounded-full bg-ink-100 px-2 py-0.5">{item.questionCount} câu</span>
          {item.duplicationRatePercent > 0 && (
            <span className="rounded-full bg-emerald-50 px-2 py-0.5 text-emerald-600">
              {item.duplicationRatePercent}% trùng lặp
            </span>
          )}
        </div>
        <p className="pt-2 text-sm font-bold text-fuo-700">{formatPoints(item.pricePoints)}</p>
      </div>
    </Link>
  );
}

function CardSkeleton() {
  return (
    <div className="card flex flex-col overflow-hidden">
      <div className="skeleton h-32 w-full" />
      <div className="space-y-2 p-4">
        <div className="skeleton h-4 w-16 rounded" />
        <div className="skeleton h-3 w-full rounded" />
        <div className="skeleton h-3 w-2/3 rounded" />
        <div className="skeleton h-4 w-20 rounded" />
      </div>
    </div>
  );
}

export default function SuocPage() {
  const { user } = useAuth();
  const [featured, setFeatured] = useState<SourceCatalogItem[]>([]);
  const [items, setItems] = useState<SourceCatalogItem[]>([]);
  const [search, setSearch] = useState("");
  const [sort, setSort] = useState("");
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
        sort: sort || undefined,
        page,
        size: PAGE_SIZE,
      });
      setItems(result.items);
      setTotalPages(result.totalPages);
      setTotalElements(result.totalElements);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được danh sách Suộc");
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
    if (!user) {
      setBalance(null);
      return;
    }
    getPointsBalance().then((r) => setBalance(r.balance)).catch(() => setBalance(null));
  }, [user]);

  const showFeatured = featured.length > 0 && !search.trim();

  return (
    <div className="space-y-6">
      <PromoBanner />

      {/* Hero */}
      <section className="relative overflow-hidden rounded-2xl border border-ink-200 bg-gradient-to-br from-ink-900 to-ink-700 p-6 text-ink-50 sm:p-8">
        <div className="relative z-10 flex flex-wrap items-end justify-between gap-4">
          <div className="max-w-xl space-y-2">
            <p className="eyebrow text-ink-300">Ngân hàng câu hỏi ôn thi</p>
            <h1 className="font-display text-3xl leading-tight sm:text-4xl">
              Suộc — Ôn thi đúng trọng tâm
            </h1>
            <p className="text-sm text-ink-200">
              Tài liệu ôn thi theo mã môn, luyện câu hỏi tương tác và thanh toán bằng FUO Point.
            </p>
          </div>
          <div className="flex items-center gap-3">
            {balance !== null && (
              <span className="rounded-full bg-ink-50/10 px-3.5 py-1.5 text-sm font-semibold text-ink-50 backdrop-blur">
                Số dư: {formatPoints(balance)}
              </span>
            )}
            <Link
              href="/suoc/my-purchases"
              className="inline-flex items-center gap-1 rounded-full bg-ink-50 px-4 py-2 text-sm font-medium text-ink-900 transition hover:bg-white"
            >
              Suộc của tôi →
            </Link>
          </div>
        </div>
      </section>

      {showFeatured && (
        <section className="space-y-3">
          <h2 className="eyebrow">Suộc nổi bật</h2>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {featured.map((item) => (
              <SourceCard key={item.id} item={item} />
            ))}
          </div>
        </section>
      )}

      {/* Sticky filter */}
      <div className="sticky top-16 z-30 -mx-4 border-y border-ink-200 bg-ink-50/85 px-4 py-3 backdrop-blur">
        <div className="flex flex-wrap items-end gap-3">
          <div className="min-w-[220px] flex-1">
            <label className="eyebrow" htmlFor="suoc-search">
              Tìm tài liệu
            </label>
            <input
              id="suoc-search"
              className="input-field mt-1"
              placeholder="VD: MLN111, CSI106"
              value={search}
              onChange={(e) => {
                setPage(0);
                setSearch(e.target.value);
              }}
            />
          </div>
          <div>
            <label className="eyebrow" htmlFor="suoc-sort">
              Sắp xếp
            </label>
            <select
              id="suoc-sort"
              className="input-field mt-1 min-w-[170px]"
              value={sort}
              onChange={(e) => {
                setPage(0);
                setSort(e.target.value);
              }}
            >
              {SORT_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>

      <section className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="eyebrow">Danh sách Suộc</h2>
          <span className="text-xs text-ink-500">{totalElements} tài liệu</span>
        </div>

        {error && (
          <p className="rounded-xl bg-red-50 px-3 py-2 text-sm text-red-800">{error}</p>
        )}

        {loading ? (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {Array.from({ length: 8 }).map((_, i) => (
              <CardSkeleton key={i} />
            ))}
          </div>
        ) : items.length === 0 ? (
          <div className="card flex flex-col items-center gap-2 py-12 text-center">
            <p className="text-sm font-medium text-ink-700">Không có tài liệu phù hợp</p>
            <p className="text-xs text-ink-500">Thử từ khóa khác hoặc xóa bộ lọc.</p>
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {items.map((item) => (
              <SourceCard key={item.id} item={item} />
            ))}
          </div>
        )}

        {totalPages > 1 && (
          <div className="flex items-center justify-center gap-3 pt-2">
            <button
              type="button"
              className="btn-secondary"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              ← Trước
            </button>
            <span className="text-sm text-ink-600">
              Trang {page + 1} / {totalPages}
            </span>
            <button
              type="button"
              className="btn-secondary"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              Sau →
            </button>
          </div>
        )}
      </section>
    </div>
  );
}