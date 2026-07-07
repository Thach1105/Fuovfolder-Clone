"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Download, Lock } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ErrorBanner } from "@/components/ui/error-banner";
import { useAuth } from "@/lib/auth/AuthProvider";
import { ApiError } from "@/lib/api/client";
import { cn } from "@/lib/utils";
import { resolveMediaUrl } from "@/lib/api/media";
import { ExamFeRunner } from "@/components/exam/exam-fe-runner";
import { ExamCommentThread } from "@/components/exam/exam-comment-thread";
import { Lightbox } from "@/components/exam/Lightbox";
import { ImageWithWatermark } from "@/components/shared/image-with-watermark";
import {
  type PublicFeQuestionList,
  type PublicPeItem,
  type PublicSubjectDetail,
  getExamFeQuestions,
  getExamPeItems,
  getExamSubject,
  peResourceDownloadUrl,
} from "@/lib/api/exam";

type Tab = "fe" | "pe";

function formatBytes(bytes: number): string {
  if (!bytes) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  const i = Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)));
  return `${(bytes / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1)} ${units[i]}`;
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

function PeExamImages({ urls }: { urls: string[] }) {
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null);
  if (urls.length === 0) return null;
  return (
    <>
      <div className={urls.length > 1 ? "grid grid-cols-2 gap-2" : ""}>
        {urls.map((url, idx) => (
          <button
            key={`${url}-${idx}`}
            type="button"
            onClick={() => setLightboxIndex(idx)}
            className="group relative overflow-hidden rounded-xl border border-foreground/10"
            aria-label="Xem ảnh lớn"
          >
            <ImageWithWatermark src={url} alt="" loading="lazy" className="max-h-72 w-full object-cover transition-transform group-hover:scale-[1.02]" />
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

function PeItemCard({ item }: { item: PublicPeItem }) {
  const images = (item.examImageUrls ?? []).map(resolveMediaUrl).filter(Boolean) as string[];
  const groups = useMemo(() => {
    const map = new Map<string, typeof item.resources>();
    for (const res of item.resources) {
      const label = res.folderLabel ?? "Tài nguyên";
      const list = map.get(label) ?? [];
      list.push(res);
      map.set(label, list);
    }
    return Array.from(map.entries());
  }, [item.resources]);

  return (
    <div className="space-y-4 rounded-2xl border border-foreground/10 bg-background/70 p-5 backdrop-blur-xl sm:p-6">
      <div>
        <h3 className="font-display text-lg">{item.title}</h3>
        {item.description && (
          <p className="mt-1 whitespace-pre-line text-sm text-muted-foreground">{item.description}</p>
        )}
      </div>

      <PeExamImages urls={images} />

      {groups.length > 0 && (
        <div className="space-y-3">
          {groups.map(([label, resources]) => (
            <div key={label} className="space-y-2">
              <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">{label}</p>
              <ul className="space-y-2">
                {resources.map((res) => (
                  <li key={res.id}>
                    <a
                      href={peResourceDownloadUrl(res.downloadUrl)}
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
      )}

      <ExamCommentThread subjectType="pe_item" subjectId={item.id} />
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
  const [tab, setTab] = useState<Tab>("fe");

  const [feList, setFeList] = useState<PublicFeQuestionList | null>(null);
  const [feLoading, setFeLoading] = useState(false);
  const [peItems, setPeItems] = useState<PublicPeItem[]>([]);
  const [peLoading, setPeLoading] = useState(false);
  const [peError, setPeError] = useState<string | null>(null);

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
    if (!detail) return;
    setFeLoading(true);
    getExamFeQuestions(code)
      .then(setFeList)
      .catch(() => setFeList(null))
      .finally(() => setFeLoading(false));
  }, [detail, code]);

  useEffect(() => {
    if (!detail?.hasActiveMembership) {
      setPeItems([]);
      return;
    }
    setPeLoading(true);
    setPeError(null);
    getExamPeItems(code)
      .then(setPeItems)
      .catch((err) => setPeError(err instanceof ApiError ? err.message : "Không tải được đề PE"))
      .finally(() => setPeLoading(false));
  }, [detail?.hasActiveMembership, code]);

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
  const feLocked = feList?.locked ?? true;

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
            <Badge variant="secondary">Xem thử</Badge>
          )}
        </div>
        <h1 className="font-display text-5xl leading-none">{detail.code}</h1>
        <p className="text-lg text-muted-foreground">{detail.title}</p>
        <div className="flex flex-wrap gap-x-4 gap-y-1 font-mono text-sm text-muted-foreground">
          <span>{detail.viewCount} lượt xem</span>
          <span>{detail.feQuestionCount} câu FE</span>
          <span>{detail.pePaperCount} đề PE</span>
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

      <div className="flex gap-1 rounded-full border border-foreground/10 bg-background/60 p-1 backdrop-blur">
        {([
          { value: "fe" as const, label: `Trắc nghiệm FE (${detail.feQuestionCount})` },
          { value: "pe" as const, label: `Thực hành PE (${detail.pePaperCount})` },
        ]).map((t) => (
          <button
            key={t.value}
            type="button"
            onClick={() => setTab(t.value)}
            className={cn(
              "flex-1 rounded-full px-4 py-2 text-sm font-medium transition-colors",
              tab === t.value ? "bg-foreground text-background" : "text-foreground/70 hover:bg-foreground/5",
            )}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === "fe" && (
        <section className="space-y-4">
          {feLoading ? (
            <div className="rounded-2xl border border-foreground/10 p-6">
              <div className="app-skeleton h-5 w-32 rounded" />
              <div className="mt-4 space-y-2">
                {Array.from({ length: 3 }).map((_, i) => <div key={i} className="app-skeleton h-11 w-full rounded-lg" />)}
              </div>
            </div>
          ) : !feList || feList.questions.length === 0 ? (
            <div className="rounded-2xl border border-foreground/10 bg-background/60 p-6 text-sm text-muted-foreground">
              Môn này chưa có câu hỏi trắc nghiệm FE.
            </div>
          ) : (
            <>
              <ExamFeRunner questions={feList.questions} showComments={!feLocked} />
              {feLocked && (
                <MembershipUpsell
                  title={`Bạn đang xem ${feList.previewCount}/${feList.totalCount} câu`}
                  description={`Mua membership để xem toàn bộ ${feList.totalCount} câu hỏi FE, bình luận và tải đề PE.`}
                />
              )}
            </>
          )}
        </section>
      )}

      {tab === "pe" && (
        <section className="space-y-4">
          {!isMember ? (
            <MembershipUpsell
              title="Đề thực hành PE chỉ dành cho thành viên"
              description={`Mua membership để mở khóa ${detail.pePaperCount} đề PE kèm tài nguyên tải về và thảo luận.`}
            />
          ) : peLoading ? (
            <div className="rounded-2xl border border-foreground/10 p-6">
              <div className="app-skeleton h-5 w-40 rounded" />
              <div className="mt-4 space-y-2">
                {Array.from({ length: 2 }).map((_, i) => <div key={i} className="app-skeleton h-24 w-full rounded-lg" />)}
              </div>
            </div>
          ) : peError ? (
            <ErrorBanner message={peError} />
          ) : peItems.length === 0 ? (
            <div className="rounded-2xl border border-foreground/10 bg-background/60 p-6 text-sm text-muted-foreground">
              Môn này chưa có đề thực hành PE.
            </div>
          ) : (
            <div className="space-y-5">
              {peItems.map((item) => <PeItemCard key={item.id} item={item} />)}
            </div>
          )}
        </section>
      )}
    </div>
  );
}
