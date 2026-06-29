"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { BadgeCheck, Crown, Wallet, GraduationCap, CalendarDays } from "lucide-react";
import { useAuth } from "@/lib/auth/AuthProvider";
import { resolveMediaUrl } from "@/lib/api/media";
import { getPointsBalance } from "@/lib/api/points";
import * as membershipApi from "@/lib/api/membership";
import type { MembershipStatusResponse } from "@/types/api";
import { UserAvatar } from "@/components/shared/user-avatar";

/**
 * Thẻ hồ sơ nổi bật ("VIP"): avatar lớn, huy hiệu membership, số dư điểm,
 * cơ sở và ngày tham gia. Đồng bộ theme sáng của toàn site.
 */
export function ProfileHero() {
  const { user } = useAuth();
  const [balance, setBalance] = useState<number | null>(null);
  const [membership, setMembership] = useState<MembershipStatusResponse | null>(null);

  useEffect(() => {
    if (!user) return;
    let active = true;
    getPointsBalance()
      .then((r) => active && setBalance(r.balance))
      .catch(() => active && setBalance(null));
    membershipApi
      .getMyMembership()
      .then((r) => active && setMembership(r))
      .catch(() => active && setMembership(null));
    return () => {
      active = false;
    };
  }, [user]);

  if (!user) return null;

  const avatar = resolveMediaUrl(user.avatarUrl);
  const isVip = membership?.active ?? false;
  const memberSince = user.createdAt
    ? new Date(user.createdAt).toLocaleDateString("vi-VN", { month: "long", year: "numeric" })
    : null;

  return (
    <div className="relative overflow-hidden rounded-3xl border border-foreground/10 bg-background shadow-sm">
      {/* Dải nền gradient nhẹ phía trên */}
      <div
        className={`h-24 w-full ${
          isVip
            ? "bg-gradient-to-r from-amber-400 via-orange-400 to-rose-400"
            : "bg-gradient-to-r from-sky-400 via-indigo-400 to-violet-400"
        }`}
      />

      <div className="px-6 pb-6">
        <div className="-mt-12 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div className="flex items-end gap-4">
            <div className="relative">
              <UserAvatar src={avatar} displayName={user.displayName} size="xl" className="rounded-2xl border-4 border-background shadow-md" />
              {isVip && (
                <span className="absolute -bottom-2 -right-2 flex h-8 w-8 items-center justify-center rounded-full bg-amber-400 text-amber-950 shadow ring-2 ring-background">
                  <Crown className="h-4 w-4" />
                </span>
              )}
            </div>

            <div className="pb-1">
              <div className="flex items-center gap-2">
                <h1 className="font-display text-2xl leading-none">{user.displayName}</h1>
                {user.emailVerified && (
                  <BadgeCheck className="h-5 w-5 text-sky-500" aria-label="Đã xác minh email" />
                )}
              </div>
              <p className="mt-1 text-sm text-muted-foreground">@{user.username}</p>
            </div>
          </div>

          <div className="flex flex-wrap gap-2 sm:pb-1">
            <Link
              href="/settings/profile"
              className="inline-flex items-center justify-center rounded-full border border-foreground/20 bg-transparent px-4 py-2 text-sm font-medium text-foreground transition hover:bg-foreground/5"
            >
              Chỉnh sửa hồ sơ
            </Link>
            {!isVip && (
              <Link
                href="/membership"
                className="inline-flex items-center justify-center rounded-full bg-foreground px-4 py-2 text-sm font-medium text-background transition hover:bg-foreground/90"
              >
                Nâng cấp VIP
              </Link>
            )}
          </div>
        </div>

        {/* Hàng thẻ thống kê */}
        <div className="mt-6 grid gap-3 sm:grid-cols-3">
          <div className="rounded-2xl border border-foreground/10 bg-muted/40 p-4">
            <div className="flex items-center gap-2 text-xs font-medium text-muted-foreground">
              <Crown className="h-3.5 w-3.5" /> Hạng thành viên
            </div>
            <p className="mt-1 font-semibold">
              {isVip ? membership?.planName ?? "VIP" : "Thành viên thường"}
            </p>
            {isVip && membership?.expiresAt && (
              <p className="mt-0.5 text-xs text-muted-foreground">
                Hết hạn {new Date(membership.expiresAt).toLocaleDateString("vi-VN")}
              </p>
            )}
          </div>

          <Link
            href="/me/points"
            className="rounded-2xl border border-foreground/10 bg-muted/40 p-4 transition hover:border-emerald-500/40 hover:bg-emerald-500/5"
          >
            <div className="flex items-center gap-2 text-xs font-medium text-muted-foreground">
              <Wallet className="h-3.5 w-3.5" /> Số dư Fuexam Point
            </div>
            <p className="mt-1 font-semibold text-emerald-700">
              {balance === null ? "…" : `${balance.toLocaleString("vi-VN")} Fuexam`}
            </p>
            <p className="mt-0.5 text-xs text-muted-foreground">Bấm để xem lịch sử</p>
          </Link>

          <div className="rounded-2xl border border-foreground/10 bg-muted/40 p-4">
            <div className="flex items-center gap-2 text-xs font-medium text-muted-foreground">
              <CalendarDays className="h-3.5 w-3.5" /> Tham gia từ
            </div>
            <p className="mt-1 font-semibold">{memberSince ?? "—"}</p>
            <p className="mt-0.5 flex items-center gap-1 text-xs text-muted-foreground">
              <GraduationCap className="h-3 w-3" /> Cộng đồng FPT
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
