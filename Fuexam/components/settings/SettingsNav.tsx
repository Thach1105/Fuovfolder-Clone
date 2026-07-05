"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const tabs = [
  { href: "/settings/profile", label: "Hồ sơ" },
  { href: "/settings/security", label: "Bảo mật" },
  { href: "/settings/devices", label: "Thiết bị" },
  { href: "/settings/notifications", label: "Thông báo" },
];

export function SettingsNav() {
  const pathname = usePathname();

  return (
    <nav className="mb-6 flex gap-1 border-b border-slate-200">
      {tabs.map((tab) => {
        const active = pathname === tab.href;
        return (
          <Link
            key={tab.href}
            href={tab.href}
            className={`px-4 py-2 text-sm font-medium transition ${
              active
                ? "border-b-2 border-fuo-600 text-fuo-600"
                : "text-slate-500 hover:text-slate-900"
            }`}
          >
            {tab.label}
          </Link>
        );
      })}
    </nav>
  );
}
