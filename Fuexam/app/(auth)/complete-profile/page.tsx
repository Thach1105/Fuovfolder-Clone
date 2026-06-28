"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { CompleteProfileForm } from "@/components/auth/complete-profile-form";
import { isPendingProfileStatus } from "@/lib/auth/pending-profile";
import { useAuth } from "@/lib/auth/AuthProvider";

export default function CompleteProfilePage() {
  const router = useRouter();
  const { user, loading } = useAuth();

  useEffect(() => {
    if (loading) return;
    if (!user) {
      router.replace("/login");
      return;
    }
    if (!isPendingProfileStatus(user.status)) {
      router.replace("/suoc");
    }
  }, [loading, router, user]);

  if (loading) {
    return <div className="app-skeleton h-96 w-full max-w-lg rounded-2xl" />;
  }
  if (!user || !isPendingProfileStatus(user.status)) return null;

  return (
    <div className="w-full max-w-lg">
      <div className="mb-8 space-y-2 text-center">
        <p className="app-eyebrow">Hoàn tất tài khoản</p>
        <h1 className="font-display text-4xl leading-tight">
          Hoàn tất hồ sơ
        </h1>
        <p className="text-sm text-muted-foreground">
          Bổ sung thông tin còn thiếu để bắt đầu sử dụng cộng đồng.
        </p>
      </div>
      <CompleteProfileForm />
    </div>
  );
}
