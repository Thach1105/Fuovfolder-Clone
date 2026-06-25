export const FPT_CAMPUSES = [
  "Hà Nội (Hòa Lạc)",
  "TP. Hồ Chí Minh",
  "Đà Nẵng",
  "Cần Thơ",
  "Quy Nhơn (AI Campus)",
] as const;

export type FptCampus = (typeof FPT_CAMPUSES)[number];
