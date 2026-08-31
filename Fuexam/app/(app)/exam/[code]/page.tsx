"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ArrowLeft, Calendar, ChevronDown, ChevronUp, Download, Eye, FileText, Lock, MessageSquare } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ErrorBanner } from "@/components/ui/error-banner";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useAuth } from "@/lib/auth/AuthProvider";
import { ApiError } from "@/lib/api/client";
import { cn } from "@/lib/utils";
import { resolveMediaUrl } from "@/lib/api/media";
import { ExamCommentThread } from "@/components/exam/exam-comment-thread";
import { Lightbox } from "@/components/exam/Lightbox";
import { ImageWithWatermark } from "@/components/shared/image-with-watermark";
import {
  type ExamPaperType,
  type PublicFeQuestion,
  type PublicFeQuestionList,
  type PublicImageItem,
  type PublicPaperDetail,
  type PublicPaperSummary,
  type PublicSubjectCard,
  type PublicSubjectDetail,
  getExamPaper,
  getExamSubject,
  incrementFeQuestionView,
  listFeQuestions,
  paperResourceDownloadUrl,
} from "@/lib/api/exam";

type TypeFilter = "ALL" | ExamPaperType;

function formatBytes(bytes: number): string {
  if (!bytes) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  const i = Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)));
  return `${(bytes / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1)} ${units[i]}`;
}

function paperTypeBadgeClass(type: ExamPaperType): string {
  return type === "FE"
    ? "bg-sky-500/15 text-sky-700 hover:bg-sky-500/15"
    : "bg-violet-500/15 text-violet-700 hover:bg-violet-500/15";
}

function MembershipUpsell({ title, description }: { title: string; description: string }) {
  return (
    <div className="rounded-2xl border border-foreground/10 bg-gradient-to-br from-amber-500/10 to-background p-6 text-center backdrop-blur">
      <div className="mx-auto flex h-11 w-11 items-center justify-center rounded-full bg-foreground/5">
        <Lock className="h-5 w-5 text-foreground/70" />
      </div>
      <h3 className="mt-3 font-display text-lg">{title}</h3>
      <p className="mx-auto mt-1 max-w-md text-sm text-muted-foreground">{description}</p>
      <Button asChild className="mt-4 rounded-full bg-foreground text-background hover:bg-foreground/90">
        <Link href="/membership">Mua membership</Link>
      </Button>
    </div>
  );
}

function PaperCard({
  paper,
  locked,
  onOpen,
}: {
  paper: PublicPaperSummary;
  locked: boolean;
  onOpen: (paper: PublicPaperSummary) => void;
}) {
  return (
    <button
      type="button"
      onClick={() => onOpen(paper)}
      className="group flex w-full flex-col gap-2 rounded-2xl border border-foreground/10 bg-background/70 p-4 text-left backdrop-blur transition hover:-translate-y-0.5 hover:border-foreground/25 hover:shadow-md"
    >
      <div className="flex flex-wrap items-center gap-1.5">
        <Badge className={paperTypeBadgeClass(paper.type)}>{paper.type}</Badge>
        <Badge variant="secondary">{paper.term}</Badge>
        {paper.retakeLabel && <Badge variant="outline">{paper.retakeLabel}</Badge>}
        {locked && <Lock className="ml-auto h-4 w-4 text-foreground/40" />}
      </div>
      <p className="font-medium leading-snug">{paper.title}</p>
      <div className="mt-auto flex flex-wrap gap-x-3 gap-y-1 pt-1 font-mono text-[11px] text-muted-foreground">
        <span>{paper.imageCount} ảnh</span>
        <span>{paper.resourceCount} tài nguyên</span>
      </div>
    </button>
  );
}

