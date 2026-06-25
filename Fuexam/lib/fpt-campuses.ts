/**
 * Danh sách cơ sở của Đại học FPT, dùng cho ô chọn campus khi đăng ký / cập nhật
 * hồ sơ. Giá trị `value` được lưu thẳng vào cột `users.campus` (varchar) ở backend.
 */
export const FPT_CAMPUSES = [
  "Hà Nội (Hòa Lạc)",
  "TP. Hồ Chí Minh",
  "Đà Nẵng",
  "Cần Thơ",
  "Quy Nhơn (AI Campus)",
] as const;

export type FptCampus = (typeof FPT_CAMPUSES)[number];
