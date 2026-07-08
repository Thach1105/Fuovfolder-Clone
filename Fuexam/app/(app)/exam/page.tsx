"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Input } from "@/components/ui/input";
import { ErrorBanner } from "@/components/ui/error-banner";
import { ApiError } from "@/lib/api/client";
import { resolveMediaUrl } from "@/lib/api/media";
import { type PublicSubjectCard, listExamSubjects } from "@/lib/api/exam";

function SubjectCard({ item }: { item: PublicSubjectCard }) {
  const coverUrl = resolveMediaUrl(item.coverImageUrl);
  return (
    <Link
      href={`/exam/${item.code}`}
      className="group flex flex-col overflow-hidden rounded-2xl border border-foreground/10 bg-background/60 backdrop-blur transition-all duration-300 hover:-translate-y-1 hover:border-foreground/25 hover:shadow-lg"
    >
      <div className="relative h-32 w-full overflow-hidden">
        {coverUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={coverUrl} alt="" loading="lazy" className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105" />
        ) : (
          <div
            className="flex h-full items-center justify-center font-display text-2xl text-white transition-transform duration-500 group-hover:scale-105"
            style={{ background: item.cardColor ?? "#1a1712" }}
          >
            {item.code}
          </div>
        )}
      </div>
      <div className="flex flex-1 flex-col gap-1 p-4">
        <span className="font-medium">{item.code}</span>
        <p className="line-clamp-2 text-xs text-muted-foreground">{item.title}</p>
        <div className="mt-auto flex flex-wrap items-center gap-1.5 pt-2 font-mono text-[11px] text-muted-foreground">
          <span className="rounded-full bg-foreground/5 px-2 py-0.5">{item.fePaperCount} đề FE</span>
          <span className="rounded-full bg-foreground/5 px-2 py-0.5">{item.pePaperCount} đề PE</span>
        </div>
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

export default function ExamPage() {
  const [items, setItems] = useState<PublicSubjectCard[]>([]);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setItems(await listExamSubjects());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được danh sách môn thi");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return items;
    return items.filter(
      (item) =>
        item.code.toLowerCase().includes(q) || item.title.toLowerCase().includes(q),
    );
  }, [items, search]);

  return (
    <div className="space-y-8">
      <section className="space-y-4">
        <p className="app-eyebrow">Ngân hàng đề thi</p>
        <h1 className="font-display text-[clamp(2.5rem,6vw,4.5rem)] leading-[0.95]">
          Đề thi FE &amp; PE <br className="hidden sm:block" />theo mã môn
        </h1>
        <p className="max-w-xl text-lg text-muted-foreground">
          Luyện câu hỏi trắc nghiệm FE, tải đề thực hành PE và trao đổi cùng cộng đồng. Mở khóa toàn
          bộ với gói membership.
        </p>
      </section>

      <div className="sticky top-[88px] z-30 flex flex-wrap items-center gap-3 rounded-2xl border border-foreground/10 bg-background/80 p-3 backdrop-blur-xl">
        <Input
          className="h-10 min-w-[220px] flex-1"
          placeholder="Tìm theo mã môn — VD: PRF192, CSD201"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      <section className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="app-eyebrow">Danh sách môn</h2>
          <span className="font-mono text-xs text-muted-foreground">{filtered.length} môn</span>
        </div>

        <ErrorBanner message={error} />

        {loading ? (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {Array.from({ length: 8 }).map((_, i) => <CardSkeleton key={i} />)}
          </div>
        ) : filtered.length === 0 ? (
          <div className="rounded-2xl border border-foreground/10 py-14 text-center">
            <p className="font-medium">Không có môn phù hợp</p>
            <p className="mt-1 text-sm text-muted-foreground">Thử từ khóa khác hoặc xóa bộ lọc.</p>
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {filtered.map((item) => <SubjectCard key={item.id} item={item} />)}
          </div>
        )}
      </section>
    </div>
  );
}
