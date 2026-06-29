export const USER_STATUS_LABELS: Record<string, string> = {
  ACTIVE: "Hoạt động",
  PENDING_EMAIL_VERIFICATION: "Chờ xác minh email",
  DISABLED: "Vô hiệu",
  DELETED: "Đã xóa",
};

export const DEPOSIT_STATUS_CONFIG: Record<string, { label: string; className: string }> = {
  paid: { label: "Đã thanh toán", className: "bg-emerald-500/15 text-emerald-600" },
  pending: { label: "Chờ thanh toán", className: "bg-yellow-500/15 text-yellow-600" },
  expired: { label: "Hết hạn", className: "bg-muted text-muted-foreground" },
  failed: { label: "Thất bại", className: "bg-red-500/15 text-red-600" },
};

export const MEMBERSHIP_PLAN_STATUS_LABELS: Record<string, string> = {
  active: "Đang bán",
  inactive: "Tạm ẩn",
  archived: "Lưu trữ",
};

export const ROLE_CLASS: Record<string, string> = {
  SUPER_ADMIN: "rounded bg-red-500/20 px-1.5 py-0.5 text-[10px] font-semibold text-red-300",
  ADMIN: "rounded bg-amber-500/20 px-1.5 py-0.5 text-[10px] font-semibold text-amber-300",
  SUB_ADMIN: "rounded bg-violet-500/20 px-1.5 py-0.5 text-[10px] font-semibold text-violet-300",
};

export const ROLE_CLASS_DEFAULT = "rounded bg-slate-700 px-1.5 py-0.5 text-[10px] text-slate-300";
export const ROLE_CLASS_FUO = "rounded bg-emerald-500/20 px-1.5 py-0.5 text-[10px] font-semibold text-emerald-300";

export function getRoleClass(role: string): string {
  if (ROLE_CLASS[role]) return ROLE_CLASS[role];
  if (role.startsWith("FUO_")) return ROLE_CLASS_FUO;
  return ROLE_CLASS_DEFAULT;
}

export const REQUEST_STATUS_LABELS: Record<string, string> = {
  pending: "Chờ xử lý",
  in_progress: "Đang thực hiện",
  completed: "Hoàn thành",
  cancelled: "Đã hủy",
};

export const CATALOG_ACTIVE_LABELS: Record<string, string> = {
  true: "Đang bán",
  false: "Ẩn",
};

export function toFilterOptions(
  labels: Record<string, string>,
  allLabel = "Tất cả",
): { value: string; label: string }[] {
  return [
    { value: "", label: allLabel },
    ...Object.entries(labels).map(([value, label]) => ({ value, label })),
  ];
}

export const SOURCE_PURCHASE_STATUS_LABELS: Record<string, string> = {
  active: "Còn hạn",
  expired: "Hết hạn",
  refunded: "Đã hoàn",
  cancelled: "Đã hủy",
};

export function toSelectOptions(
  labels: Record<string, string>,
): { value: string; label: string }[] {
  return Object.entries(labels).map(([value, label]) => ({ value, label }));
}
