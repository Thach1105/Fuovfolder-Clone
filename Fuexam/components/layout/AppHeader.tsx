"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "@/lib/auth/AuthProvider";
import { hasStaffAccess } from "@/lib/auth/roles";
import { cn } from "@/lib/utils";
import { ForumNavDropdown } from "@/components/layout/ForumNavDropdown";
import { NotificationBell } from "@/components/layout/NotificationBell";
import { PointsBalanceBadge } from "@/components/layout/PointsBalanceBadge";

const NAV_ITEMS = [
  { href: "/membership", label: "Membership" },
  { href: "/coursera", label: "Coursera" },
  { href: "/suoc", label: "Source" },
  { href: "/exam", label: "Thi FE/PE" },
  { href: "/course", label: "Khoá học" },
];

export function AppHeader() {
  const pathname = usePathname();
  const { user, loading, logout } = useAuth();

  return (
    <header className="sticky top-0 z-50 border-b border-ink-200/70 bg-ink-50/80 backdrop-blur-xl">
      <div className="mx-auto flex h-16 max-w-7xl items-center gap-6 px-4">
        <Link href="/" className="group flex shrink-0 items-center gap-2.5">
          <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-ink-900 text-sm font-bold text-ink-50 transition-transform duration-300 group-hover:-rotate-6">
            Fuexam
          </span>
          <span className="hidden font-display text-xl text-ink-900 sm:inline">
            Fuexam
          </span>
        </Link>

        <nav className="hidden items-center gap-1 md:flex">
          <ForumNavDropdown />
          {NAV_ITEMS.map((item) => {
            const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "rounded-full px-3.5 py-1.5 text-sm font-medium transition-colors",
                  active
                    ? "bg-ink-900 text-ink-50"
                    : "text-ink-600 hover:bg-ink-100 hover:text-ink-900",
                )}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>

        <div className="ml-auto flex items-center gap-2">
          <Link
            href="/search"
            className="rounded-full p-2 text-ink-500 transition hover:bg-ink-100 hover:text-ink-800"
            aria-label="Tìm kiếm"
          >
            <SearchIcon />
          </Link>

          {loading ? (
            <span className="skeleton h-8 w-20 rounded-full" />
          ) : user ? (
            <>
              <NotificationBell />
              <PointsBalanceBadge />
              {hasStaffAccess(user) && (
                <Link
                  href="/admin"
                  className="hidden rounded-full border border-amber-200 bg-amber-50 px-3.5 py-1.5 text-sm font-medium text-amber-800 transition hover:bg-amber-100 sm:inline-flex"
                >
                  Quản trị
                </Link>
              )}
              <Link
                href="/profile"
                className="flex items-center gap-2 rounded-full px-1.5 py-1 transition hover:bg-ink-100"
              >
                <span className="flex h-8 w-8 items-center justify-center rounded-full bg-fuo-100 text-sm font-semibold text-fuo-700">
                  {user.displayName.charAt(0).toUpperCase()}
                </span>
                <span className="hidden text-sm font-medium text-ink-700 sm:inline">
                  {user.displayName}
                </span>
              </Link>
              <button
                type="button"
                onClick={() => logout()}
                className="btn-secondary hidden sm:inline-flex"
              >
                Đăng xuất
              </button>
            </>
          ) : (
            <>
              <Link href="/login" className="btn-secondary">
                Đăng nhập
              </Link>
              <Link href="/register" className="btn-primary">
                Tạo tài khoản
              </Link>
            </>
          )}
        </div>
      </div>
    </header>
  );
}

function SearchIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <circle cx="11" cy="11" r="8" />
      <path d="m21 21-4.3-4.3" />
    </svg>
  );
}