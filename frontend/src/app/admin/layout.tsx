import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "FuOverflow Admin",
  description: "Bảng điều khiển quản trị FuOverflow",
};

export default function AdminRootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return children;
}
