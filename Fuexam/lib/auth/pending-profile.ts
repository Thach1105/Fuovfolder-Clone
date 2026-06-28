export const PENDING_PROFILE_STATUS = "PENDING_PROFILE";
export const COMPLETE_PROFILE_PATH = "/complete-profile";

export function isPendingProfileStatus(
  status: string | null | undefined,
): boolean {
  return status === PENDING_PROFILE_STATUS;
}

export function isSafeAppPath(path: string | null | undefined): path is string {
  return Boolean(path && path.startsWith("/") && !path.startsWith("//"));
}
