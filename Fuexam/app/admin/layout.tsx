import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Fuexam Admin",
  description: "Bảng điều khiển quản trị Fuexam",
};

export default function AdminRootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return children;
}
