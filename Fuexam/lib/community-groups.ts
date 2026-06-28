export type CommunityGroup = {
  name: string;
  desc: string;
  href: string;
};

/** Single source of truth for Facebook community groups (home page + popup). */
export const COMMUNITY_GROUPS: CommunityGroup[] = [
  {
    name: "Cộng đồng Fuexam",
    desc: "Nhận thông báo, hỏi đáp nhanh và cập nhật Source mới.",
    href: "https://www.facebook.com/groups/976684067613564",
  },
  {
    name: "Hỗ trợ học tập FPT",
    desc: "Trao đổi môn học, tài liệu và kinh nghiệm qua môn.",
    href: "https://www.facebook.com/groups/720202890383681",
  },
  {
    name: "FuOverflow Community",
    desc: "Cộng đồng trao đổi tài liệu, đề thi và kinh nghiệm học tập.",
    href: "https://www.facebook.com/groups/1729976124824445",
  },
];
