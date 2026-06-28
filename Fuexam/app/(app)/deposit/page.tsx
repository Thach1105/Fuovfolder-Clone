"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
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
import { cn } from "@/lib/utils";
import {
  createCustomPaymentLink,
  createPaymentLink,
  listDepositTiers,
  listMyDeposits,
  resumeDeposit,
  MIN_CUSTOM_DEPOSIT_VND,
  type DepositTier,
  type DepositHistoryItem,
  type DepositHistoryPage,
} from "@/lib/api/payment";

const formatVnd = (n: number) => `${n.toLocaleString("vi-VN")} VND`;
const formatPoints = (n: number) => `${n.toLocaleString("vi-VN")} Fuexam`;
const formatDate = (s: string) =>
  new Date(s).toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "short" });

const STATUS_CONFIG: Record<string, { label: string; className: string }> = {
  paid: { label: "Đã thanh toán", className: "bg-emerald-500/15 text-emerald-600" },
  pending: { label: "Chờ thanh toán", className: "bg-yellow-500/15 text-yellow-600" },
  expired: { label: "Hết hạn", className: "bg-muted text-muted-foreground" },
  failed: { label: "Thất bại", className: "bg-red-500/15 text-red-600" },
};

type PendingDeposit =
  | { type: "tier"; tier: DepositTier }
  | { type: "custom"; amountVnd: number }
  | null;

