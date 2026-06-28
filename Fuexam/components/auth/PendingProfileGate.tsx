"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/AuthProvider";
import {
  COMPLETE_PROFILE_PATH,
  isPendingProfileStatus,
} from "@/lib/auth/pending-profile";

const EXEMPT_PATHS = new Set([
  COMPLETE_PROFILE_PATH,
  "/callback",
  "/error",
  "/login",
  "/register",
  "/forgot-password",
  "/reset-password",
  "/verify-email",
]);

function isExemptPath(pathname: string) {
  return EXEMPT_PATHS.has(pathname);
}

export function PendingProfileGate() {
  const pathname = usePathname();
  const router = useRouter();
  const { user, loading } = useAuth();

  useEffect(() => {
    if (loading || !pathname || !user) return;
    if (!isPendingProfileStatus(user.status)) return;
    if (isExemptPath(pathname)) return;
    router.replace(COMPLETE_PROFILE_PATH);
  }, [loading, pathname, router, user]);

  return null;
}
