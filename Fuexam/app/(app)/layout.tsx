import type { ReactNode } from "react";
import { AppHeader } from "@/components/app/app-header";
import { AppBackdrop } from "@/components/app/app-backdrop";
import { JoinGroupPopup } from "@/components/app/join-group-popup";
import { AnnouncementBar } from "@/components/app/announcement-bar";

export default function AppLayout({ children }: { children: ReactNode }) {
  return (
    <div className="relative min-h-screen noise-overlay">
      <AppBackdrop />
      <AnnouncementBar />
      <AppHeader />
      <main className="mx-auto max-w-[1200px] px-4 py-8 lg:px-6">{children}</main>
      <JoinGroupPopup />
    </div>
  );
}