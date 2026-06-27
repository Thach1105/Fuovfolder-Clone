import Link from "next/link";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { RoleBadge, StatusBadge } from "@/components/admin/badges";
import { formatDateTime } from "@/lib/format-datetime";
import type { AdminUserSummary } from "@/types/api";

const PRIVILEGED_ROLES = new Set(["SUB_ADMIN", "ADMIN", "SUPER_ADMIN"]);

function canDeleteUser(user: AdminUserSummary, currentUserId: string | null): boolean {
  if (user.id === currentUserId) return false;
  if (user.roles.some((r) => PRIVILEGED_ROLES.has(r))) return false;
  return true;
}

export function AdminUsersTable({
  users,
  canDelete = false,
  currentUserId = null,
  onDelete,
}: {
  users: AdminUserSummary[];
  canDelete?: boolean;
  currentUserId?: string | null;
  onDelete?: (user: AdminUserSummary) => void;
}) {
  if (users.length === 0) {
    return <p className="py-8 text-center text-sm text-muted-foreground">Chưa có người dùng nào.</p>;
  }

  return (
    <div className="rounded-xl border border-border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Người dùng</TableHead>
            <TableHead>Vai trò</TableHead>
            <TableHead>Trạng thái</TableHead>
            <TableHead>Đăng nhập cuối</TableHead>
            <TableHead>Tạo lúc</TableHead>
            <TableHead className="text-right">Hành động</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {users.map((user) => (
            <TableRow key={user.id}>
              <TableCell>
                <div className="min-w-0">
                  <p className="font-medium text-foreground">{user.displayName}</p>
                  <p className="truncate text-xs text-muted-foreground">@{user.username}</p>
                  <p className="truncate text-xs text-muted-foreground">
                    {user.email}
                    {!user.emailVerified && (
                      <span className="ml-1 text-amber-500">(chưa xác thực)</span>
                    )}
                  </p>
                </div>
              </TableCell>
              <TableCell>
                <div className="flex flex-wrap gap-1">
                  {user.roles.length > 0 ? (
                    user.roles.map((role) => <RoleBadge key={role} role={role} />)
                  ) : (
                    <span className="text-xs text-muted-foreground">—</span>
                  )}
                </div>
              </TableCell>
              <TableCell>
                <StatusBadge status={user.status} />
              </TableCell>
              <TableCell className="text-sm text-muted-foreground">
                {formatDateTime(user.lastLoginAt)}
              </TableCell>
              <TableCell className="text-sm text-muted-foreground">
                {formatDateTime(user.createdAt)}
              </TableCell>
              <TableCell className="space-x-3 text-right">
                <Link
                  href={`/users/${user.id}/permissions`}
                  className="text-sm font-medium text-primary hover:underline"
                >
                  Phân quyền
                </Link>
                {canDelete && canDeleteUser(user, currentUserId) && (
                  <button
                    type="button"
                    className="text-sm font-medium text-destructive hover:underline"
                    onClick={() => onDelete?.(user)}
                  >
                    Xóa
                  </button>
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
