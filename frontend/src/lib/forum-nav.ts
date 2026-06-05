export interface ForumNavItem {
  href: string;
  label: string;
  icon: ForumNavIcon;
  dividerBefore?: boolean;
}

export type ForumNavIcon =
  | "documents"
  | "exam"
  | "textbook"
  | "search"
  | "user"
  | "eye"
  | "pin"
  | "new"
  | "topics"
  | "watched"
  | "mark-read"
  | "award";

export const FORUM_DROPDOWN_ITEMS: ForumNavItem[] = [
  { href: "/forums/tai-lieu-cac-mon-hoc", label: "Tài liệu", icon: "documents" },
  { href: "/forums/de-thi-cac-ky-truoc", label: "Đề thi các kỳ trước", icon: "exam" },
  {
    href: "/forums/giao-trinh-slide-code-mau",
    label: "Giáo trình - Slide - Code mẫu",
    icon: "textbook",
  },
  { href: "/search", label: "Tìm tài liệu", icon: "search" },
  { href: "/profile", label: "Tài liệu của tôi", icon: "user", dividerBefore: true },
  { href: "/whats-new", label: "Đã xem", icon: "eye" },
  { href: "/watched", label: "Đang theo dõi", icon: "pin" },
  { href: "/whats-new", label: "Bài mới", icon: "new", dividerBefore: true },
  { href: "/search", label: "Tìm chủ đề", icon: "topics" },
  { href: "/profile", label: "Chủ đề của bạn", icon: "user" },
  { href: "/watched", label: "Đã theo dõi", icon: "watched", dividerBefore: true },
  { href: "/search", label: "Tìm trong diễn đàn", icon: "search" },
  { href: "/whats-new", label: "Đánh dấu đã đọc", icon: "mark-read" },
  { href: "/awards", label: "Danh hiệu", icon: "award" },
];

export const FORUM_SUB_NAV_ITEMS: ForumNavItem[] = [
  { href: "/forums/tai-lieu-cac-mon-hoc", label: "Tài liệu", icon: "documents" },
  { href: "/whats-new", label: "Bài mới", icon: "new" },
  { href: "/search", label: "Tìm chủ đề", icon: "topics" },
  { href: "/watched", label: "Đã theo dõi", icon: "watched" },
  { href: "/search", label: "Tìm trong diễn đàn", icon: "search" },
  { href: "/whats-new", label: "Đánh dấu đã đọc", icon: "mark-read" },
  { href: "/awards", label: "Danh hiệu", icon: "award" },
];

export const DOCUMENT_FORUM_SLUGS = [
  "tai-lieu-cac-mon-hoc",
  "de-thi-cac-ky-truoc",
  "giao-trinh-slide-code-mau",
] as const;

export function isForumSectionPath(pathname: string): boolean {
  return (
    pathname === "/" ||
    pathname.startsWith("/forums") ||
    pathname.startsWith("/threads") ||
    pathname.startsWith("/whats-new") ||
    pathname.startsWith("/watched") ||
    pathname.startsWith("/notifications") ||
    pathname.startsWith("/search") ||
    pathname.startsWith("/awards")
  );
}
