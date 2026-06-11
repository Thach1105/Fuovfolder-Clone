import type { UserProfileResponse } from "@/types/api";

export function can(user: UserProfileResponse | null | undefined, permission: string): boolean {
  if (!user) {
    return false;
  }
  if (user.superAdmin) {
    return true;
  }
  return user.permissions?.includes(permission) ?? false;
}

export function canAny(user: UserProfileResponse | null | undefined, permissions: string[]): boolean {
  return permissions.some((permission) => can(user, permission));
}
