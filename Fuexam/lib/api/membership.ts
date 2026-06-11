import { apiFetch } from "@/lib/api/client";
import type { MembershipPlanResponse, MembershipStatusResponse } from "@/types/api";

export function listMembershipPlans() {
  return apiFetch<MembershipPlanResponse[]>("/api/v1/membership/plans");
}

export function getMyMembership() {
  return apiFetch<MembershipStatusResponse>("/api/v1/membership/me");
}

export function subscribeMembership(planSlug: string) {
  return apiFetch<MembershipStatusResponse>("/api/v1/membership/subscribe", {
    method: "POST",
    body: JSON.stringify({ planSlug }),
  });
}