export default function DepositPage() {
  const router = useRouter();
  const { user, loading: authLoading } = useAuth();

  // Tab
  const [activeTab, setActiveTab] = useState<"deposit" | "history">("deposit");

  // Deposit form state
  const [tiers, setTiers] = useState<DepositTier[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [submittingId, setSubmittingId] = useState<string | null>(null);
  const [customAmount, setCustomAmount] = useState("");
  const [customSubmitting, setCustomSubmitting] = useState(false);
  const [pendingDeposit, setPendingDeposit] = useState<PendingDeposit>(null);

  // History state
  const [deposits, setDeposits] = useState<DepositHistoryPage | null>(null);
  const [historyPage, setHistoryPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState<string>("");
  const [historyLoading, setHistoryLoading] = useState(false);
  const [resumingId, setResumingId] = useState<string | null>(null);

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

  useEffect(() => {
    if (activeTab !== "history") return;
    setHistoryLoading(true);
    listMyDeposits({ page: historyPage, size: 20, status: statusFilter || undefined })
      .then(setDeposits)
      .catch(() => toast.error("Không tải được lịch sử nạp tiền"))
      .finally(() => setHistoryLoading(false));
  }, [activeTab, historyPage, statusFilter]);

  async function handleResume(item: DepositHistoryItem) {
    setResumingId(item.orderId);
    try {
      const res = await resumeDeposit(item.orderId);
      if (res.canResume && res.checkoutUrl) {
        window.location.href = res.checkoutUrl;
      } else {
        toast.error(res.message || "Không thể tiếp tục thanh toán");
        listMyDeposits({ page: historyPage, size: 20, status: statusFilter || undefined }).then(setDeposits);
      }
    } catch {
      toast.error("Có lỗi xảy ra, vui lòng thử lại.");
    } finally {
      setResumingId(null);
    }
  }

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

        {/* Tab bar */}
        <div className="mb-6 flex gap-0 border-b border-border">
          <button
            className={cn(
              "px-5 py-2.5 text-sm font-medium border-b-2 -mb-px transition-colors",
              activeTab === "deposit"
                ? "border-emerald-500 text-emerald-700"
                : "border-transparent text-muted-foreground hover:text-foreground"
            )}
            onClick={() => setActiveTab("deposit")}
          >
            Nạp tiền
          </button>
          <button
            className={cn(
              "px-5 py-2.5 text-sm font-medium border-b-2 -mb-px transition-colors",
              activeTab === "history"
                ? "border-emerald-500 text-emerald-700"
                : "border-transparent text-muted-foreground hover:text-foreground"
            )}
            onClick={() => setActiveTab("history")}
          >
            Lịch sử nạp tiền
          </button>
        </div>

        {/* Deposit form tab */}
        {activeTab === "deposit" && (
          <>
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
          </>
        )}

        {/* History tab */}
        {activeTab === "history" && (
          <div>
            {/* Filter row */}
            <div className="mb-4 flex flex-wrap items-center gap-3">
              <label className="text-sm font-medium text-muted-foreground">Trạng thái:</label>
              <select
                value={statusFilter}
                onChange={(e) => { setStatusFilter(e.target.value); setHistoryPage(0); }}
                className="rounded-lg border border-foreground/15 bg-background px-3 py-1.5 text-sm outline-none focus:border-emerald-500"
              >
                <option value="">Tất cả</option>
                <option value="pending">Chờ thanh toán</option>
                <option value="paid">Đã thanh toán</option>
                <option value="failed">Thất bại</option>
                <option value="expired">Hết hạn</option>
              </select>
            </div>

            {historyLoading && (
              <p className="py-10 text-center text-sm text-muted-foreground">Đang tải lịch sử...</p>
            )}

            {!historyLoading && deposits && deposits.items.length === 0 && (
              <div className="rounded-lg border border-foreground/10 bg-background p-8 text-center text-sm text-muted-foreground">
                Chưa có giao dịch nào.
              </div>
            )}

            {!historyLoading && deposits && deposits.items.length > 0 && (
              <>
                <div className="overflow-x-auto rounded-xl border border-foreground/10">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-foreground/10 bg-muted/40 text-left text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                        <th className="px-4 py-3">Ngày</th>
                        <th className="px-4 py-3">Mã đơn</th>
                        <th className="px-4 py-3">Số tiền</th>
                        <th className="px-4 py-3">Gói</th>
                        <th className="px-4 py-3">Points</th>
                        <th className="px-4 py-3">Trạng thái</th>
                        <th className="px-4 py-3">Hành động</th>
                      </tr>
                    </thead>
                    <tbody>
                      {deposits.items.map((item) => {
                        const statusCfg = STATUS_CONFIG[item.status] ?? { label: item.status, className: "bg-muted text-muted-foreground" };
                        const isResuming = resumingId === item.orderId;
                        return (
                          <tr key={item.orderId} className="border-b border-foreground/5 last:border-0 hover:bg-muted/20 transition-colors">
                            <td className="px-4 py-3 text-muted-foreground whitespace-nowrap">
                              {formatDate(item.createdAt)}
                            </td>
                            <td className="px-4 py-3 font-mono text-xs text-muted-foreground">
                              {item.orderCode}
                            </td>
                            <td className="px-4 py-3 font-semibold whitespace-nowrap">
                              {formatVnd(item.amount)}
                            </td>
                            <td className="px-4 py-3 text-muted-foreground">
                              {item.tierLabel ?? "Nạp linh động"}
                            </td>
                            <td className="px-4 py-3 text-emerald-700 font-medium whitespace-nowrap">
                              {item.pointsAwarded != null ? formatPoints(item.pointsAwarded) : "—"}
                            </td>
                            <td className="px-4 py-3">
                              <span className={cn("inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium", statusCfg.className)}>
                                {statusCfg.label}
                              </span>
                            </td>
                            <td className="px-4 py-3">
                              {item.canResume ? (
                                <Button
                                  size="sm"
                                  variant="outline"
                                  disabled={isResuming}
                                  onClick={() => handleResume(item)}
                                  className="rounded-lg border-emerald-500/40 text-emerald-700 hover:bg-emerald-500/10 text-xs px-3 py-1"
                                >
                                  {isResuming ? "Đang xử lý..." : "Thanh toán tiếp"}
                                </Button>
                              ) : (
                                <span className="text-xs text-muted-foreground">—</span>
                              )}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>

                {/* Pagination */}
                {deposits.totalPages > 1 && (
                  <div className="mt-4 flex items-center justify-between text-sm">
                    <span className="text-muted-foreground">
                      Trang {deposits.page + 1} / {deposits.totalPages} ({deposits.totalElements} giao dịch)
                    </span>
                    <div className="flex gap-2">
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={historyPage === 0}
                        onClick={() => setHistoryPage((p) => Math.max(0, p - 1))}
                      >
                        Trước
                      </Button>
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={historyPage >= deposits.totalPages - 1}
                        onClick={() => setHistoryPage((p) => p + 1)}
                      >
                        Tiếp
                      </Button>
                    </div>
                  </div>
                )}
              </>
            )}
          </div>
        )}
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
