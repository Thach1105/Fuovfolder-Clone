"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { Suspense, useCallback, useEffect, useState } from "react";
import { Loader2 } from "lucide-react";
import { useAuth } from "@/lib/auth/AuthProvider";
import {
  COMPLETE_PROFILE_PATH,
  isPendingProfileStatus,
  isSafeAppPath,
} from "@/lib/auth/pending-profile";
import * as usersApi from "@/lib/api/users";

function CallbackInner() {
  const router = useRouter();
  const { refreshUser } = useAuth();
  const [error, setError] = useState(false);

  const handleCallback = useCallback(async () => {
    try {
      await refreshUser();
      const next = sessionStorage.getItem("oauth_next");
      sessionStorage.removeItem("oauth_next");

      const profile = await usersApi.getCurrentUser();
      const destination = isPendingProfileStatus(profile.status)
        ? COMPLETE_PROFILE_PATH
        : isSafeAppPath(next)
          ? next
          : "/suoc";

      router.replace(destination);
    } catch {
      setError(true);
    }
  }, [refreshUser, router]);

  useEffect(() => {
    queueMicrotask(() => {
      void handleCallback();
    });
  }, [handleCallback]);

  if (error) {
    return (
      <div className="w-full max-w-md text-center">
        <h1 className="font-display text-2xl">Đăng nhập thất bại</h1>
        <p className="mt-2 text-foreground/60">
          Không thể xác thực tài khoản Google. Vui lòng thử lại.
        </p>
        <Link href="/login" className="mt-4 inline-block font-medium text-foreground hover:underline">
          Quay lại đăng nhập
        </Link>
      </div>
    );
  }

  return (
    <div className="flex flex-col items-center gap-4">
      <Loader2 className="size-8 animate-spin text-foreground/40" />
      <p className="text-sm text-foreground/60">Đang xác thực...</p>
    </div>
  );
}

export default function OAuthCallbackPage() {
  return (
    <Suspense fallback={<Loader2 className="size-8 animate-spin text-foreground/40" />}>
      <CallbackInner />
    </Suspense>
  );
}
