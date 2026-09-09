"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { ChevronDown, Folder, FileStack } from "lucide-react";
import { ErrorBanner } from "@/components/ui/error-banner";
import { ApiError } from "@/lib/api/client";
import { formatDateTime } from "@/lib/format-datetime";
import { type PublicSubjectCard, type PublicSubjectLatestPaper, listExamSubjects } from "@/lib/api/exam";

type TermGroup = {
  term: number | null;
  label: string;
  subjects: PublicSubjectCard[];
  feTotal: number;
  peTotal: number;
  latestPaper: (PublicSubjectLatestPaper & { subjectCode: string }) | null;
};

/** Cycles through a fixed palette so each "Kỳ" badge gets a distinct, stable color. */
const TERM_BADGE_COLORS = [
  "bg-amber-500 text-amber-950",
  "bg-sky-500 text-sky-950",
  "bg-rose-500 text-rose-950",
  "bg-emerald-500 text-emerald-950",
  "bg-violet-500 text-violet-950",
  "bg-cyan-500 text-cyan-950",
  "bg-orange-500 text-orange-950",
  "bg-lime-500 text-lime-950",
  "bg-fuchsia-500 text-fuchsia-950",
  "bg-teal-500 text-teal-950",
];

function termBadgeColor(term: number): string {
  return TERM_BADGE_COLORS[term % TERM_BADGE_COLORS.length];
}

function groupSubjects(items: PublicSubjectCard[]): TermGroup[] {
  const byTerm = new Map<number | null, PublicSubjectCard[]>();
  for (const item of items) {
    const key = item.curriculumTerm ?? null;
    const bucket = byTerm.get(key);
    if (bucket) {
      bucket.push(item);
    } else {
      byTerm.set(key, [item]);
    }
  }

  const terms = [...byTerm.keys()].sort((a, b) => {
    if (a === null) return -1;
    if (b === null) return 1;
    return a - b;
  });

  return terms.map((term) => {
    const subjects = byTerm.get(term)!;
    const feTotal = subjects.reduce((sum, s) => sum + s.fePaperCount, 0);
    const peTotal = subjects.reduce((sum, s) => sum + s.pePaperCount, 0);
    const latestPaper = subjects.reduce<(PublicSubjectLatestPaper & { subjectCode: string }) | null>(
      (latest, s) => {
        if (!s.latestPaper) return latest;
        if (!latest || new Date(s.latestPaper.createdAt).getTime() > new Date(latest.createdAt).getTime()) {
          return { ...s.latestPaper, subjectCode: s.code };
        }
        return latest;
      },
      null,
    );
    return {
      term,
      label: term === null ? "Tổng hợp - Chưa rõ kỳ" : `Kỳ ${term}`,
      subjects,
      feTotal,
      peTotal,
      latestPaper,
    };
  });
}

function CardSkeleton() {
  return (
    <div className="flex flex-col gap-3 px-4 py-4">
      <div className="app-skeleton h-8 w-40 rounded" />
      <div className="app-skeleton h-4 w-full rounded" />
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

  const groups = useMemo(() => groupSubjects(filtered), [filtered]);

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
        <ErrorBanner message={error} />

        <div className="rounded-2xl border border-foreground/10">
          {loading ? (
            <div className="divide-y divide-foreground/10">
              {Array.from({ length: 4 }).map((_, i) => <CardSkeleton key={i} />)}
            </div>
          ) : (
            <Collapsible defaultOpen>
              <div className="flex items-center justify-between border-b border-foreground/10 px-4 py-3">
                <div>
                  <p className="app-eyebrow">Danh sách môn</p>
                  <p className="font-mono text-xs text-muted-foreground">
                    {filtered.length} môn · nhóm theo kỳ học
                  </p>
                </div>
                <CollapsibleTrigger asChild>
                  <button
                    type="button"
                    aria-label="Thu gọn / mở rộng danh sách"
                    className="rounded-full p-2 text-muted-foreground hover:bg-foreground/5 hover:text-foreground"
                  >
                    <ChevronDown className="h-4 w-4" />
                  </button>
                </CollapsibleTrigger>
              </div>
              <CollapsibleContent>
                {filtered.length === 0 ? (
                  <div className="py-14 text-center">
                    <p className="font-medium">Không có môn phù hợp</p>
                    <p className="mt-1 text-sm text-muted-foreground">Thử từ khóa khác hoặc xóa bộ lọc.</p>
                  </div>
                ) : (
                  <div className="divide-y divide-foreground/10">
                    {groups.map((group) => (
                      <div
                        key={group.term ?? "none"}
                        className="flex flex-col gap-3 px-4 py-4 sm:flex-row sm:items-start"
                      >
                        <div className="flex shrink-0 items-center gap-2 sm:w-40">
                          {group.term === null ? (
                            <span className="flex h-8 w-8 items-center justify-center rounded-md bg-foreground/5 text-muted-foreground">
                              <FileStack className="h-4 w-4" />
                            </span>
                          ) : (
                            <span
                              className={`flex h-8 w-8 items-center justify-center rounded-full text-sm font-bold ${termBadgeColor(group.term)}`}
                            >
                              {group.term}
                            </span>
                          )}
                          <span className="text-sm font-medium">{group.label}</span>
                        </div>

                        <div className="flex flex-1 flex-wrap gap-x-3 gap-y-1.5">
                          {group.subjects.map((item) => (
                            <Link
                              key={item.id}
                              href={`/exam/${item.code}`}
                              className="inline-flex items-center gap-1 font-mono text-sm text-sky-500 hover:underline"
                              title={item.title}
                            >
                              <Folder className="h-3.5 w-3.5 text-muted-foreground" />
                              {item.code}
                            </Link>
                          ))}
                        </div>

                        <div className="flex shrink-0 gap-6 text-right sm:w-28">
                          <div>
                            <p className="text-sm font-semibold">{group.feTotal}</p>
                            <p className="text-xs text-muted-foreground">Đề FE</p>
                          </div>
                          <div>
                            <p className="text-sm font-semibold">{group.peTotal}</p>
                            <p className="text-xs text-muted-foreground">Đề PE</p>
                          </div>
                        </div>

                        <div className="shrink-0 sm:w-56 sm:text-right">
                          {group.latestPaper ? (
                            <div className="flex flex-col items-start gap-1 sm:items-end">
                              <Badge
                                className={
                                  group.latestPaper.paperType === "FE"
                                    ? "bg-amber-500 text-amber-950 hover:bg-amber-500"
                                    : "bg-rose-500 text-rose-950 hover:bg-rose-500"
                                }
                              >
                                {group.latestPaper.paperType === "FE" ? "Đề Thi FE" : "Đề Thi PE"}
                              </Badge>
                              <p className="max-w-[200px] truncate text-xs" title={group.latestPaper.examCode}>
                                {group.latestPaper.subjectCode} · {group.latestPaper.examCode}
                              </p>
                              <p className="text-xs text-muted-foreground">
                                {formatDateTime(group.latestPaper.createdAt)}
                              </p>
                            </div>
                          ) : (
                            <p className="text-xs text-muted-foreground">Chưa có đề nào</p>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </CollapsibleContent>
            </Collapsible>
          )}
        </div>
      </section>
    </div>
  );
}
