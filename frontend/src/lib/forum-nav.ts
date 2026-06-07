export interface ForumNavItem {
  href: string;
  label: string;
  icon: ForumNavIcon;
  dividerBefore?: boolean;
  children?: ForumNavItem[];
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

export const DOCUMENT_HUB_SLUG = "tai-lieu";

export const DOCUMENT_CHILD_FORUMS = [
  {
    slug: "de-thi-cac-ky-truoc",
    label: "Đề thi các kỳ trước",
    icon: "exam" as const,
  },
  {
    slug: "giao-trinh-slide-code-mau",
    label: "Giáo trình - Slide - Code mẫu",
    icon: "textbook" as const,
  },
] as const;

export const DOCUMENT_FORUM_SLUGS = DOCUMENT_CHILD_FORUMS.map((forum) => forum.slug);

export const LEGACY_DOCUMENT_HUB_SLUG = "tai-lieu-cac-mon-hoc";

export const FORUM_DROPDOWN_ITEMS: ForumNavItem[] = [
  {
    href: `/forums/${DOCUMENT_HUB_SLUG}`,
    label: "Tài liệu",
    icon: "documents",
    children: DOCUMENT_CHILD_FORUMS.map((forum) => ({
      href: `/forums/${forum.slug}`,
      label: forum.label,
      icon: forum.icon,
    })),
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
  { href: `/forums/${DOCUMENT_HUB_SLUG}`, label: "Tài liệu", icon: "documents" },
  { href: "/whats-new", label: "Bài mới", icon: "new" },
  { href: "/search", label: "Tìm chủ đề", icon: "topics" },
  { href: "/watched", label: "Đã theo dõi", icon: "watched" },
  { href: "/search", label: "Tìm trong diễn đàn", icon: "search" },
  { href: "/whats-new", label: "Đánh dấu đã đọc", icon: "mark-read" },
  { href: "/awards", label: "Danh hiệu", icon: "award" },
];

export function isDocumentHubSlug(slug: string): boolean {
  return slug === DOCUMENT_HUB_SLUG;
}

export function isDocumentChildForumSlug(slug: string): boolean {
  return (DOCUMENT_FORUM_SLUGS as readonly string[]).includes(slug);
}

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