/** Paper images open in a plain lightbox: the backend has no per-image comment thread. */
function PaperImages({ urls }: { urls: string[] }) {
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null);
  if (urls.length === 0) return null;
  return (
    <>
      <div className={urls.length > 1 ? "grid grid-cols-2 gap-2 sm:grid-cols-3" : ""}>
        {urls.map((url, idx) => (
          <button
            key={`${url}-${idx}`}
            type="button"
            onClick={() => setLightboxIndex(idx)}
            className="group relative overflow-hidden rounded-xl border border-foreground/10"
            aria-label="Xem ảnh lớn"
          >
            <ImageWithWatermark
              src={url}
              alt=""
              loading="lazy"
              className="max-h-72 w-full object-cover transition-transform group-hover:scale-[1.02]"
            />
            <span className="absolute inset-0 bg-black/0 transition-colors group-hover:bg-black/10" />
          </button>
        ))}
      </div>
      {lightboxIndex !== null && (
        <Lightbox
          images={urls}
          currentIndex={lightboxIndex}
          onClose={() => setLightboxIndex(null)}
          onNavigate={setLightboxIndex}
        />
      )}
    </>
  );
}

function FePostImages({ images, questionId }: { images: PublicImageItem[]; questionId: string }) {
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null);

  const resolved = useMemo(
    () =>
      images.map((img) => ({
        ...img,
        resolvedUrl: resolveMediaUrl(img.url),
      })),
    [images],
  );

  // Map each full-image's PublicImageItem.index to its position within fullUrls
  // (the Lightbox's own index space), rather than looking up by resolved URL —
  // URLs can repeat or fail to resolve, which made indexOf-based lookup fragile.
  const { fullUrls, lightboxIndexByItemIndex } = useMemo(() => {
    const urls: string[] = [];
    const map = new Map<number, number>();
    for (const img of resolved) {
      if (img.type === "full" && img.resolvedUrl) {
        map.set(img.index, urls.length);
        urls.push(img.resolvedUrl);
      }
    }
    return { fullUrls: urls, lightboxIndexByItemIndex: map };
  }, [resolved]);

  if (resolved.length === 0) return null;

  return (
    <>
      <div className={resolved.length > 1 ? "grid grid-cols-2 gap-2 sm:grid-cols-3" : ""}>
        {resolved.map((img) => {
          if (img.type === "full") {
            const fullIndex = lightboxIndexByItemIndex.get(img.index) ?? -1;
            return (
              <button
                key={img.index}
                type="button"
                onClick={() => fullIndex >= 0 && setLightboxIndex(fullIndex)}
                className="group relative overflow-hidden rounded-xl border border-foreground/10"
                aria-label="Xem ảnh lớn và bình luận"
              >
                <ImageWithWatermark
                  src={img.resolvedUrl ?? ""}
                  alt=""
                  loading="lazy"
                  className="max-h-72 w-full object-cover transition-transform group-hover:scale-[1.02]"
                />
                <span className="absolute inset-0 bg-black/0 transition-colors group-hover:bg-black/10" />
              </button>
            );
          }
          return (
            <Link
              key={img.index}
              href="/membership"
              className="group relative flex min-h-40 items-center justify-center overflow-hidden rounded-xl border border-foreground/10"
              aria-label="Mua membership để xem ảnh"
            >
              {img.resolvedUrl && (
                /* eslint-disable-next-line @next/next/no-img-element */
                <img
                  src={img.resolvedUrl}
                  alt=""
                  loading="lazy"
                  className="max-h-72 w-full object-cover"
                />
              )}
              <div className="absolute inset-0 bg-gradient-to-b from-black/30 via-black/50 to-black/70" />
              <div className="relative flex flex-col items-center gap-1.5 px-3 text-center text-white">
                <Lock className="h-5 w-5" />
                <span className="text-xs font-medium">Mua membership để xem</span>
              </div>
            </Link>
          );
        })}
      </div>
      {lightboxIndex !== null && (
        <Lightbox
          images={fullUrls}
          currentIndex={lightboxIndex}
          onClose={() => setLightboxIndex(null)}
          onNavigate={setLightboxIndex}
        />
      )}
    </>
  );
}

