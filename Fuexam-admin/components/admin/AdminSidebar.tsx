"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  Award,
  BookOpen,
  GraduationCap,
  LayoutDashboard,
  LogOut,
  type LucideIcon,
  ShieldCheck,
  ShoppingBag,
  FileText,
  Flag,
  Inbox,
  Users,
  CreditCard,
} from "lucide-react";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";
import { isSuperAdmin, staffRoleLabel } from "@/lib/auth/roles";
import { cn } from "@/lib/utils";

interface NavItem {
  href: string;
  label: string;
  icon: LucideIcon;
  exact?: boolean;
  permission: string;
}

interface NavSection {
  title: string;
  items: NavItem[];
}

const NAV: NavSection[] = [
  {
    title: "Tổng quan",
    items: [
      {
        href: "/dashboard",
        label: "Bảng điều khiển",
        icon: LayoutDashboard,
        exact: true,
        permission: "admin.overview:read",
      },
    ],
  },
  {
    title: "Người dùng",
    items: [
      { href: "/users", label: "Người dùng", icon: Users, permission: "admin.user:read" },
      { href: "/rbac/roles", label: "Vai trò & quyền", icon: ShieldCheck, permission: "rbac.role:read" },
    ],
  },
  {
    title: "Bán hàng",
    items: [
      {
        href: "/source/catalog",
        label: "Source — Tài liệu",
        icon: FileText,
        permission: "source.catalog.admin:read",
      },
      {
        href: "/source/purchases",
        label: "Source — Đơn mua",
        icon: ShoppingBag,
        permission: "source.purchase.admin:read",
      },
      {
        href: "/coursera/catalog",
        label: "Coursera — Khóa học",
        icon: GraduationCap,
        permission: "coursera.catalog.admin:read",
      },
      {
        href: "/coursera/orders",
        label: "Coursera — Đơn",
        icon: BookOpen,
        permission: "coursera.request.admin:read",
      },
      {
        href: "/membership/plans",
        label: "Membership — Gói",
        icon: CreditCard,
        permission: "membership.admin:read",
      },
    ],
  },
  {
    title: "Kiểm duyệt & Danh hiệu",
    items: [
      {
        href: "/moderation/flags",
        label: "Báo cáo",
        icon: Flag,
        permission: "forum.moderation:read",
      },
      {
        href: "/moderation/queue",
        label: "Hàng chờ duyệt",
        icon: Inbox,
        permission: "forum.moderation:read",
      },
      { href: "/awards", label: "Danh hiệu", icon: Award, permission: "admin.panel:access" },
    ],
  },
];

export function AdminSidebar() {
  const pathname = usePathname();
  const { user, logout } = useAuth();

  const sections = NAV.map((section) => ({
    ...section,
    items: section.items.filter((item) => can(user, item.permission)),
  })).filter((section) => section.items.length > 0);

  return (
    <aside className="flex w-64 shrink-0 flex-col border-r border-border bg-card">
      <div className="border-b border-border px-5 py-5">
        <Link href="/dashboard" className="flex items-center gap-3">
          <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary text-sm font-bold text-primary-foreground">
            FX
          </span>
          <div>
            <p className="text-sm font-semibold text-foreground">FUExam Admin</p>
            <p className="text-xs text-muted-foreground">Bảng điều khiển</p>
          </div>
        </Link>
      </div>

      <nav className="flex-1 space-y-4 overflow-y-auto px-3 py-4">
        {sections.map((section) => (
          <div key={section.title}>
            <p className="px-3 pb-1 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
              {section.title}
            </p>
            <div className="space-y-1">
              {section.items.map((item) => {
                const active = item.exact
                  ? pathname === item.href
                  : pathname.startsWith(item.href);
                const Icon = item.icon;
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    className={cn(
                      "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                      active
                        ? "bg-primary/10 text-primary"
                        : "text-muted-foreground hover:bg-accent hover:text-accent-foreground",
                    )}
                  >
                    <Icon className="h-4 w-4" />
                    {item.label}
                  </Link>
                );
              })}
            </div>
          </div>
        ))}
      </nav>

      <div className="border-t border-border p-4">
        {user && (
          <div className="mb-3 rounded-lg bg-muted px-3 py-2">
            <p className="truncate text-sm font-medium text-foreground">{user.displayName}</p>
            <p className="truncate text-xs text-muted-foreground">@{user.username}</p>
            {staffRoleLabel(user) && (
              <p className="mt-1 text-xs text-primary">{staffRoleLabel(user)}</p>
            )}
            {isSuperAdmin(user) && (
              <span className="mt-2 inline-block rounded bg-primary/15 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-primary">
                SUPER_ADMIN
              </span>
            )}
          </div>
        )}
        <button
          type="button"
          onClick={() => logout()}
          className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground"
        >
          <LogOut className="h-4 w-4" />
          Đăng xuất
        </button>
      </div>
    </aside>
  );
}
