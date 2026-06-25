"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
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
import {
  createCustomPaymentLink,
  createPaymentLink,
  listDepositTiers,
  MIN_CUSTOM_DEPOSIT_VND,
  type DepositTier,
} from "@/lib/api/payment";

const formatVnd = (n: number) => `${n.toLocaleString("vi-VN")} VND`;
const formatPoints = (n: number) => `${n.toLocaleString("vi-VN")} Fuexam`;

type PendingDeposit =
  | { type: "tier"; tier: DepositTier }
  | { type: "custom"; amountVnd: number }
  | null;

export default function DepositPage() {
  const router = useRouter();
  const { user, loading: authLoading } = useAuth();
  const [tiers, setTiers] = useState<DepositTier[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [submittingId, setSubmittingId] = useState<string | null>(null);
  const [customAmount, setCustomAmount] = useState("");
  const [customSubmitting, setCustomSubmitting] = useState(false);
  const [pendingDeposit, setPendingDeposit] = useState<PendingDeposit>(null);

  const parsedCustomAmount = Number(customAmount.replace(/\D/g, ""));
  const customAmountValid =
    Number.isFinite(parsedCustomAmount) && parsedCustomAmount >= MIN_CUSTOM_DEPOSIT_VND;

  const pendingSummary = useMemo(() => {
    if (!pendingDeposit) return null;
    if (pendingDeposit.type === "tier") {
      return {
        amount: pendingDeposit.tier.amountVnd,
        points: pendingDeposit.tier.totalPoints,
        label: `Gói ${formatVnd(pendingDeposit.tier.amountVnd)}`,
      };
    }
    return {
      amount: pendingDeposit.amountVnd,
      points: pendingDeposit.amountVnd,
      label: "Nạp linh động",
    };
  }, [pendingDeposit]);

  useEffect(() => {
    listDepositTiers()
      .then(setTiers)
      .catch((err) => {
        setError(err instanceof ApiError ? err.message : "Không tải được danh sách mệnh giá.");
      })
      .finally(() => setLoading(false));
  }, []);

  function requireLogin() {
    router.push(`/login?next=${encodeURIComponent("/deposit")}`);
  }

  function requestTierDeposit(tier: DepositTier) {
    if (!user) {
      requireLogin();
      return;
    }
    setError(null);
    setPendingDeposit({ type: "tier", tier });
  }

  function requestCustomDeposit() {
    if (!user) {
      requireLogin();
      return;
    }
    if (!customAmountValid) {
      setError(`Số tiền nạp tối thiểu là ${formatVnd(MIN_CUSTOM_DEPOSIT_VND)}.`);
      return;
    }
    setError(null);
    setPendingDeposit({ type: "custom", amountVnd: parsedCustomAmount });
  }

  async function confirmDeposit() {
    if (!pendingDeposit) return;
    setError(null);
    try {
      const origin = window.location.origin;
      if (pendingDeposit.type === "tier") {
        setSubmittingId(pendingDeposit.tier.id);
        const response = await createPaymentLink({
          tierId: pendingDeposit.tier.id,
          returnUrl: `${origin}/payment/success`,
          cancelUrl: `${origin}/payment/cancel`,
        });
        if (response.checkoutUrl) {
          window.location.href = response.checkoutUrl;
          return;
        }
      } else {
        setCustomSubmitting(true);
        const response = await createCustomPaymentLink({
          amountVnd: pendingDeposit.amountVnd,
          returnUrl: `${origin}/payment/success`,
          cancelUrl: `${origin}/payment/cancel`,
        });
        if (response.checkoutUrl) {
          window.location.href = response.checkoutUrl;
          return;
        }
      }
      setError("Không tạo được link thanh toán mới. Vui lòng thử lại.");
      setPendingDeposit(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tạo được link thanh toán.");
      setPendingDeposit(null);
    } finally {
      setSubmittingId(null);
      setCustomSubmitting(false);
    }
  }

  return (
    <>
      <div className="mx-auto max-w-5xl px-4 py-10">
        <div className="mb-8 text-center">
          <h1 className="font-display text-4xl tracking-tight">Nạp Fuexam Point</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            Nhập số tiền tùy ý hoặc chọn mệnh giá có sẵn. Thanh toán qua PayOS và nhận Fuexam Point tương ứng.
          </p>
        </div>

        <div className="mb-8 rounded-2xl border border-emerald-500/30 bg-emerald-500/5 p-6">
          <p className="text-xs font-semibold uppercase tracking-wider text-emerald-700">
            Nạp linh động
          </p>
          <p className="mt-1 text-sm text-muted-foreground">
            Nhập số tiền bạn muốn nạp, tối thiểu {formatVnd(MIN_CUSTOM_DEPOSIT_VND)}. Quy đổi 1 VND = 1 Fuexam Point.
          </p>
          <div className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-stretch">
            <div className="relative flex-1">
              <input
                type="text"
                inputMode="numeric"
                value={parsedCustomAmount > 0 ? parsedCustomAmount.toLocaleString("vi-VN") : ""}
                onChange={(e) => setCustomAmount(e.target.value)}
                placeholder="Ví dụ: 50.000"
                className="w-full rounded-xl border border-foreground/15 bg-background px-4 py-2.5 pr-14 text-lg font-semibold text-foreground outline-none transition focus:border-emerald-500 focus:ring-4 focus:ring-emerald-500/10"
              />
              <span className="absolute right-4 top-1/2 -translate-y-1/2 text-sm text-muted-foreground">VND</span>
            </div>
            <Button
              type="button"
              onClick={requestCustomDeposit}
              disabled={authLoading || customSubmitting || (customAmount !== "" && !customAmountValid)}
              className="rounded-xl bg-emerald-600 px-6 py-2.5 text-white hover:bg-emerald-500"
            >
              {!user ? "Đăng nhập để nạp" : customSubmitting ? "Đang tạo link..." : "Nạp ngay"}
            </Button>
          </div>
          {customAmountValid && (
            <p className="mt-3 text-sm text-muted-foreground">
              Bạn sẽ nhận được <span className="font-semibold text-emerald-700">{formatPoints(parsedCustomAmount)}</span>.
            </p>
          )}
        </div>

        {!loading && tiers.length > 0 && (
          <p className="mb-4 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            Hoặc chọn mệnh giá có sẵn
          </p>
        )}

        {error && (
          <div className="mb-6 rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3 text-center text-sm text-destructive">
            {error}
          </div>
        )}

        {loading && <p className="text-center text-sm text-muted-foreground">Đang tải mệnh giá...</p>}

        {!loading && tiers.length === 0 && (
          <div className="rounded-lg border border-foreground/10 bg-background p-8 text-center text-sm text-muted-foreground">
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
                className="flex flex-col rounded-2xl border border-foreground/10 bg-background p-6 shadow-sm transition hover:border-emerald-500/50 hover:shadow-md"
              >
                <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  Mệnh giá
                </p>
                <p className="mt-1 font-display text-3xl">{formatVnd(tier.amountVnd)}</p>
                {tier.bonusPercent > 0 && (
                  <p className="mt-1 text-xs font-medium text-emerald-700">
                    +{tier.bonusPercent}% bonus
                  </p>
                )}

                <div className="mt-4 flex-1 rounded-lg border border-emerald-500/20 bg-emerald-500/5 p-3 text-center">
                  <p className="text-xs text-muted-foreground">Bạn nhận được</p>
                  <p className="mt-1 text-2xl font-bold text-emerald-700">
                    {formatPoints(tier.totalPoints)}
                  </p>
                </div>

                <Button
                  type="button"
                  onClick={() => requestTierDeposit(tier)}
                  disabled={disabled}
                  className="mt-5 w-full rounded-xl bg-emerald-600 px-4 py-2.5 text-white hover:bg-emerald-500"
                >
                  {!user ? "Đăng nhập để nạp" : submitting ? "Đang tạo link..." : "Chọn mệnh giá này"}
                </Button>
              </article>
            );
          })}
        </div>
      </div>

      <AlertDialog open={Boolean(pendingDeposit)} onOpenChange={(open) => !open && setPendingDeposit(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xác nhận nạp tiền?</AlertDialogTitle>
            <AlertDialogDescription>
              {pendingSummary
                ? `${pendingSummary.label}: thanh toán ${formatVnd(pendingSummary.amount)} và nhận ${formatPoints(pendingSummary.points)}.`
                : "Vui lòng kiểm tra số tiền trước khi tiếp tục."}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Hủy</AlertDialogCancel>
            <AlertDialogAction onClick={confirmDeposit} disabled={customSubmitting || Boolean(submittingId)}>
              {customSubmitting || submittingId ? "Đang tạo link..." : "Tiếp tục thanh toán"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
