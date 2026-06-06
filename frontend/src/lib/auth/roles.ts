import type { UserProfileResponse } from "@/types/api";

export const STAFF_ROLES = ["SUPER_ADMIN", "ADMIN", "SUB_ADMIN"] as const;

export type StaffRole = (typeof STAFF_ROLES)[number];

export function hasStaffAccess(user: UserProfileResponse | null | undefined): boolean {
  if (!user) {
    return false;
  }
  if (user.superAdmin) {
    return true;
  }
  return user.permissions?.includes("admin.panel:access") ?? hasStaffRole(user.roles);
}

function hasStaffRole(roles: string[] | null | undefined): boolean {
  if (!roles?.length) {
    return false;
  }
  return roles.some((role) => STAFF_ROLES.includes(role as StaffRole));
}

export function isSuperAdmin(user: UserProfileResponse | null | undefined): boolean {
  return user?.superAdmin ?? user?.roles?.includes("SUPER_ADMIN") ?? false;
}

export function isAdmin(user: UserProfileResponse | null | undefined): boolean {
  if (isSuperAdmin(user)) {
    return true;
  }
  return user?.roles?.includes("ADMIN") ?? false;
}

export function canRefundSource(user: UserProfileResponse | null | undefined): boolean {
  if (user?.superAdmin) {
    return true;
  }
  return user?.permissions?.includes("source.purchase.admin:refund") ?? false;
}

export function staffRoleLabel(user: UserProfileResponse | null): string {
  if (!user) {
    return "";
  }
  if (isSuperAdmin(user)) {
    return "Super quản trị";
  }
  if (user.roles?.includes("ADMIN")) {
    return "Quản trị viên";
  }
  if (hasStaffAccess(user)) {
    return "Phó quản trị";
  }
  return "";
}
