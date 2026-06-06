import { apiFetch } from "@/lib/api/client";
import type {
  CreateRoleRequest,
  EffectivePermissions,
  PermissionCatalogResponse,
  RoleDetailResponse,
  RoleSummaryResponse,
  UserPermissionOverrideResponse,
} from "@/types/api";

export function listPermissions() {
  return apiFetch<PermissionCatalogResponse>("/api/v1/admin/rbac/permissions");
}

export function listRoles() {
  return apiFetch<RoleSummaryResponse[]>("/api/v1/admin/rbac/roles");
}

export function getRole(roleId: string) {
  return apiFetch<RoleDetailResponse>(`/api/v1/admin/rbac/roles/${roleId}`);
}

export function createRole(body: CreateRoleRequest) {
  return apiFetch<RoleDetailResponse>("/api/v1/admin/rbac/roles", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function updateRolePermissions(roleId: string, permissions: string[]) {
  return apiFetch<RoleDetailResponse>(`/api/v1/admin/rbac/roles/${roleId}/permissions`, {
    method: "PUT",
    body: JSON.stringify({ permissions }),
  });
}

export function getUserRoles(userId: string) {
  return apiFetch<string[]>(`/api/v1/admin/rbac/users/${userId}/roles`);
}

export function updateUserRoles(userId: string, roleSlugs: string[]) {
  return apiFetch<string[]>(`/api/v1/admin/rbac/users/${userId}/roles`, {
    method: "PUT",
    body: JSON.stringify({ roleSlugs }),
  });
}

export function getUserEffectivePermissions(userId: string) {
  return apiFetch<EffectivePermissions>(`/api/v1/admin/rbac/users/${userId}/permissions`);
}

export function getUserOverrides(userId: string) {
  return apiFetch<UserPermissionOverrideResponse[]>(`/api/v1/admin/rbac/users/${userId}/overrides`);
}

export function updateUserOverrides(
  userId: string,
  overrides: { permissionSlug: string; effect: string; reason?: string }[],
) {
  return apiFetch<UserPermissionOverrideResponse[]>(`/api/v1/admin/rbac/users/${userId}/overrides`, {
    method: "PUT",
    body: JSON.stringify({ overrides }),
  });
}
