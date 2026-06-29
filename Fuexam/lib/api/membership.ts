import { API_V1 } from "@/lib/constants/api";
import { apiFetch } from "@/lib/api/client";
import type { MembershipPlanResponse, MembershipStatusResponse } from "@/types/api";

export function listMembershipPlans() {
  return apiFetch<MembershipPlanResponse[]>(`${API_V1}/membership/plans`);
}

export function getMyMembership() {
  return apiFetch<MembershipStatusResponse>(`${API_V1}/membership/me`);
}

export function subscribeMembership(planSlug: string) {
  return apiFetch<MembershipStatusResponse>(`${API_V1}/membership/subscribe`, {
    method: "POST",
    body: JSON.stringify({ planSlug }),
  });
}
