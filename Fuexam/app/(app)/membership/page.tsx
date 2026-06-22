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

  const tierStyle = (slug: string) => {
    if (slug.includes("nova")) {
      return "border-fuchsia-500/40 bg-gradient-to-b from-fuchsia-950/40 to-slate-950";
    }
    if (slug.includes("vip")) {
      return "border-emerald-500/40 bg-gradient-to-b from-emerald-950/40 to-slate-950";
    }
    return "border-sky-500/40 bg-gradient-to-b from-sky-950/40 to-slate-950";
  };

  return (
    <div className="mx-auto max-w-6xl px-4 py-10">
      <div className="mb-10 text-center">
        <h1 className="text-3xl font-bold text-white">Membership</h1>
        <p className="mt-2 text-slate-400">
          Nâng cấp tài khoản để mở khóa quyền lợi diễn đàn, tài liệu và hiệu ứng đặc biệt.
        </p>
        {status?.active && (
          <p className="mt-4 text-sm text-emerald-400">
            Gói hiện tại: <strong>{status.planName}</strong> — hết hạn{" "}
            {status.expiresAt ? new Date(status.expiresAt).toLocaleDateString("vi-VN") : "—"}
          </p>
        )}
      </div>

      {error && (
        <div className="mb-6 rounded-lg border border-red-800 bg-red-950/50 px-4 py-3 text-sm text-red-300">
          {error}
        </div>
      )}

      {loading && <p className="text-center text-sm text-slate-400">Đang tải gói...</p>}

      <div className="grid gap-6 md:grid-cols-3">
        {plans.map((plan) => (
          <article
            key={plan.id}
            className={`rounded-2xl border p-6 ${tierStyle(plan.slug)} ${plan.slug.includes("vip") ? "ring-1 ring-emerald-500/30" : ""}`}
          >
            {resolveMediaUrl(plan.imageUrl) && (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={resolveMediaUrl(plan.imageUrl)!}
                alt=""
                className="mb-4 h-24 w-full rounded-lg object-cover"
              />
            )}
            <h2 className="text-xl font-bold text-white">{plan.name}</h2>
            <p className="mt-2 text-3xl font-bold text-amber-300">
              {plan.pricePoints.toLocaleString("vi-VN")}{" "}
              <span className="text-sm font-normal text-slate-400">Fuexam</span>
            </p>
            <p className="mt-1 text-xs text-slate-500">{plan.durationDays} ngày · Role {plan.roleSlug}</p>
            {plan.description && <p className="mt-4 text-sm text-slate-400">{plan.description}</p>}
            <button
              type="button"
              disabled={authLoading || subscribing === plan.slug || status?.active}
              onClick={() => subscribe(plan.slug)}
              className="btn-primary mt-6 w-full bg-amber-500 hover:bg-amber-400 disabled:opacity-50"
            >
              {!user
                ? "Đăng nhập để mua"
                : subscribing === plan.slug
                  ? "Đang xử lý..."
                  : status?.active
                    ? "Đã có gói active"
                    : "Đăng ký bằng Fuexam Point"}
            </button>
          </article>
        ))}
      </div>

      <p className="mt-10 text-center text-xs text-slate-500">
        Tỷ giá: 1.000 Fuexam = 1.000 VND ·{" "}
        <Link href="/me/points" className="text-amber-400 hover:underline">
          Nạp Fuexam Point
        </Link>
      </p>
    </div>
  );
}
