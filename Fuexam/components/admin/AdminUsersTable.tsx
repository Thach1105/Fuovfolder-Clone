"use client";

import Link from "next/link";
import type { AdminUserSummary } from "@/types/api";

import { formatDateShort } from "@/lib/format-datetime";
import { USER_STATUS_LABELS, getRoleClass } from "@/lib/constants/status-labels";

function RoleBadges({ roles }: { roles: string[] }) {
  return (
    <div className="flex flex-wrap gap-1">
      {roles.map((role) => (
        <span key={role} className={getRoleClass(role)}>
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
            <th className="px-4 py-3 font-medium" />
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
                <span className="text-slate-300">{USER_STATUS_LABELS[user.status] ?? user.status}</span>
                {!user.emailVerified && (
                  <p className="mt-0.5 text-xs text-amber-500">Email chưa xác minh</p>
                )}
              </td>
              <td className="px-4 py-3 text-slate-400">{formatDateShort(user.lastLoginAt)}</td>
              <td className="px-4 py-3 text-slate-400">{formatDateShort(user.createdAt)}</td>
              <td className="px-4 py-3">
                <Link
                  href={`/admin/users/${user.id}/permissions`}
                  className="text-xs text-amber-400 hover:underline"
                >
                  Quyền
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
