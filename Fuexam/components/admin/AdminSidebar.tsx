"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";
import { isSuperAdmin, staffRoleLabel } from "@/lib/auth/roles";
import { cn } from "@/lib/utils";

const NAV = [
  { href: "/admin", label: "Tổng quan", exact: true, permission: "admin.overview:read" },
  { href: "/admin/users", label: "Người dùng", exact: false, permission: "admin.user:read" },
  { href: "/admin/rbac/roles", label: "Phân quyền", exact: false, permission: "rbac.role:read" },
  {
    href: "/admin/membership/plans",
    label: "Membership — Gói",
    exact: false,
    permission: "membership.admin:read",
  },
  {
    href: "/admin/coursera/catalog",
    label: "Coursera — Khóa học",
    exact: false,
    permission: "coursera.catalog.admin:read",
  },
  {
    href: "/admin/coursera/orders",
    label: "Coursera — Đơn",
    exact: false,
    permission: "coursera.request.admin:read",
  },
  {
    href: "/admin/moderation/flags",
    label: "Moderation — Báo cáo",
    exact: false,
    permission: "forum.moderation:read",
  },
  {
    href: "/admin/moderation/queue",
    label: "Moderation — Hàng chờ",
    exact: false,
    permission: "forum.moderation:read",
  },
  {
    href: "/admin/source/catalog",
    label: "Source — Tài liệu",
    exact: false,
    permission: "source.catalog.admin:read",
  },
  {
    href: "/admin/source/purchases",
    label: "Source — Đơn mua",
    exact: false,
    permission: "source.purchase.admin:read",
  },
];

export function AdminSidebar() {
  const pathname = usePathname();
  const { user, logout } = useAuth();

  const visibleNav = NAV.filter((item) => can(user, item.permission));

  return (
    <aside className="flex w-64 shrink-0 flex-col border-r border-slate-800 bg-slate-950">
      <div className="border-b border-slate-800 px-5 py-5">
        <Link href="/admin" className="flex items-center gap-3">
          <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-amber-500 text-sm font-bold text-slate-950">
            ADM
          </span>
          <div>
            <p className="text-sm font-semibold text-white">FUExam Admin</p>
            <p className="text-xs text-slate-500">Bảng điều khiển</p>
          </div>
        </Link>
      </div>

      <nav className="flex-1 space-y-1 px-3 py-4">
        {visibleNav.map((item) => {
          const active = item.exact ? pathname === item.href : pathname.startsWith(item.href);
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "block rounded-lg px-3 py-2 text-sm font-medium transition",
                active
                  ? "bg-amber-500/15 text-amber-300"
                  : "text-slate-400 hover:bg-slate-900 hover:text-slate-200",
              )}
            >
              {item.label}
            </Link>
          );
        })}
      </nav>

      <div className="border-t border-slate-800 p-4">
        {user && (
          <div className="mb-3 rounded-lg bg-slate-900 px-3 py-2">
            <p className="truncate text-sm font-medium text-slate-200">{user.displayName}</p>
            <p className="truncate text-xs text-slate-500">@{user.username}</p>
            <p className="mt-1 text-xs text-amber-400/90">{staffRoleLabel(user)}</p>
            {isSuperAdmin(user) && (
              <span className="mt-2 inline-block rounded bg-amber-500/20 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-amber-300">
                SUPER_ADMIN
              </span>
            )}
          </div>
        )}
        <Link
          href="/"
          className="mb-2 block rounded-lg px-3 py-2 text-sm text-slate-400 transition hover:bg-slate-900 hover:text-slate-200"
        >
          ← Về diễn đàn
        </Link>
        <button
          type="button"
          onClick={() => logout()}
          className="w-full rounded-lg px-3 py-2 text-left text-sm text-slate-400 transition hover:bg-slate-900 hover:text-slate-200"
        >
          Đăng xuất
        </button>
      </div>
    </aside>
  );
}
