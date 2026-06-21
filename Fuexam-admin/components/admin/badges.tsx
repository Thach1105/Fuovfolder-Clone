import { cn } from "@/lib/utils";

const ROLE_COLOR: Record<string, string> = {
  SUPER_ADMIN: "bg-red-500/15 text-red-500",
  ADMIN: "bg-amber-500/15 text-amber-500",
  SUB_ADMIN: "bg-violet-500/15 text-violet-500",
};

export function RoleBadge({ role }: { role: string }) {
  const color =
    ROLE_COLOR[role] ??
    (role.startsWith("FUO_")
      ? "bg-emerald-500/15 text-emerald-500"
      : "bg-muted text-muted-foreground");
  return (
    <span className={cn("inline-block rounded-full px-2 py-0.5 text-xs font-medium", color)}>
      {role}
    </span>
  );
}

const STATUS: Record<string, { label: string; color: string }> = {
  ACTIVE: { label: "Hoạt động", color: "bg-emerald-500/15 text-emerald-500" },
  PENDING_EMAIL_VERIFICATION: { label: "Chờ xác thực", color: "bg-amber-500/15 text-amber-500" },
  DISABLED: { label: "Vô hiệu hóa", color: "bg-red-500/15 text-red-500" },
  DELETED: { label: "Đã xóa", color: "bg-muted text-muted-foreground" },
};

export function StatusBadge({ status }: { status: string }) {
  const s = STATUS[status] ?? { label: status, color: "bg-muted text-muted-foreground" };
  return (
    <span className={cn("inline-block rounded-full px-2 py-0.5 text-xs font-medium", s.color)}>
      {s.label}
    </span>
  );
}
