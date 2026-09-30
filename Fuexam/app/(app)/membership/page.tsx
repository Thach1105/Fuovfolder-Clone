"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ErrorBanner } from "@/components/ui/error-banner";
import { LoadingState } from "@/components/ui/loading-state";
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
import { requestPointsBalanceRefresh } from "@/lib/api/points";
import * as membershipApi from "@/lib/api/membership";
import { type VoucherPreviewResponse, previewVoucher } from "@/lib/api/voucher";
import { resolveMediaUrl } from "@/lib/api/media";
import type { MembershipPlanResponse, MembershipStatusResponse } from "@/types/api";

export default function MembershipPage() {
  const router = useRouter();
  const { user, loading: authLoading, refreshUser } = useAuth();
  const [plans, setPlans] = useState<MembershipPlanResponse[]>([]);
  const [status, setStatus] = useState<MembershipStatusResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [subscribing, setSubscribing] = useState<string | null>(null);
  const [confirmPlanSlug, setConfirmPlanSlug] = useState<string | null>(null);
  const [voucherCode, setVoucherCode] = useState("");
  const [voucherPreview, setVoucherPreview] = useState<VoucherPreviewResponse | null>(null);
  const [voucherError, setVoucherError] = useState<string | null>(null);
  const [applyingVoucher, setApplyingVoucher] = useState(false);

  const confirmPlan = useMemo(
    () => plans.find((plan) => plan.slug === confirmPlanSlug) ?? null,
    [plans, confirmPlanSlug],
  );

  useEffect(() => {
    membershipApi
      .listMembershipPlans()
      .then(setPlans)
      .catch(() => setError("Không tải được gói membership."))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (!user) {
      setStatus(null);
      return;
    }
    membershipApi
      .getMyMembership()
      .then(setStatus)
      .catch(() => setStatus(null));
  }, [user]);

  function resetVoucherState() {
    setVoucherCode("");
    setVoucherPreview(null);
    setVoucherError(null);
  }

  function requestSubscribe(planSlug: string) {
    if (!user) {
      router.push(`/login?next=${encodeURIComponent("/membership")}`);
      return;
    }
    resetVoucherState();
    setConfirmPlanSlug(planSlug);
  }

  const handleApplyVoucher = async () => {
    if (!voucherCode.trim() || !confirmPlan) return;
    setApplyingVoucher(true);
    setVoucherError(null);
    try {
      const result = await previewVoucher(voucherCode.trim(), "membership", confirmPlan.pricePoints);
      if (result.valid) {
        setVoucherPreview(result);
      } else {
        setVoucherError(result.message);
        setVoucherPreview(null);
      }
    } catch (err) {
      setVoucherError(err instanceof ApiError ? err.message : "Không thể áp dụng voucher");
      setVoucherPreview(null);
    } finally {
      setApplyingVoucher(false);
    }
  };

  const subscribe = async () => {
    if (!confirmPlan) return;
    setSubscribing(confirmPlan.slug);
    setError(null);
    try {
      const result = await membershipApi.subscribeMembership(
        confirmPlan.slug,
        voucherPreview ? voucherCode.trim() : undefined,
      );
      setStatus(result);
      setConfirmPlanSlug(null);
      resetVoucherState();
      requestPointsBalanceRefresh();
      await refreshUser();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không đăng ký được gói.");
    } finally {
      setSubscribing(null);
    }
  };

  const tierAccent = (slug: string) => {
    if (slug.includes("nova")) return "from-fuchsia-500 to-purple-600";
    if (slug.includes("vip")) return "from-emerald-500 to-teal-600";
    return "from-sky-500 to-blue-600";
  };

  const isPopular = (slug: string) => slug.includes("vip");

  return (
    <>
      <div className="mx-auto max-w-6xl px-4 py-10">
        <div className="mb-10 text-center">
          <h1 className="font-display text-4xl tracking-tight">Membership</h1>
          <p className="mt-2 text-muted-foreground">
            Nâng cấp tài khoản để mở quyền lợi thành viên, vai trò riêng và ưu đãi trong cộng đồng.
          </p>
          {status?.active && (
            <p className="mt-4 inline-flex items-center gap-2 rounded-full border border-emerald-500/30 bg-emerald-500/10 px-4 py-1.5 text-sm text-emerald-700">
              Gói hiện tại: <strong>{status.planName}</strong> - {" "}
              {status.expiresAt ? `hết hạn ${new Date(status.expiresAt).toLocaleDateString("vi-VN")}` : "Trọn đời"}
            </p>
          )}
        </div>

        <ErrorBanner message={error} className="mb-6" />

        {loading && <LoadingState message="Đang tải gói..." className="text-center" />}

        <div className="grid gap-6 md:grid-cols-3">
          {plans.map((plan) => {
            const popular = isPopular(plan.slug);
            const imageUrl = resolveMediaUrl(plan.imageUrl);
            return (
              <article
                key={plan.id}
                className={`relative flex flex-col overflow-hidden rounded-2xl border bg-background shadow-sm transition hover:shadow-md ${
                  popular ? "border-foreground/30 ring-1 ring-foreground/10" : "border-foreground/10"
                }`}
              >
                {popular && (
                  <span className="absolute right-4 top-4 z-10 rounded-full bg-foreground px-3 py-1 font-mono text-[10px] uppercase tracking-widest text-background">
                    Phổ biến
                  </span>
                )}

                <div className={`relative h-28 w-full bg-gradient-to-br ${tierAccent(plan.slug)}`}>
                  {imageUrl && (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                      src={imageUrl}
                      alt=""
                      loading="lazy"
                      className="h-full w-full object-cover"
                      onError={(e) => { e.currentTarget.style.display = "none"; }}
                    />
                  )}
                  <span className="pointer-events-none absolute inset-0 flex items-center justify-center font-display text-2xl font-bold text-white/90 drop-shadow">
                    {plan.name}
                  </span>
                </div>

                <div className="flex flex-1 flex-col p-6">
                  <p className="font-display text-2xl">
                    {plan.pricePoints.toLocaleString("vi-VN")} {" "}
                    <span className="text-sm font-normal text-muted-foreground">Fuexam Point</span>
                  </p>
                  <p className="mt-1 text-xs text-muted-foreground">
                    {plan.billingInterval === "lifetime" ? "Trọn đời" : `${plan.durationDays} ngày`} - Role {plan.roleSlug}
                  </p>
                  {plan.description && <p className="mt-4 text-sm text-muted-foreground">{plan.description}</p>}
                  <Button
                    type="button"
                    disabled={authLoading || subscribing === plan.slug || status?.active}
                    onClick={() => requestSubscribe(plan.slug)}
                    className={`mt-6 w-full rounded-full ${
                      popular
                        ? "bg-foreground text-background hover:bg-foreground/90"
                        : "border border-foreground/20 bg-transparent text-foreground hover:bg-foreground/5"
                    }`}
                  >
                    {!user
                      ? "Đăng nhập để mua"
                      : subscribing === plan.slug
                        ? "Đang xử lý..."
                        : status?.active
                          ? "Đã có gói active"
                          : "Đăng ký bằng Fuexam Point"}
                  </Button>
                </div>
              </article>
            );
          })}
        </div>

        <p className="mt-10 text-center text-xs text-muted-foreground">
          Tỷ giá: 1.000 Fuexam = 1.000 VND - {" "}
          <Link href="/deposit" className="font-medium text-foreground hover:underline">
            Nạp Fuexam Point
          </Link>
        </p>
      </div>

      <AlertDialog open={Boolean(confirmPlan)} onOpenChange={(open) => { if (!open) { setConfirmPlanSlug(null); resetVoucherState(); } }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xác nhận mua membership?</AlertDialogTitle>
            <AlertDialogDescription>
              {confirmPlan
                ? `Bạn sẽ mua gói ${confirmPlan.name} với giá ${confirmPlan.pricePoints.toLocaleString("vi-VN")} Fuexam Point ${confirmPlan.billingInterval === "lifetime" ? "trọn đời" : `trong ${confirmPlan.durationDays} ngày`}.`
                : "Vui lòng kiểm tra lại gói trước khi xác nhận."}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <div className="space-y-2">
            <div className="flex gap-2">
              <Input
                placeholder="Nhập mã voucher"
                value={voucherCode}
                onChange={(e) => setVoucherCode(e.target.value.toUpperCase())}
                className="flex-1"
              />
              <Button variant="outline" onClick={handleApplyVoucher} disabled={applyingVoucher || !voucherCode.trim()}>
                {applyingVoucher ? "..." : "Áp dụng"}
              </Button>
            </div>
            {voucherError && <p className="text-sm text-destructive">{voucherError}</p>}
            {voucherPreview && confirmPlan && (
              <div className="rounded-lg bg-emerald-500/10 px-3 py-2 text-sm text-emerald-700">
                <p>{voucherPreview.message}</p>
                <p>Giá gốc: {confirmPlan.pricePoints.toLocaleString("vi-VN")} → Giá mới: {voucherPreview.finalPoints.toLocaleString("vi-VN")} (giảm {voucherPreview.discountPoints.toLocaleString("vi-VN")} Fuexam Point)</p>
              </div>
            )}
          </div>
          <AlertDialogFooter>
            <AlertDialogCancel>Hủy</AlertDialogCancel>
            <AlertDialogAction onClick={subscribe} disabled={!confirmPlan || subscribing === confirmPlan.slug}>
              {confirmPlan && subscribing === confirmPlan.slug ? "Đang mua..." : "Xác nhận mua"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
