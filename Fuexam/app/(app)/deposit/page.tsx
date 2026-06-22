"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/AuthProvider";
import { ApiError } from "@/lib/api/client";
import {
  createPaymentLink,
  listDepositTiers,
  type DepositTier,
} from "@/lib/api/payment";

const formatVnd = (n: number) => `${n.toLocaleString("vi-VN")} ₫`;
const formatPoints = (n: number) => `${n.toLocaleString("vi-VN")} Fuexam`;

export default function DepositPage() {
  const router = useRouter();
  const { user, loading: authLoading } = useAuth();
  const [tiers, setTiers] = useState<DepositTier[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [submittingId, setSubmittingId] = useState<string | null>(null);

  useEffect(() => {
    listDepositTiers()
      .then(setTiers)
      .catch((err) => {
        setError(err instanceof ApiError ? err.message : "Không tải được danh sách mệnh giá.");
      })
      .finally(() => setLoading(false));
  }, []);

  const handleSelect = async (tier: DepositTier) => {
    if (!user) {
      router.push(`/login?next=${encodeURIComponent("/deposit")}`);
      return;
    }
    setSubmittingId(tier.id);
    setError(null);
    try {
      const origin = window.location.origin;
      const response = await createPaymentLink({
        tierId: tier.id,
        returnUrl: `${origin}/payment/success`,
        cancelUrl: `${origin}/payment/cancel`,
      });
      if (response.checkoutUrl) {
        window.location.href = response.checkoutUrl;
        return;
      }
      setError("Không tạo được link thanh toán mới. Vui lòng thử lại.");
      setSubmittingId(null);
      return;
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tạo được link thanh toán.");
      setSubmittingId(null);
    }
  };

  return (
    <div className="mx-auto max-w-5xl px-4 py-10">
      <div className="mb-8 text-center">
        <h1 className="text-3xl font-bold text-slate-100">Nạp Fuexam Point</h1>
        <p className="mt-2 text-sm text-slate-400">
          Chọn mệnh giá cố định, thanh toán qua PayOS và nhận Fuexam Point tương ứng.
        </p>
      </div>

      {error && (
        <div className="mb-6 rounded-lg border border-red-800 bg-red-950/50 px-4 py-3 text-center text-sm text-red-300">
          {error}
        </div>
      )}

      {loading && (
        <p className="text-center text-sm text-slate-400">Đang tải mệnh giá...</p>
      )}

      {!loading && tiers.length === 0 && (
        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-8 text-center text-sm text-slate-400">
          Hiện chưa có mệnh giá nào được mở bán. Vui lòng quay lại sau.
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {tiers.map((tier) => {
          const submitting = submittingId === tier.id;
          const disabled = authLoading || submitting;
          return (
            <article
              key={tier.id}
              className="flex flex-col rounded-2xl border border-slate-800 bg-slate-900/50 p-6 transition hover:border-emerald-500/60"
            >
              <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                Mệnh giá
              </p>
              <p className="mt-1 text-3xl font-bold text-slate-100">
                {formatVnd(tier.amountVnd)}
              </p>
              {tier.bonusPercent > 0 && (
                <p className="mt-1 text-xs font-medium text-emerald-400">
                  +{tier.bonusPercent}% bonus
                </p>
              )}

              <div className="mt-4 flex-1 rounded-lg border border-slate-800 bg-slate-950/60 p-3 text-center">
                <p className="text-xs text-slate-500">Bạn nhận được</p>
                <p className="mt-1 text-2xl font-bold text-emerald-400">
                  {formatPoints(tier.totalPoints)}
                </p>
              </div>

              <button
                type="button"
                onClick={() => handleSelect(tier)}
                disabled={disabled}
                className="mt-5 inline-flex w-full items-center justify-center rounded-lg bg-emerald-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {!user
                  ? "Đăng nhập để nạp"
                  : submitting
                    ? "Đang tạo link..."
                    : "Chọn mệnh giá này"}
              </button>
            </article>
          );
        })}
      </div>
    </div>
  );
}