function formatRelativeDate(dateStr: string): string {
  const date = new Date(dateStr);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMin = Math.floor(diffMs / 60000);
  if (diffMin < 1) return "Vừa xong";
  if (diffMin < 60) return `${diffMin} phút trước`;
  const diffHours = Math.floor(diffMin / 60);
  if (diffHours < 24) return `${diffHours} giờ trước`;
  const diffDays = Math.floor(diffHours / 24);
  if (diffDays < 7) return diffDays === 1 ? "Hôm qua" : `${diffDays} ngày trước`;
  return date.toLocaleDateString("vi-VN");
}

function FePostCard({ question, subjectCode }: { question: PublicFeQuestion; subjectCode: string }) {
  const [showComments, setShowComments] = useState(false);
  const viewedRef = useRef(false);

  useEffect(() => {
    if (viewedRef.current) return;
    viewedRef.current = true;
    incrementFeQuestionView(subjectCode, question.id);
  }, [subjectCode, question.id]);

  return (
    <div className="space-y-3 rounded-2xl border border-foreground/10 bg-background/70 p-4 backdrop-blur">
      {question.questionText && (
        <p className="whitespace-pre-line text-sm font-medium leading-snug">{question.questionText}</p>
      )}
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1 font-mono text-[11px] text-muted-foreground">
        <span className="flex items-center gap-1">
          <Calendar className="h-3 w-3" />
          {formatRelativeDate(question.createdAt)}
        </span>
        <span className="flex items-center gap-1">
          <Eye className="h-3 w-3" />
          {question.viewCount} lượt xem
        </span>
        <span className="flex items-center gap-1">
          <MessageSquare className="h-3 w-3" />
          {question.commentCount} bình luận
        </span>
      </div>
      <FePostImages images={question.images} questionId={question.id} />
      <button
        type="button"
        onClick={() => setShowComments((v) => !v)}
        className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground hover:text-foreground"
      >
        <MessageSquare className="h-3.5 w-3.5" />
        {question.commentCount} bình luận
        {showComments ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
      </button>
      {showComments && (
        <ExamCommentThread subjectType="fe_question" subjectId={question.id} />
      )}
    </div>
  );
}

function FeQuestionsSection({ feData, subjectCode }: { feData: PublicFeQuestionList; subjectCode: string }) {
  if (feData.questions.length === 0) return null;
  return (
    <section className="space-y-3">
      <div className="flex items-center justify-between">
        <h3 className="app-eyebrow">Câu hỏi FE</h3>
        {feData.locked && (
          <span className="font-mono text-xs text-muted-foreground">
            {feData.previewImageCount} / {feData.totalCount} ảnh xem trước
          </span>
        )}
      </div>
      {feData.locked && (
        <MembershipUpsell
          title="Bộ câu hỏi FE chỉ xem đầy đủ khi là thành viên"
          description="Bạn có thể xem trước một số ảnh. Mua membership để mở khóa toàn bộ ảnh và thảo luận."
        />
      )}
      <div className="space-y-3">
        {feData.questions.map((q) => (
          <FePostCard key={q.id} question={q} subjectCode={subjectCode} />
        ))}
      </div>
    </section>
  );
}

function ResourceList({ resources }: { resources: PublicPaperDetail["resources"] }) {
  const groups = useMemo(() => {
    const map = new Map<string, PublicPaperDetail["resources"]>();
    for (const res of resources) {
      const label = res.folderLabel ?? "Tài nguyên";
      const list = map.get(label) ?? [];
      list.push(res);
      map.set(label, list);
    }
    return Array.from(map.entries());
  }, [resources]);

  if (groups.length === 0) return null;

  return (
    <div className="space-y-3">
      {groups.map(([label, list]) => (
        <div key={label} className="space-y-2">
          <p className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            <FileText className="h-3.5 w-3.5" />
            {label}
          </p>
          <ul className="space-y-2">
            {list.map((res) => (
              <li key={res.id}>
                <a
                  href={paperResourceDownloadUrl(res.downloadUrl)}
                  download={res.originalFilename}
                  className="flex items-center justify-between gap-3 rounded-xl border border-foreground/10 px-4 py-2.5 text-sm transition hover:border-foreground/25 hover:bg-foreground/5"
                >
                  <span className="min-w-0 flex-1 truncate font-medium">{res.originalFilename}</span>
                  <span className="font-mono text-[11px] text-muted-foreground">{formatBytes(res.sizeBytes)}</span>
                  <Download className="h-4 w-4 shrink-0 text-foreground/60" />
                </a>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  );
}

function PaperDetailView({ paperId, onBack }: { paperId: string; onBack: () => void }) {
  const [paper, setPaper] = useState<PublicPaperDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    getExamPaper(paperId)
      .then((data) => {
        if (active) setPaper(data);
      })
      .catch((err) => {
        if (active) setError(err instanceof ApiError ? err.message : "Không tải được đề thi");
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [paperId]);

  const imageUrls = useMemo(
    () => (paper?.imageUrls ?? []).map(resolveMediaUrl).filter(Boolean) as string[],
    [paper?.imageUrls],
  );

  return (
    <div className="space-y-5">
      <button
        type="button"
        onClick={onBack}
        className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-4 w-4" /> Về danh sách đề
      </button>

      {loading ? (
        <div className="space-y-3">
          <div className="app-skeleton h-8 w-56 rounded" />
          <div className="app-skeleton h-64 w-full rounded-2xl" />
        </div>
      ) : error || !paper ? (
        <ErrorBanner message={error ?? "Không tải được đề thi"} />
      ) : (
        <div className="space-y-6">
          <header className="space-y-2">
            <div className="flex flex-wrap items-center gap-1.5">
              <Badge className={paperTypeBadgeClass(paper.type)}>{paper.type}</Badge>
              <Badge variant="secondary">{paper.term}</Badge>
              {paper.retakeLabel && <Badge variant="outline">{paper.retakeLabel}</Badge>}
            </div>
            <h2 className="font-display text-2xl leading-tight">{paper.title}</h2>
            {paper.description && (
              <p className="whitespace-pre-line text-sm text-muted-foreground">{paper.description}</p>
            )}
          </header>

          {imageUrls.length > 0 && (
            <section className="space-y-2">
              <h3 className="app-eyebrow">Ảnh đề thi</h3>
              <p className="text-xs text-muted-foreground">Bấm vào ảnh để xem lớn và bình luận theo từng ảnh.</p>
              <PaperImages urls={imageUrls} />
            </section>
          )}

          {paper.resources.length > 0 && (
            <section className="space-y-2">
              <h3 className="app-eyebrow">Tài nguyên tải về</h3>
              <ResourceList resources={paper.resources} />
            </section>
          )}

          <section className="space-y-2">
            <h3 className="app-eyebrow">Thảo luận về đề</h3>
            <ExamCommentThread paperId={paper.id} />
          </section>
        </div>
      )}
    </div>
  );
}

export default function ExamDetailPage() {
  const params = useParams<{ code: string }>();
  const code = params.code;
  const { loading: authLoading } = useAuth();

  const [detail, setDetail] = useState<PublicSubjectDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [feData, setFeData] = useState<PublicFeQuestionList | null>(null);

  const [typeFilter, setTypeFilter] = useState<TypeFilter>("ALL");
  const [termFilter, setTermFilter] = useState<string>("ALL");
  const [openPaper, setOpenPaper] = useState<PublicPaperSummary | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setDetail(await getExamSubject(code));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được môn thi");
    } finally {
      setLoading(false);
    }
  }, [code]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    let active = true;
    listFeQuestions(code)
      .then((data) => {
        if (active) setFeData(data);
      })
      .catch(() => {
        if (active) setFeData(null);
      });
    return () => {
      active = false;
    };
  }, [code]);

  // Reset filters/opened paper when navigating to a different subject.
  useEffect(() => {
    setTypeFilter("ALL");
    setTermFilter("ALL");
    setOpenPaper(null);
  }, [code]);

  const papers = detail?.papers ?? [];

  const terms = useMemo(() => {
    const set = new Set<string>();
    for (const p of papers) set.add(p.term);
    return Array.from(set).sort();
  }, [papers]);

  const filteredPapers = useMemo(
    () =>
      papers.filter(
        (p) =>
          (typeFilter === "ALL" || p.type === typeFilter) &&
          (termFilter === "ALL" || p.term === termFilter),
      ),
    [papers, typeFilter, termFilter],
  );

  const fePaperCount = useMemo(() => papers.filter((p) => p.type === "FE").length, [papers]);
  const pePaperCount = useMemo(() => papers.filter((p) => p.type === "PE").length, [papers]);

  if (loading || authLoading) {
    return (
      <div className="space-y-4">
        <div className="app-skeleton h-10 w-48 rounded" />
        <div className="app-skeleton h-56 w-full rounded-2xl" />
        <div className="app-skeleton h-32 w-full rounded-2xl" />
      </div>
    );
  }

  if (error && !detail) {
    return (
      <div className="mx-auto max-w-lg rounded-2xl border border-foreground/10 p-8 text-center">
        <h1 className="font-display text-2xl">Không tìm thấy môn thi</h1>
        <p className="mt-2 text-sm text-muted-foreground">{error}</p>
        <Button asChild className="mt-6 rounded-full bg-foreground text-background hover:bg-foreground/90">
          <Link href="/exam">Về danh sách đề thi</Link>
        </Button>
      </div>
    );
  }

  if (!detail) return null;

  const coverUrl = resolveMediaUrl(detail.coverImageUrl);
  const isMember = detail.hasActiveMembership;

  return (
    <div className="space-y-6">
      <nav className="flex items-center gap-1.5 font-mono text-sm text-muted-foreground">
        <Link href="/exam" className="hover:text-foreground hover:underline">Đề thi</Link>
        <span>/</span>
        <span className="text-foreground">{detail.code}</span>
      </nav>

      <header className="space-y-3">
        <div className="flex flex-wrap gap-2">
          {isMember ? (
            <Badge className="bg-emerald-500/15 text-emerald-700 hover:bg-emerald-500/15">Đã mở khóa</Badge>
          ) : (
            <Badge variant="secondary">Xem danh sách</Badge>
          )}
        </div>
        <h1 className="font-display text-5xl leading-none">{detail.code}</h1>
        <p className="text-lg text-muted-foreground">{detail.title}</p>
        <div className="flex flex-wrap gap-x-4 gap-y-1 font-mono text-sm text-muted-foreground">
          <span>{detail.viewCount} lượt xem</span>
          <span>{fePaperCount} đề FE</span>
          <span>{pePaperCount} đề PE</span>
        </div>
      </header>

      <div className="relative h-56 w-full overflow-hidden rounded-2xl shadow-lg">
        {coverUrl ? (
          <>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={coverUrl} alt={detail.title} loading="lazy" className="h-full w-full object-cover" />
            <div className="absolute inset-0 bg-linear-to-t from-black/60 via-black/10 to-transparent" />
            <span className="absolute bottom-4 left-5 font-display text-4xl text-white drop-shadow-lg">{detail.code}</span>
          </>
        ) : (
          <div
            className="flex h-full w-full items-center justify-center font-display text-5xl text-white"
            style={{ background: detail.cardColor ?? "#1a1712" }}
          >
            {detail.code}
          </div>
        )}
      </div>

      {detail.description && (
        <div className="rounded-2xl border border-foreground/10 bg-background/60 p-6 backdrop-blur">
          <h2 className="font-display text-lg">Mô tả</h2>
          <p className="mt-2 whitespace-pre-line text-sm text-muted-foreground">{detail.description}</p>
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-[1fr_260px]">
        <div className="space-y-5">
          {openPaper ? (
            <PaperDetailView paperId={openPaper.id} onBack={() => setOpenPaper(null)} />
          ) : (
            <>
              {feData && <FeQuestionsSection feData={feData} subjectCode={detail?.code ?? code} />}

              {/* Filters */}
              <div className="flex flex-wrap items-center gap-3">
                <div className="flex gap-1 rounded-full border border-foreground/10 bg-background/60 p-1 backdrop-blur">
                  {([
                    { value: "ALL" as const, label: `Tất cả (${papers.length})` },
                    { value: "FE" as const, label: `FE (${fePaperCount})` },
                    { value: "PE" as const, label: `PE (${pePaperCount})` },
                  ]).map((t) => (
                    <button
                      key={t.value}
                      type="button"
                      onClick={() => setTypeFilter(t.value)}
                      className={cn(
                        "rounded-full px-4 py-1.5 text-sm font-medium transition-colors",
                        typeFilter === t.value
                          ? "bg-foreground text-background"
                          : "text-foreground/70 hover:bg-foreground/5",
                      )}
                    >
                      {t.label}
                    </button>
                  ))}
                </div>

                {terms.length > 0 && (
                  <Select value={termFilter} onValueChange={setTermFilter}>
                    <SelectTrigger className="h-9 w-[160px]">
                      <SelectValue placeholder="Kỳ" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="ALL">Tất cả kỳ</SelectItem>
                      {terms.map((term) => (
                        <SelectItem key={term} value={term}>
                          {term}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              </div>

              {!isMember && papers.length > 0 && (
                <MembershipUpsell
                  title="Nội dung đề chỉ dành cho thành viên"
                  description="Bạn có thể xem danh sách đề. Mua membership để mở khóa ảnh đề, tài nguyên tải về và thảo luận."
                />
              )}

              {papers.length === 0 ? (
                <div className="rounded-2xl border border-foreground/10 bg-background/60 p-6 text-sm text-muted-foreground">
                  Môn này chưa có đề thi.
                </div>
              ) : filteredPapers.length === 0 ? (
                <div className="rounded-2xl border border-foreground/10 bg-background/60 p-6 text-sm text-muted-foreground">
                  Không có đề phù hợp bộ lọc.
                </div>
              ) : (
                <div className="grid gap-3 sm:grid-cols-2">
                  {filteredPapers.map((paper) => (
                    <PaperCard
                      key={paper.id}
                      paper={paper}
                      locked={!isMember}
                      onOpen={
                        isMember
                          ? setOpenPaper
                          : () => {
                              if (typeof window !== "undefined") window.location.href = "/membership";
                            }
                      }
                    />
                  ))}
                </div>
              )}
            </>
          )}
        </div>

        {/* Related sidebar */}
        {(detail.related?.length ?? 0) > 0 && (
          <aside className="space-y-3">
            <h2 className="app-eyebrow">Môn liên quan</h2>
            <div className="space-y-2">
              {detail.related.map((rel: PublicSubjectCard) => (
                <Link
                  key={rel.id}
                  href={`/exam/${rel.code}`}
                  className="flex items-center gap-3 rounded-xl border border-foreground/10 bg-background/60 p-3 backdrop-blur transition hover:border-foreground/25 hover:bg-foreground/5"
                >
                  <div
                    className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg font-display text-xs text-white"
                    style={{ background: rel.cardColor ?? "#1a1712" }}
                  >
                    {rel.code.slice(0, 4)}
                  </div>
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">{rel.code}</p>
                    <p className="truncate text-xs text-muted-foreground">{rel.title}</p>
                  </div>
                </Link>
              ))}
            </div>
          </aside>
        )}
      </div>
    </div>
  );
}
