"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { AdminShell } from "@/components/admin/AdminShell";
import { StatCard } from "@/components/admin/StatCard";
import { Card, CardContent } from "@/components/ui/card";
import { ApiError } from "@/lib/api/client";
import { getAdminOverview } from "@/lib/api/admin";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";
import type { AdminOverviewResponse } from "@/types/api";

const QUICK_LINKS: { href: string; label: string; description: string; permission: string }[] = [
  { href: "/users", label: "Người dùng", description: "Quản lý tài khoản & vai trò", permission: "admin.user:read" },
  { href: "/rbac/roles", label: "Phân quyền", description: "Vai trò & quyền hệ thống", permission: "rbac.role:read" },
  { href: "/source/catalog", label: "Source", description: "Tài liệu ôn thi", permission: "source.catalog.admin:read" },
  { href: "/coursera/orders", label: "Coursera", description: "Đơn dịch vụ", permission: "coursera.request.admin:read" },
  { href: "/membership/plans", label: "Membership", description: "Gói thành viên", permission: "membership.admin:read" },
  { href: "/moderation/flags", label: "Kiểm duyệt", description: "Báo cáo & hàng chờ", permission: "forum.moderation:read" },
];

export default function DashboardPage() {
  const { user } = useAuth();
  const [data, setData] = useState<AdminOverviewResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getAdminOverview()
      .then(setData)
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : "Không tải được số liệu tổng quan."),
      )
      .finally(() => setLoading(false));
  }, []);

  const links = QUICK_LINKS.filter((l) => can(user, l.permission));

  return (
    <AdminShell title="Tổng quan" description="Thống kê nhanh toàn hệ thống">
      {error && (
        <div className="mb-6 rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      )}

      {loading && !data && <p className="text-sm text-muted-foreground">Đang tải...</p>}

      {data && (
        <>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard label="Tổng người dùng" value={data.totalUsers.toLocaleString("vi-VN")} />
            <StatCard label="Đang hoạt động" value={data.activeUsers.toLocaleString("vi-VN")} accent="success" />
            <StatCard
              label="Chờ xác thực"
              value={data.pendingVerificationUsers.toLocaleString("vi-VN")}
              accent="warning"
            />
            <StatCard label="Bị vô hiệu hóa" value={data.disabledUsers.toLocaleString("vi-VN")} accent="danger" />
            <StatCard label="Super Admin" value={data.superAdminUsers} />
            <StatCard label="Admin" value={data.adminUsers} />
            <StatCard label="Sub Admin" value={data.subAdminUsers} />
          </div>

          {links.length > 0 && (
            <>
              <h2 className="mb-3 mt-8 text-sm font-semibold uppercase tracking-wider text-muted-foreground">
                Truy cập nhanh
              </h2>
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {links.map((link) => (
                  <Link key={link.href} href={link.href}>
                    <Card className="transition-colors hover:border-primary/50 hover:bg-accent/50">
                      <CardContent className="p-5">
                        <p className="font-semibold text-foreground">{link.label}</p>
                        <p className="mt-1 text-sm text-muted-foreground">{link.description}</p>
                      </CardContent>
                    </Card>
                  </Link>
                ))}
              </div>
            </>
          )}
        </>
      )}
    </AdminShell>
  );
}
