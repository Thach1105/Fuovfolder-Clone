import type { UserProfileResponse } from "@/types/api";

export const STAFF_ROLES = ["ADMIN", "SUB_ADMIN"] as const;

export type StaffRole = (typeof STAFF_ROLES)[number];

export function hasStaffAccess(roles: string[] | null | undefined): boolean {
  if (!roles?.length) {
    return false;
  }
  return roles.some((role) => STAFF_ROLES.includes(role as StaffRole));
}

export function isAdmin(roles: string[] | null | undefined): boolean {
  return roles?.includes("ADMIN") ?? false;
}

export function staffRoleLabel(user: UserProfileResponse | null): string {
  if (!user) {
    return "";
  }
  if (isAdmin(user.roles)) {
    return "Quản trị viên";
  }
  if (hasStaffAccess(user.roles)) {
    return "Phó quản trị";
  }
  return "";
}
