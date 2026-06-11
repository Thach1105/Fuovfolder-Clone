import type { ReactNode } from "react";
import Link from "next/link";
import { AppBackdrop } from "@/components/app/app-backdrop";

export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="relative flex min-h-screen flex-col noise-overlay">
      <AppBackdrop />
      <header className="px-6 py-6">
        <Link href="/" className="font-display text-xl tracking-tight">
          FUExam
        </Link>
      </header>
      <main className="flex flex-1 items-center justify-center px-4 pb-16">
        {children}
      </main>
    </div>
  );
}