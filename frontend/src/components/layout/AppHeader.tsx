"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "@/lib/auth/AuthProvider";
import { hasStaffAccess } from "@/lib/auth/roles";
import { cn } from "@/lib/utils";
import { PointsBalanceBadge } from "@/components/layout/PointsBalanceBadge";

const NAV_ITEMS = [
  { href: "/", label: "Diễn đàn" },
  { href: "/membership", label: "Membership" },
  { href: "/coursera", label: "Coursera" },
  { href: "/suoc", label: "Suộc" },
  { href: "/course", label: "Khoá học" },
];

export function AppHeader() {
  const pathname = usePathname();
  const { user, loading, logout } = useAuth();

  return (
    <header className="sticky top-0 z-50 border-b border-slate-200 bg-white/95 backdrop-blur">
      <div className="mx-auto flex h-14 max-w-7xl items-center gap-6 px-4">
        <Link href="/" className="flex shrink-0 items-center gap-2">
          <span className="flex h-9 w-9 items-center justify-center rounded-full bg-fuo-600 text-sm font-bold text-white">
            FUO
          </span>
          <span className="hidden font-semibold text-slate-800 sm:inline">
            FuOverflow
          </span>
        </Link>

        <nav className="hidden items-center gap-1 md:flex">
          {NAV_ITEMS.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "rounded-lg px-3 py-1.5 text-sm font-medium transition",
                pathname === item.href
                  ? "bg-fuo-50 text-fuo-700"
                  : "text-slate-600 hover:bg-slate-100 hover:text-slate-900",
              )}
            >
              {item.label}
            </Link>
          ))}
        </nav>

        <div className="ml-auto flex items-center gap-2">
          <Link
            href="/search"
            className="rounded-lg p-2 text-slate-500 transition hover:bg-slate-100 hover:text-slate-700"
            aria-label="Tìm kiếm"
          >
            <SearchIcon />
          </Link>

          {loading ? (
            <span className="h-8 w-20 animate-pulse rounded-lg bg-slate-100" />
          ) : user ? (
            <>
              <PointsBalanceBadge />
              {hasStaffAccess(user.roles) && (
                <Link
                  href="/admin"
                  className="hidden rounded-lg border border-amber-200 bg-amber-50 px-3 py-1.5 text-sm font-medium text-amber-800 transition hover:bg-amber-100 sm:inline-flex"
                >
                  Quản trị
                </Link>
              )}
              <Link
                href="/profile"
                className="flex items-center gap-2 rounded-lg px-2 py-1 transition hover:bg-slate-100"
              >
                <span className="flex h-8 w-8 items-center justify-center rounded-full bg-fuo-100 text-sm font-semibold text-fuo-700">
                  {user.displayName.charAt(0).toUpperCase()}
                </span>
                <span className="hidden text-sm font-medium text-slate-700 sm:inline">
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
