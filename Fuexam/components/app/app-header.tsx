"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Search } from "lucide-react";
import { useEffect, useState } from "react";
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
import { hasStaffAccess } from "@/lib/auth/roles";
import { cn } from "@/lib/utils";
import { HeaderBalance } from "@/components/app/header-balance";
import { HeaderNotificationBell } from "@/components/app/header-notification-bell";
import { resolveMediaUrl } from "@/lib/api/media";

const NAV_ITEMS = [
  { href: "/suoc", label: "Source" },
  { href: "/forums", label: "Diễn đàn" },
  { href: "/coursera", label: "Coursera" },
  { href: "/membership", label: "Membership" },
];

export function AppHeader() {
  const pathname = usePathname();
  const { user, loading, logout } = useAuth();
  const [scrolled, setScrolled] = useState(false);
  const [logoutOpen, setLogoutOpen] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 12);
    onScroll();
    window.addEventListener("scroll", onScroll);
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  async function handleLogout() {
    setLoggingOut(true);
    try {
      await logout();
      setLogoutOpen(false);
    } finally {
      setLoggingOut(false);
    }
  }

  return (
    <>
      <header className="sticky top-0 z-50 px-4 pt-4">
        <nav
          className={cn(
            "mx-auto flex h-14 max-w-[1200px] items-center gap-6 rounded-2xl border px-4 transition-all duration-500 lg:px-6",
            scrolled
              ? "border-foreground/10 bg-background/80 shadow-lg backdrop-blur-xl"
              : "border-transparent bg-background/40 backdrop-blur-md",
          )}
        >
          <Link href="/" className="group flex shrink-0 items-center gap-2">
            <span className="font-display text-xl tracking-tight">Fuexam</span>
            <span className="mt-1 font-mono text-[10px] text-muted-foreground">FPT</span>
          </Link>

          <div className="hidden items-center gap-1 md:flex">
            {NAV_ITEMS.map((item) => {
              const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={cn(
                    "rounded-full px-3.5 py-1.5 text-sm transition-colors",
                    active
                      ? "bg-foreground text-background"
                      : "text-foreground/70 hover:bg-foreground/5 hover:text-foreground",
                  )}
                >
                  {item.label}
                </Link>
              );
            })}
          </div>

          <div className="ml-auto flex items-center gap-2">
            <Link
              href="/search"
              aria-label="Tìm kiếm"
              className="rounded-full p-2 text-foreground/60 transition hover:bg-foreground/5 hover:text-foreground"
            >
              <Search className="h-4 w-4" />
            </Link>

            {loading ? (
              <span className="app-skeleton h-8 w-24 rounded-full" />
            ) : user ? (
              <>
                <HeaderBalance />
                <HeaderNotificationBell />
                {hasStaffAccess(user) && (
                  <Link
                    href="/admin"
                    className="hidden rounded-full border border-foreground/15 px-3 py-1.5 text-sm text-foreground/80 transition hover:bg-foreground/5 sm:inline-flex"
                  >
                    Quản trị
                  </Link>
                )}
                <Link
                  href="/profile"
                  className="flex items-center gap-2 rounded-full py-1 pl-1 pr-2 transition hover:bg-foreground/5"
                >
                  {(() => {
                    const avatar = resolveMediaUrl(user.avatarUrl);
                    return avatar ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img
                        src={avatar}
                        alt={user.displayName}
                        className="h-8 w-8 rounded-full object-cover"
                      />
                    ) : (
                      <span className="flex h-8 w-8 items-center justify-center rounded-full bg-foreground text-sm font-semibold text-background">
                        {user.displayName.charAt(0).toUpperCase()}
                      </span>
                    );
                  })()}
                  <span className="hidden text-sm font-medium sm:inline">{user.displayName}</span>
                </Link>
                <Button
                  variant="ghost"
                  size="sm"
                  className="hidden rounded-full sm:inline-flex"
                  onClick={() => setLogoutOpen(true)}
                >
                  Đăng xuất
                </Button>
              </>
            ) : (
              <>
                <Link
                  href="/login"
                  className="hidden text-sm text-foreground/70 transition hover:text-foreground sm:inline"
                >
                  Đăng nhập
                </Link>
                <Button asChild size="sm" className="rounded-full bg-foreground px-4 text-background hover:bg-foreground/90">
                  <Link href="/register">Tạo tài khoản</Link>
                </Button>
              </>
            )}
          </div>
        </nav>
      </header>

      <AlertDialog open={logoutOpen} onOpenChange={setLogoutOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Đăng xuất?</AlertDialogTitle>
            <AlertDialogDescription>
              Bạn sẽ cần đăng nhập lại để mua Source, xem membership và nhận thông báo cá nhân.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Hủy</AlertDialogCancel>
            <AlertDialogAction onClick={handleLogout} disabled={loggingOut}>
              {loggingOut ? "Đang đăng xuất..." : "Đăng xuất"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
