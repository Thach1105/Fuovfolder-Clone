import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "FUExam Admin",
  description: "Bảng điều khiển quản trị FUExam",
};

export default function AdminRootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return children;
}
