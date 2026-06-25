"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { Check } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { useAuth } from "@/lib/auth/AuthProvider";
import { ApiError } from "@/lib/api/client";
import { getPointsBalance, requestPointsBalanceRefresh } from "@/lib/api/points";
import { SourceQuestionRunner } from "@/components/source/source-question-runner";
import {
  type PublicQuestion,
  type SourceCatalogDetail,
  formatPoints,
  getSourceDetail,
  getSourceQuestions,
  purchaseSource,
} from "@/lib/api/source";

export default function SuocDetailPage() {
  const params = useParams<{ code: string }>();
  const code = params.code;
  const router = useRouter();
  const { user, loading: authLoading } = useAuth();

  const [detail, setDetail] = useState<SourceCatalogDetail | null>(null);
  const [balance, setBalance] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [purchasing, setPurchasing] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [success, setSuccess] = useState<string | null>(null);
  const [questions, setQuestions] = useState<PublicQuestion[]>([]);
  const [questionsLoading, setQuestionsLoading] = useState(false);

  const refreshBalance = useCallback(() => {
    if (!user) {
      setBalance(null);
      return;
    }
    getPointsBalance().then((r) => setBalance(r.balance)).catch(() => setBalance(null));
  }, [user]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setDetail(await getSourceDetail(code));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được tài liệu");
    } finally {
      setLoading(false);
    }
  }, [code]);

  useEffect(() => { load(); }, [load]);
  useEffect(() => { refreshBalance(); }, [refreshBalance]);

  useEffect(() => {
    if (!detail?.hasActiveAccess || !user) {
      setQuestions([]);
      return;
    }
    setQuestionsLoading(true);
    getSourceQuestions(code)
      .then(setQuestions)
      .catch(() => setQuestions([]))
      .finally(() => setQuestionsLoading(false));
  }, [detail?.hasActiveAccess, user, code]);

  function requestPurchase() {
    if (!user) {
      router.push(`/login?next=/suoc/${code}`);
      return;
    }
    setConfirmOpen(true);
  }

  async function handlePurchase() {
    if (!user || !detail) return;
    setError(null);
    setSuccess(null);
    setPurchasing(true);
    try {
      await purchaseSource(detail.id);
      setSuccess("Mua thành công. Source đã được mở cho tài khoản của bạn.");
      setConfirmOpen(false);
      refreshBalance();
      requestPointsBalanceRefresh();
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Mua thất bại");
    } finally {
      setPurchasing(false);
    }
  }

  if (loading || authLoading) {
    return (
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-4 lg:col-span-2">
          <div className="app-skeleton h-10 w-48 rounded" />
          <div className="app-skeleton h-56 w-full rounded-2xl" />
          <div className="app-skeleton h-32 w-full rounded-2xl" />
        </div>
        <div className="app-skeleton h-64 w-full rounded-2xl" />
      </div>
    );
  }

  if (error && !detail) {
    return (
      <div className="mx-auto max-w-lg rounded-2xl border border-foreground/10 p-8 text-center">
        <h1 className="font-display text-2xl">Không tìm thấy tài liệu</h1>
        <p className="mt-2 text-sm text-muted-foreground">{error}</p>
        <Button asChild className="mt-6 rounded-full bg-foreground text-background hover:bg-foreground/90">
          <Link href="/suoc">Về danh sách Source</Link>
        </Button>
      </div>
    );
  }

  if (!detail) return null;

  const ownedActive = Boolean(user && detail.hasActiveAccess);
  const accessEndsLabel = detail.activeAccessEndsAt
    ? new Date(detail.activeAccessEndsAt).toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" })
    : null;
  const balanceAfterPurchase = balance === null ? null : balance - detail.pricePoints;
  const insufficientBalance = balanceAfterPurchase !== null && balanceAfterPurchase < 0;

  const perks = [
    `Truy cập tài liệu trong ${detail.accessDays} ngày`,
    `${detail.questionCount} câu hỏi ôn tập`,
    ...(detail.duplicationRatePercent > 0 ? [`Tỉ lệ trùng lặp đề thi: ${detail.duplicationRatePercent}%`] : []),
    "Luyện câu hỏi, xem đáp án và giải thích ngay trên web",
  ];

  return (
    <>
      <div className="space-y-6">
        <nav className="flex items-center gap-1.5 font-mono text-sm text-muted-foreground">
          <Link href="/suoc" className="hover:text-foreground hover:underline">Source</Link>
          <span>/</span>
          <span className="text-foreground">{detail.code}</span>
        </nav>

        <div className="grid gap-6 lg:grid-cols-3">
          <div className="space-y-6 lg:col-span-2">
            <header className="space-y-3">
              <div className="flex flex-wrap gap-2">
                {detail.featured && <Badge variant="secondary">Nổi bật</Badge>}
                {ownedActive && <Badge className="bg-emerald-500/15 text-emerald-700 hover:bg-emerald-500/15">Đang sở hữu</Badge>}
              </div>
              <h1 className="font-display text-5xl leading-none">{detail.code}</h1>
              <p className="text-lg text-muted-foreground">{detail.title}</p>
              <div className="flex flex-wrap gap-x-4 gap-y-1 font-mono text-sm text-muted-foreground">
                <span>{detail.viewCount} lượt xem</span>
                <span>{detail.questionCount} câu hỏi</span>
                {detail.duplicationRatePercent > 0 && <span className="text-emerald-600">{detail.duplicationRatePercent}% trùng lặp</span>}
              </div>
            </header>

            <div
              className="flex h-56 items-center justify-center rounded-2xl font-display text-5xl text-white shadow-lg"
              style={{ background: detail.cardColor ?? "#1a1712" }}
            >
              {detail.code}
            </div>

            <div className="rounded-2xl border border-foreground/10 bg-background/60 p-6 backdrop-blur">
              <h2 className="font-display text-lg">Bạn sẽ nhận được</h2>
              <ul className="mt-3 grid gap-2.5 text-sm sm:grid-cols-2">
                {perks.map((line) => (
                  <li key={line} className="flex items-start gap-2">
                    <Check className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" />
                    <span>{line}</span>
                  </li>
                ))}
              </ul>
            </div>

            {detail.description && (
              <div className="rounded-2xl border border-foreground/10 bg-background/60 p-6 backdrop-blur">
                <h2 className="font-display text-lg">Mô tả</h2>
                <p className="mt-2 whitespace-pre-line text-sm text-muted-foreground">{detail.description}</p>
              </div>
            )}

            {ownedActive && (
              questionsLoading ? (
                <div className="rounded-2xl border border-foreground/10 p-6">
                  <div className="app-skeleton h-5 w-32 rounded" />
                  <div className="mt-4 space-y-2">
                    {Array.from({ length: 3 }).map((_, i) => <div key={i} className="app-skeleton h-11 w-full rounded-lg" />)}
                  </div>
                </div>
              ) : (
                <SourceQuestionRunner questions={questions} />
              )
            )}
          </div>

          <aside className="space-y-4 lg:sticky lg:top-28 lg:self-start">
            <div className="rounded-2xl border border-foreground/10 bg-background/70 p-6 backdrop-blur-xl">
              <div className="text-center">
                <p className="app-eyebrow">Giá</p>
                <p className="font-display text-4xl">{formatPoints(detail.pricePoints)}</p>
              </div>

              <div className="mt-5">
                {success ? (
                  <div className="space-y-2 text-center">
                    <p className="rounded-xl bg-emerald-500/10 px-3 py-2 text-sm text-emerald-700">{success}</p>
                    <Button asChild className="w-full rounded-full bg-foreground text-background hover:bg-foreground/90">
                      <Link href="/suoc/my-purchases">Xem Source của tôi</Link>
                    </Button>
                  </div>
                ) : ownedActive ? (
                  <div className="space-y-2 text-center">
                    <p className="rounded-xl bg-emerald-500/10 px-3 py-2 text-sm text-emerald-700">
                      Bạn đang sở hữu Source này{accessEndsLabel ? ` đến ${accessEndsLabel}` : ""}.
                    </p>
                    <Button asChild variant="outline" className="w-full rounded-full">
                      <Link href="/suoc/my-purchases">Xem Source của tôi</Link>
                    </Button>
                  </div>
                ) : (
                  <Button className="w-full rounded-full bg-foreground text-background hover:bg-foreground/90" disabled={purchasing} onClick={requestPurchase}>
                    {purchasing ? "Đang xử lý..." : user ? "Mua ngay" : "Đăng nhập để mua"}
                  </Button>
                )}
              </div>

              {error && <p className="mt-3 rounded-xl bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>}
              {balance !== null && <p className="mt-3 text-center font-mono text-xs text-muted-foreground">Số dư: {formatPoints(balance)}</p>}

              <dl className="mt-4 space-y-2 border-t border-foreground/10 pt-4 text-sm">
                <div className="flex justify-between"><dt className="text-muted-foreground">Thời hạn</dt><dd className="font-medium">{detail.accessDays} ngày</dd></div>
                <div className="flex justify-between"><dt className="text-muted-foreground">Số câu hỏi</dt><dd className="font-medium">{detail.questionCount} câu</dd></div>
                {detail.duplicationRatePercent > 0 && (
                  <div className="flex justify-between"><dt className="text-muted-foreground">Trùng lặp</dt><dd className="font-medium">{detail.duplicationRatePercent}%</dd></div>
                )}
              </dl>
            </div>

            {detail.related.length > 0 && (
              <div className="rounded-2xl border border-foreground/10 bg-background/60 p-6 backdrop-blur">
                <h2 className="font-display text-lg">Tài liệu liên quan</h2>
                <ul className="mt-3 space-y-2">
                  {detail.related.map((rel) => (
                    <li key={rel.id}>
                      <Link href={`/suoc/${rel.code}`} className="flex items-center justify-between gap-2 rounded-xl border border-foreground/10 px-3 py-2 text-sm transition hover:border-foreground/25 hover:bg-foreground/5">
                        <span className="font-medium">{rel.code}</span>
                        <span className="font-mono text-muted-foreground">{formatPoints(rel.pricePoints)}</span>
                      </Link>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </aside>
        </div>
      </div>

      <AlertDialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xác nhận mua Source {detail.code}?</AlertDialogTitle>
            <AlertDialogDescription>
              Bạn sẽ bị trừ {formatPoints(detail.pricePoints)} và có quyền truy cập trong {detail.accessDays} ngày.
              {balance !== null ? ` Số dư sau mua: ${formatPoints(Math.max(0, balanceAfterPurchase ?? 0))}.` : ""}
            </AlertDialogDescription>
          </AlertDialogHeader>
          {insufficientBalance && (
            <p className="rounded-xl bg-destructive/10 px-3 py-2 text-sm text-destructive">
              Số dư hiện tại không đủ. Vui lòng nạp thêm Fuexam Point trước khi mua.
            </p>
          )}
          <AlertDialogFooter>
            <AlertDialogCancel>Hủy</AlertDialogCancel>
            <AlertDialogAction onClick={handlePurchase} disabled={purchasing || insufficientBalance}>
              {purchasing ? "Đang mua..." : "Xác nhận mua"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
