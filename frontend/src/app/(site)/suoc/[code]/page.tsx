"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { useAuth } from "@/lib/auth/AuthProvider";
import { ApiError } from "@/lib/api/client";
import { getPointsBalance } from "@/lib/api/points";
import { SourceQuestionRunner } from "@/components/source/SourceQuestionRunner";
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
  const [success, setSuccess] = useState<string | null>(null);
  const [questions, setQuestions] = useState<PublicQuestion[]>([]);
  const [questionsLoading, setQuestionsLoading] = useState(false);

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
  }, [code, user?.id]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!user) {
      setBalance(null);
      return;
    }
    getPointsBalance().then((r) => setBalance(r.balance)).catch(() => setBalance(null));
  }, [user]);

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

  async function handlePurchase() {
    if (!user) {
      router.push("/login");
      return;
    }
    if (!detail) return;
    setError(null);
    setSuccess(null);
    setPurchasing(true);
    try {
      await purchaseSource(detail.id);
      setSuccess("Mua thành công! Tài liệu đã được thêm vào Suộc của bạn.");
      getPointsBalance().then((r) => setBalance(r.balance)).catch(() => {});
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
          <div className="skeleton h-8 w-40 rounded" />
          <div className="skeleton h-56 w-full rounded-2xl" />
          <div className="skeleton h-32 w-full rounded-2xl" />
        </div>
        <div className="skeleton h-64 w-full rounded-2xl" />
      </div>
    );
  }

  if (error && !detail) {
    return (
      <div className="card mx-auto max-w-lg p-8 text-center">
        <h1 className="text-xl font-bold text-ink-900">Không tìm thấy tài liệu</h1>
        <p className="mt-2 text-sm text-ink-500">{error}</p>
        <Link href="/suoc" className="btn-primary mt-6 inline-flex">
          Về danh sách Suộc
        </Link>
      </div>
    );
  }

  if (!detail) {
    return null;
  }

  const ownedActive = Boolean(user && detail.hasActiveAccess);
  const accessEndsLabel =
    detail.activeAccessEndsAt != null
      ? new Date(detail.activeAccessEndsAt).toLocaleDateString("vi-VN", {
          day: "2-digit",
          month: "2-digit",
          year: "numeric",
        })
      : null;

  return (
    <div className="space-y-5">
      <nav className="flex items-center gap-1.5 text-sm text-ink-500">
        <Link href="/suoc" className="hover:text-ink-900 hover:underline">
          Tài liệu ôn thi
        </Link>
        <span>/</span>
        <span className="font-medium text-ink-700">{detail.code}</span>
      </nav>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-5 lg:col-span-2">
          <header className="space-y-3">
            <div className="flex flex-wrap items-center gap-2">
              {detail.featured && (
                <span className="rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-semibold text-amber-700">
                  Nổi bật
                </span>
              )}
              {ownedActive && (
                <span className="rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-semibold text-emerald-700">
                  Đang sở hữu
                </span>
              )}
            </div>
            <h1 className="font-display text-3xl text-ink-900">{detail.code}</h1>
            <p className="text-base text-ink-600">{detail.title}</p>
            <div className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-ink-500">
              <span>{detail.viewCount} lượt xem</span>
              <span>{detail.questionCount} câu hỏi</span>
              {detail.duplicationRatePercent > 0 && (
                <span className="font-medium text-emerald-600">
                  {detail.duplicationRatePercent}% trùng lặp
                </span>
              )}
            </div>
          </header>

          <div
            className="flex h-56 items-center justify-center rounded-2xl font-display text-4xl text-white shadow-soft"
            style={{ background: detail.cardColor ?? "#6d28d9" }}
          >
            {detail.code}
          </div>

          <div className="card space-y-3 p-5">
            <h2 className="text-sm font-semibold text-ink-900">Bạn sẽ nhận được</h2>
            <ul className="grid gap-2.5 text-sm text-ink-700 sm:grid-cols-2">
              {[
                `Truy cập tài liệu trong ${detail.accessDays} ngày`,
                `${detail.questionCount} câu hỏi ôn tập chất lượng`,
                ...(detail.duplicationRatePercent > 0
                  ? [`Tỉ lệ trùng lặp đề thi: ${detail.duplicationRatePercent}%`]
                  : []),
                "Học mọi lúc, mọi nơi trên mọi thiết bị",
              ].map((line) => (
                <li key={line} className="flex items-start gap-2">
                  <CheckIcon />
                  <span>{line}</span>
                </li>
              ))}
            </ul>
          </div>

          {detail.description && (
            <div className="card space-y-2 p-5">
              <h2 className="text-sm font-semibold text-ink-900">Mô tả</h2>
              <p className="whitespace-pre-line text-sm text-ink-600">{detail.description}</p>
            </div>
          )}

          {ownedActive &&
            (questionsLoading ? (
              <div className="card p-5">
                <div className="skeleton h-5 w-32 rounded" />
                <div className="mt-4 space-y-2">
                  {Array.from({ length: 3 }).map((_, i) => (
                    <div key={i} className="skeleton h-10 w-full rounded-lg" />
                  ))}
                </div>
              </div>
            ) : (
              <SourceQuestionRunner questions={questions} />
            ))}
        </div>

        <aside className="space-y-4 lg:sticky lg:top-24 lg:self-start">
          <div className="card space-y-4 p-5">
            <div className="text-center">
              <p className="eyebrow">Giá</p>
              <p className="font-display text-3xl text-fuo-700">{formatPoints(detail.pricePoints)}</p>
            </div>

            {success ? (
              <div className="space-y-2 text-center">
                <p className="rounded-xl bg-emerald-50 px-3 py-2 text-sm text-emerald-800">{success}</p>
                <Link href="/suoc/my-purchases" className="btn-accent block w-full">
                  Xem Suộc của tôi
                </Link>
              </div>
            ) : ownedActive ? (
              <div className="space-y-2 text-center">
                <p className="rounded-xl bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
                  Bạn đang sở hữu tài liệu này
                  {accessEndsLabel ? ` (hết hạn ${accessEndsLabel})` : ""}.
                </p>
                <Link href="/suoc/my-purchases" className="btn-secondary block w-full">
                  Xem Suộc của tôi
                </Link>
              </div>
            ) : (
              <button
                type="button"
                className="btn-accent w-full"
                disabled={purchasing}
                onClick={handlePurchase}
              >
                {purchasing ? "Đang xử lý..." : user ? "Mua ngay" : "Đăng nhập để mua"}
              </button>
            )}

            {error && (
              <p className="rounded-xl bg-red-50 px-3 py-2 text-sm text-red-800">{error}</p>
            )}
            {balance !== null && (
              <p className="text-center text-xs text-ink-500">Số dư: {formatPoints(balance)}</p>
            )}

            <dl className="space-y-2 border-t border-ink-200 pt-3 text-sm">
              <div className="flex justify-between">
                <dt className="text-ink-500">Thời hạn</dt>
                <dd className="font-medium text-ink-800">{detail.accessDays} ngày</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-ink-500">Số câu hỏi</dt>
                <dd className="font-medium text-ink-800">{detail.questionCount} câu</dd>
              </div>
              {detail.duplicationRatePercent > 0 && (
                <div className="flex justify-between">
                  <dt className="text-ink-500">Trùng lặp</dt>
                  <dd className="font-medium text-ink-800">{detail.duplicationRatePercent}%</dd>
                </div>
              )}
            </dl>
          </div>

          {detail.related.length > 0 && (
            <div className="card space-y-3 p-5">
              <h2 className="text-sm font-semibold text-ink-900">Tài liệu liên quan</h2>
              <ul className="space-y-2">
                {detail.related.map((rel) => (
                  <li key={rel.id}>
                    <Link
                      href={`/suoc/${rel.code}`}
                      className="flex items-center justify-between gap-2 rounded-xl border border-ink-200 px-3 py-2 text-sm transition hover:border-ink-300 hover:bg-ink-50"
                    >
                      <span className="font-medium text-ink-700">{rel.code}</span>
                      <span className="text-fuo-700">{formatPoints(rel.pricePoints)}</span>
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </aside>
      </div>
    </div>
  );
}

function CheckIcon() {
  return (
    <svg
      className="mt-0.5 shrink-0 text-emerald-600"
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.5"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M20 6 9 17l-5-5" />
    </svg>
  );
}