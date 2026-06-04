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

const SORT_OPTIONS = [
  { value: "", label: "Mặc định" },
  { value: "newest", label: "Mới nhất" },
  { value: "price_asc", label: "Giá thấp → cao" },
  { value: "price_desc", label: "Giá cao → thấp" },
  { value: "popular", label: "Phổ biến" },
];

const PAGE_SIZE = 24;

function SourceCard({ item }: { item: SourceCatalogItem }) {
  return (
    <Link
      href={`/suoc/${item.code}`}
      className="card flex flex-col overflow-hidden transition hover:shadow-md"
    >
      <div
        className="flex h-32 items-center justify-center px-4 text-center text-xl font-bold text-white"
        style={{ background: item.cardColor ?? "#6d28d9" }}
      >
        {item.code}
      </div>
      <div className="space-y-1 p-4">
        <div className="flex items-center gap-2">
          <span className="text-sm font-semibold text-slate-800">{item.code}</span>
          {item.featured && (
            <span className="text-[10px] font-semibold uppercase text-amber-600">Nổi bật</span>
          )}
        </div>
        <p className="line-clamp-2 text-xs text-slate-500">{item.title}</p>
        <div className="flex flex-wrap gap-2 pt-1 text-[11px] text-slate-500">
          <span>{item.questionCount} câu</span>
          {item.duplicationRatePercent > 0 && (
            <span className="text-emerald-600">{item.duplicationRatePercent}% trùng lặp</span>
          )}
        </div>
        <p className="pt-1 text-sm font-bold text-fuo-700">{formatPoints(item.pricePoints)}</p>
      </div>
    </Link>
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

  return (
    <div className="space-y-5">
      <PromoBanner />

      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Suộc — Tài liệu ôn thi</h1>
          <p className="text-sm text-slate-600">
            Ngân hàng câu hỏi ôn thi theo mã môn, thanh toán bằng FUO Point.
          </p>
        </div>
        <div className="flex items-center gap-3">
          {balance !== null && (
            <span className="rounded-lg bg-fuo-50 px-3 py-1.5 text-sm font-semibold text-fuo-700">
              {formatPoints(balance)}
            </span>
          )}
          <Link href="/suoc/my-purchases" className="text-sm font-medium text-fuo-600 hover:underline">
            Suộc của tôi →
          </Link>
        </div>
      </div>

      {featured.length > 0 && !search.trim() && (
        <section className="space-y-2">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">
            Suộc nổi bật
          </h2>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {featured.map((item) => (
              <SourceCard key={item.id} item={item} />
            ))}
          </div>
        </section>
      )}

      <div className="card flex flex-wrap items-end gap-3 p-4">
        <div className="min-w-[200px] flex-1">
          <label className="text-xs font-medium text-slate-500" htmlFor="suoc-search">
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
          <label className="text-xs font-medium text-slate-500" htmlFor="suoc-sort">
            Sắp xếp
          </label>
          <select
            id="suoc-sort"
            className="input-field mt-1 min-w-[160px]"
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

      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">
            Danh sách Suộc
          </h2>
          <span className="text-xs text-slate-500">{totalElements} tài liệu</span>
        </div>

        {error && (
          <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-800">{error}</p>
        )}

        {loading ? (
          <p className="text-sm text-slate-500">Đang tải...</p>
        ) : items.length === 0 ? (
          <p className="text-sm text-slate-500">Không có tài liệu phù hợp.</p>
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
            <span className="text-sm text-slate-600">
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
