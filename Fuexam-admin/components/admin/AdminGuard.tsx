"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { useAuth } from "@/lib/auth/AuthProvider";
import { hasStaffAccess } from "@/lib/auth/roles";

export function AdminGuard({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (loading) {
      return;
    }
    if (!user) {
      const search = typeof window !== "undefined" ? window.location.search : "";
      const next = encodeURIComponent(pathname + search);
      router.replace(`/login?next=${next}`);
      return;
    }
    if (!hasStaffAccess(user)) {
      router.replace("/forbidden");
    }
  }, [user, loading, router, pathname]);

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background text-muted-foreground">
        <p className="text-sm">Đang tải bảng điều khiển...</p>
      </div>
    );
  }

  if (!user || !hasStaffAccess(user)) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background text-muted-foreground">
        <p className="text-sm">Đang kiểm tra quyền truy cập...</p>
      </div>
    );
  }

  return <>{children}</>;
}
