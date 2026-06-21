"use client";

import { useAuth } from "@/lib/auth/AuthProvider";
import { Button } from "@/components/ui/button";

export default function ForbiddenPage() {
  const { logout } = useAuth();
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-background px-4 text-center">
      <p className="text-sm font-medium uppercase tracking-wider text-primary">Truy cập bị từ chối</p>
      <h1 className="mt-2 text-2xl font-bold text-foreground">Bạn không có quyền quản trị</h1>
      <p className="mt-2 max-w-md text-sm text-muted-foreground">
        Khu vực này yêu cầu quyền <code className="text-primary">admin.panel:access</code>. Liên hệ
        quản trị viên nếu bạn cần quyền truy cập.
      </p>
      <div className="mt-8">
        <Button variant="outline" onClick={() => logout()}>
          Đăng nhập tài khoản khác
        </Button>
      </div>
    </div>
  );
}
