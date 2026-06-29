"use client";

import Link from "next/link";
import { useAuth } from "@/lib/auth/AuthProvider";
import { LoadingState } from "@/components/ui/loading-state";

interface AuthGuardProps {
  children: React.ReactNode;
  fallback?: React.ReactNode;
}

function DefaultLoginPrompt() {
  return (
    <div className="card p-6 text-sm text-slate-600">
      <p>Bạn cần đăng nhập để xem nội dung này.</p>
      <Link
        href="/login"
        className="mt-3 inline-block font-medium text-fuo-600 hover:underline"
      >
        Đăng nhập
      </Link>
    </div>
  );
}

export function AuthGuard({ children, fallback }: AuthGuardProps) {
  const { user, loading } = useAuth();

  if (loading) {
    return <LoadingState message="Đang kiểm tra đăng nhập..." />;
  }

  if (!user) {
    return <>{fallback ?? <DefaultLoginPrompt />}</>;
  }

  return <>{children}</>;
}
