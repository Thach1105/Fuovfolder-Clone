import { AppHeader } from "@/components/layout/AppHeader";
import { ForumSubNav } from "@/components/layout/ForumSubNav";

export default function SiteLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <>
      <AppHeader />
      <ForumSubNav />
      <main className="mx-auto max-w-7xl px-4 py-6">{children}</main>
    </>
  );
}
