import { API_V1 } from "@/lib/constants/api";
import { apiFetch } from "@/lib/api/client";
import type {
  AdminMembershipPlanResponse,
  MembershipRoleOptionResponse,
} from "@/types/api";

export function listAdminMembershipPlans() {
  return apiFetch<AdminMembershipPlanResponse[]>(`${API_V1}/admin/membership/plans`);
}

export function listMembershipRoleOptions() {
  return apiFetch<MembershipRoleOptionResponse[]>(`${API_V1}/admin/membership/roles`);
}

export function createMembershipPlan(body: {
  slug: string;
  name: string;
  description?: string;
  pricePoints: number;
  billingInterval: string;
  roleSlug: string;
  durationDays: number;
  status: string;
}) {
  return apiFetch<AdminMembershipPlanResponse>(`${API_V1}/admin/membership/plans`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function updateMembershipPlan(
  planId: string,
  body: {
    name: string;
    description?: string;
    pricePoints: number;
    billingInterval: string;
    roleSlug: string;
    durationDays: number;
    status: string;
  },
) {
  return apiFetch<AdminMembershipPlanResponse>(`${API_V1}/admin/membership/plans/${planId}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}
