"use client";

import Link from "next/link";
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
      const next = encodeURIComponent(pathname);
      router.replace(`/login?next=${next}`);
      return;
    }
    if (!hasStaffAccess(user)) {
      router.replace("/admin/forbidden");
    }
  }, [user, loading, router, pathname]);

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-950 text-slate-300">
        <p className="text-sm">Đang tải bảng điều khiển...</p>
      </div>
    );
  }

  if (!user || !hasStaffAccess(user)) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-950 text-slate-300">
        <p className="text-sm">Đang kiểm tra quyền truy cập...</p>
      </div>
    );
  }

  return <>{children}</>;
}

export function AdminForbidden() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-slate-950 px-4 text-center">
      <p className="text-sm font-medium uppercase tracking-wider text-amber-400">
        Truy cập bị từ chối
      </p>
      <h1 className="mt-2 text-2xl font-bold text-white">Bạn không có quyền quản trị</h1>
      <p className="mt-2 max-w-md text-sm text-slate-400">
        Khu vực này yêu cầu quyền <code className="text-amber-300">admin.panel:access</code>. Liên hệ
        quản trị viên nếu bạn cần quyền truy cập.
      </p>
      <div className="mt-8 flex gap-3">
        <Link href="/" className="btn-secondary">
          Về diễn đàn
        </Link>
        <Link href="/login?next=%2Fadmin" className="btn-primary">
          Đăng nhập tài khoản khác
        </Link>
      </div>
    </div>
  );
}
