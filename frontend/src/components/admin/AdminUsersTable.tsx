"use client";

import type { AdminUserSummary } from "@/types/api";

const STATUS_LABELS: Record<string, string> = {
  ACTIVE: "Hoạt động",
  PENDING_EMAIL_VERIFICATION: "Chờ xác minh email",
  DISABLED: "Vô hiệu",
  DELETED: "Đã xóa",
};

function formatDate(iso: string | null) {
  if (!iso) {
    return "—";
  }
  return new Date(iso).toLocaleString("vi-VN", {
    dateStyle: "short",
    timeStyle: "short",
  });
}

function RoleBadges({ roles }: { roles: string[] }) {
  return (
    <div className="flex flex-wrap gap-1">
      {roles.map((role) => (
        <span
          key={role}
          className={
            role === "ADMIN"
              ? "rounded bg-amber-500/20 px-1.5 py-0.5 text-[10px] font-semibold text-amber-300"
              : role === "SUB_ADMIN"
                ? "rounded bg-violet-500/20 px-1.5 py-0.5 text-[10px] font-semibold text-violet-300"
                : "rounded bg-slate-700 px-1.5 py-0.5 text-[10px] text-slate-300"
          }
        >
          {role}
        </span>
      ))}
    </div>
  );
}

export function AdminUsersTable({ users }: { users: AdminUserSummary[] }) {
  if (users.length === 0) {
    return (
      <p className="rounded-xl border border-slate-800 bg-slate-950 px-4 py-8 text-center text-sm text-slate-500">
        Chưa có người dùng nào.
      </p>
    );
  }

  return (
    <div className="overflow-hidden rounded-xl border border-slate-800">
      <table className="w-full text-left text-sm">
        <thead className="bg-slate-950 text-xs uppercase tracking-wide text-slate-500">
          <tr>
            <th className="px-4 py-3 font-medium">Người dùng</th>
            <th className="px-4 py-3 font-medium">Vai trò</th>
            <th className="px-4 py-3 font-medium">Trạng thái</th>
            <th className="px-4 py-3 font-medium">Đăng nhập cuối</th>
            <th className="px-4 py-3 font-medium">Tạo lúc</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-800 bg-slate-950/50">
          {users.map((user) => (
            <tr key={user.id} className="hover:bg-slate-900/80">
              <td className="px-4 py-3">
                <p className="font-medium text-slate-200">{user.displayName}</p>
                <p className="text-xs text-slate-500">@{user.username}</p>
                <p className="text-xs text-slate-500">{user.email}</p>
              </td>
              <td className="px-4 py-3">
                <RoleBadges roles={user.roles} />
              </td>
              <td className="px-4 py-3">
                <span className="text-slate-300">
                  {STATUS_LABELS[user.status] ?? user.status}
                </span>
                {!user.emailVerified && (
                  <p className="mt-0.5 text-xs text-amber-500">Email chưa xác minh</p>
                )}
              </td>
              <td className="px-4 py-3 text-slate-400">{formatDate(user.lastLoginAt)}</td>
              <td className="px-4 py-3 text-slate-400">{formatDate(user.createdAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
