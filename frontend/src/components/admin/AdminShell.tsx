"use client";

import { AdminGuard } from "@/components/admin/AdminGuard";
import { AdminSidebar } from "@/components/admin/AdminSidebar";

export function AdminShell({
  title,
  description,
  children,
}: {
  title: string;
  description?: string;
  children: React.ReactNode;
}) {
  return (
    <AdminGuard>
      <div className="flex min-h-screen bg-slate-900">
        <AdminSidebar />
        <div className="flex min-w-0 flex-1 flex-col">
          <header className="border-b border-slate-800 bg-slate-950/80 px-8 py-6 backdrop-blur">
            <h1 className="text-xl font-bold text-white">{title}</h1>
            {description && <p className="mt-1 text-sm text-slate-400">{description}</p>}
          </header>
          <div className="flex-1 overflow-auto p-8">{children}</div>
        </div>
      </div>
    </AdminGuard>
  );
}
