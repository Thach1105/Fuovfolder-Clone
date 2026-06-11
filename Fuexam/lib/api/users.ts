import { apiFetch } from "@/lib/api/client";
import type {
  UpdateUserProfileRequest,
  UserProfileResponse,
} from "@/types/api";

export function getCurrentUser() {
  return apiFetch<UserProfileResponse>("/api/v1/users/me");
}

export function updateProfile(data: UpdateUserProfileRequest) {
  return apiFetch<UserProfileResponse>("/api/v1/users/me/profile", {
    method: "PATCH",
    body: JSON.stringify(data),
  });
}
