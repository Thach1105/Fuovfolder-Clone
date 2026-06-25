"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useAuth } from "@/lib/auth/AuthProvider";
import { ApiError } from "@/lib/api/client";
import * as membershipApi from "@/lib/api/membership";
import { resolveMediaUrl } from "@/lib/api/media";
import type { MembershipPlanResponse, MembershipStatusResponse } from "@/types/api";

export default function MembershipPage() {
  const { user, loading: authLoading, refreshUser } = useAuth();
  const [plans, setPlans] = useState<MembershipPlanResponse[]>([]);
  const [status, setStatus] = useState<MembershipStatusResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [subscribing, setSubscribing] = useState<string | null>(null);

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

  const subscribe = async (planSlug: string) => {
    if (!user) {
      window.location.href = `/login?next=${encodeURIComponent("/membership")}`;
      return;
    }
    setSubscribing(planSlug);
    setError(null);
    try {
      const result = await membershipApi.subscribeMembership(planSlug);
      setStatus(result);
      await refreshUser();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không đăng ký được gói.");
    } finally {
      setSubscribing(null);
    }
  };

  // Mỗi hạng gói có một dải màu nhấn nhẹ trên nền sáng, đồng bộ theme chung.
  const tierAccent = (slug: string) => {
    if (slug.includes("nova")) {
      return "from-fuchsia-500 to-purple-600";
    }
    if (slug.includes("vip")) {
      return "from-emerald-500 to-teal-600";
    }
    return "from-sky-500 to-blue-600";
  };

  const isPopular = (slug: string) => slug.includes("vip");

  return (
    <div className="mx-auto max-w-6xl px-4 py-10">
      <div className="mb-10 text-center">
        <h1 className="font-display text-4xl tracking-tight">Membership</h1>
        <p className="mt-2 text-muted-foreground">
          Nâng cấp tài khoản để mở khóa quyền lợi diễn đàn, tài liệu và hiệu ứng đặc biệt.
        </p>
        {status?.active && (
          <p className="mt-4 inline-flex items-center gap-2 rounded-full border border-emerald-500/30 bg-emerald-500/10 px-4 py-1.5 text-sm text-emerald-700">
            Gói hiện tại: <strong>{status.planName}</strong> — hết hạn{" "}
            {status.expiresAt ? new Date(status.expiresAt).toLocaleDateString("vi-VN") : "—"}
          </p>
        )}
      </div>

      {error && (
        <div className="mb-6 rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      )}

      {loading && <p className="text-center text-sm text-muted-foreground">Đang tải gói...</p>}

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

              {/* Ảnh bìa gói — có fallback gradient + chữ cái khi ảnh lỗi/thiếu */}
              <div className={`relative h-28 w-full bg-gradient-to-br ${tierAccent(plan.slug)}`}>
                {imageUrl && (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    src={imageUrl}
                    alt=""
                    className="h-full w-full object-cover"
                    onError={(e) => {
                      e.currentTarget.style.display = "none";
                    }}
                  />
                )}
                <span className="pointer-events-none absolute inset-0 flex items-center justify-center font-display text-2xl font-bold text-white/90 drop-shadow">
                  {plan.name}
                </span>
              </div>

              <div className="flex flex-1 flex-col p-6">
                <p className="font-display text-2xl">
                  {plan.pricePoints.toLocaleString("vi-VN")}{" "}
                  <span className="text-sm font-normal text-muted-foreground">Fuexam</span>
                </p>
                <p className="mt-1 text-xs text-muted-foreground">
                  {plan.durationDays} ngày · Role {plan.roleSlug}
                </p>
                {plan.description && (
                  <p className="mt-4 text-sm text-muted-foreground">{plan.description}</p>
                )}
                <button
                  type="button"
                  disabled={authLoading || subscribing === plan.slug || status?.active}
                  onClick={() => subscribe(plan.slug)}
                  className={`mt-6 inline-flex w-full items-center justify-center rounded-full px-5 py-2.5 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50 ${
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
                </button>
              </div>
            </article>
          );
        })}
      </div>

      <p className="mt-10 text-center text-xs text-muted-foreground">
        Tỷ giá: 1.000 Fuexam = 1.000 VND ·{" "}
        <Link href="/me/points" className="font-medium text-foreground hover:underline">
          Nạp Fuexam Point
        </Link>
      </p>
    </div>
  );
}
