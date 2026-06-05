"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { useAuth } from "@/lib/auth/AuthProvider";
import { ApiError } from "@/lib/api/client";
import { getPointsBalance } from "@/lib/api/points";
import {
  type SourceCatalogDetail,
  formatPoints,
  getSourceDetail,
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
    return <p className="text-sm text-slate-500">Đang tải...</p>;
  }

  if (error && !detail) {
    return (
      <div className="card mx-auto max-w-lg p-8 text-center">
        <h1 className="text-xl font-bold text-slate-900">Không tìm thấy tài liệu</h1>
        <p className="mt-2 text-sm text-slate-500">{error}</p>
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
    <div className="space-y-4">
      <p className="text-sm text-fuo-600">
        <Link href="/suoc" className="hover:underline">
          Tài liệu ôn thi
        </Link>
      </p>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-4 lg:col-span-2">
          {detail.featured && (
            <span className="inline-block rounded bg-amber-100 px-2 py-0.5 text-xs font-semibold text-amber-700">
              Nổi bật
            </span>
          )}
          <h1 className="text-2xl font-bold text-emerald-700">{detail.code}</h1>
          <div className="flex flex-wrap gap-4 text-sm text-slate-500">
            <span>{detail.viewCount} lượt xem</span>
            <span>{detail.questionCount} câu hỏi</span>
            {detail.duplicationRatePercent > 0 && (
              <span className="font-medium text-emerald-600">
                {detail.duplicationRatePercent}% trùng lặp
              </span>
            )}
          </div>

          <div
            className="flex h-56 items-center justify-center rounded-xl text-3xl font-bold text-white"
            style={{ background: detail.cardColor ?? "#6d28d9" }}
          >
            {detail.code}
          </div>

          <div className="card space-y-3 p-5">
            <h2 className="flex items-center gap-2 text-sm font-semibold text-slate-900">
              Bạn sẽ nhận được
            </h2>
            <ul className="grid gap-2 text-sm text-slate-700 sm:grid-cols-2">
              <li>✔ Truy cập tài liệu trong {detail.accessDays} ngày</li>
              <li>✔ {detail.questionCount} câu hỏi ôn tập chất lượng</li>
              {detail.duplicationRatePercent > 0 && (
                <li>✔ Tỷ lệ trùng lặp đề thi: {detail.duplicationRatePercent}%</li>
              )}
              <li>✔ Học mọi lúc, mọi nơi trên mọi thiết bị</li>
            </ul>
          </div>

          {detail.description && (
            <div className="card p-5 text-sm text-slate-700">
              <p className="whitespace-pre-line">{detail.description}</p>
            </div>
          )}
        </div>

        <aside className="space-y-4">
          <div className="card space-y-4 p-5">
            <p className="text-center text-2xl font-bold text-fuo-700">
              {formatPoints(detail.pricePoints)}
            </p>
            {success ? (
              <div className="space-y-2 text-center">
                <p className="rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-800">{success}</p>
                <Link href="/suoc/my-purchases" className="btn-primary block w-full text-center">
                  Xem Suộc của tôi
                </Link>
              </div>
            ) : ownedActive ? (
              <div className="space-y-2 text-center">
                <p className="rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
                  Bạn đang sở hữu tài liệu này
                  {accessEndsLabel ? ` (hết hạn ${accessEndsLabel})` : ""}.
                </p>
                <Link href="/suoc/my-purchases" className="btn-primary block w-full text-center">
                  Xem Suộc của tôi
                </Link>
              </div>
            ) : (
              <button
                type="button"
                className="btn-primary w-full"
                disabled={purchasing}
                onClick={handlePurchase}
              >
                {purchasing ? "Đang xử lý..." : user ? "Mua ngay" : "Đăng nhập để mua"}
              </button>
            )}
            {error && (
              <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-800">{error}</p>
            )}
            {balance !== null && (
              <p className="text-center text-xs text-slate-500">Số dư: {formatPoints(balance)}</p>
            )}
            <dl className="space-y-2 border-t border-slate-100 pt-3 text-sm">
              <div className="flex justify-between">
                <dt className="text-slate-500">Thời hạn</dt>
                <dd className="font-medium text-slate-800">{detail.accessDays} ngày</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-slate-500">Số câu hỏi</dt>
                <dd className="font-medium text-slate-800">{detail.questionCount} câu</dd>
              </div>
              {detail.duplicationRatePercent > 0 && (
                <div className="flex justify-between">
                  <dt className="text-slate-500">Trùng lặp</dt>
                  <dd className="font-medium text-slate-800">{detail.duplicationRatePercent}%</dd>
                </div>
              )}
            </dl>
          </div>

          {detail.related.length > 0 && (
            <div className="card space-y-3 p-5">
              <h2 className="text-sm font-semibold text-slate-900">Tài liệu liên quan</h2>
              <ul className="space-y-2">
                {detail.related.map((rel) => (
                  <li key={rel.id}>
                    <Link
                      href={`/suoc/${rel.code}`}
                      className="flex items-center justify-between gap-2 rounded-lg border border-slate-200 px-3 py-2 text-sm hover:border-slate-300"
                    >
                      <span className="font-medium text-slate-700">{rel.code}</span>
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
