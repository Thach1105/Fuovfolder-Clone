import type { MockContributor, MockThread } from "@/types/api";

export const MOCK_THREADS: MockThread[] = [
  {
    id: 1,
    tag: "Hỏi Đáp",
    title: "Đồ án theo chuyên ngành hẹp IC design",
    forum: "Hỏi Đáp",
    replies: 12,
    views: 340,
    lastActivity: "Hôm qua, lúc 18:34",
    author: "namhaylamne",
    authorInitial: "N",
  },
  {
    id: 2,
    tag: "Hỏi Đáp",
    title: "CAA201 - Final exam tips",
    forum: "CAA201",
    replies: 8,
    views: 521,
    lastActivity: "Thứ bảy lúc 10:10",
    author: "minthep",
    authorInitial: "M",
  },
  {
    id: 3,
    tag: "Confession",
    title: "Confession về kỳ thi cuối kỳ",
    forum: "Confession Trường F",
    replies: 45,
    views: 2100,
    lastActivity: "2 giờ trước",
    author: "meiying",
    authorInitial: "M",
  },
  {
    id: 4,
    tag: "Hỏi Đáp",
    title: "PRO192 - OOP assignment help",
    forum: "PRO192",
    replies: 3,
    views: 89,
    lastActivity: "Hôm nay, lúc 09:15",
    author: "vu_binh",
    authorInitial: "V",
  },
  {
    id: 5,
    tag: "Thảo luận",
    title: "Game Hacking - ethical discussion",
    forum: "Game Hacking",
    replies: 22,
    views: 890,
    lastActivity: "3 ngày trước",
    author: "misa",
    authorInitial: "M",
  },
];

export const MOCK_CONTRIBUTORS: MockContributor[] = [
  { rank: 1, username: "Misa", score: 11500, initial: "M" },
  { rank: 2, username: "Minthep", score: 9800, initial: "M" },
  { rank: 3, username: "Meiying", score: 8200, initial: "M" },
  { rank: 4, username: "namhaylamne", score: 6100, initial: "N" },
  { rank: 5, username: "vu_binh", score: 4500, initial: "V" },
];

export type ThreadTab = "discussion" | "confession" | "popular";

export function filterThreadsByTab(
  threads: MockThread[],
  tab: ThreadTab,
): MockThread[] {
  switch (tab) {
    case "confession":
      return threads.filter((t) => t.tag === "Confession");
    case "popular":
      return [...threads].sort((a, b) => b.views - a.views);
    default:
      return threads.filter((t) => t.tag !== "Confession");
  }
}
