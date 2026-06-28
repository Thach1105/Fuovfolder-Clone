export type CommunityGroup = {
  name: string;
  desc: string;
  href: string;
};

/** Single source of truth for Facebook community groups (home page + popup). */
export const COMMUNITY_GROUPS: CommunityGroup[] = [
  {
    name: "Fuexam",
    desc: "Nhận thông báo, hỏi đáp nhanh và cập nhật Source mới.",
    href: "https://www.facebook.com/groups/976684067613564",
  },
  {
    name: "Fuexam 2",
    desc: "Trao đổi môn học, tài liệu và kinh nghiệm qua môn.",
    href: "https://www.facebook.com/groups/720202890383681",
  },
  {
    name: "Hỗ Trợ Code",
    desc: "Cộng đồng hỗ trợ lập trình, bài tập và đồ án.",
    href: "https://www.facebook.com/groups/1729976124824445",
  },
];
