export type ThreadType = "discussion" | "article" | "poll" | "question";

export const THREAD_TYPES: { id: ThreadType; label: string }[] = [
  { id: "discussion", label: "Thảo luận" },
  { id: "article", label: "Article" },
  { id: "poll", label: "Bình chọn" },
  { id: "question", label: "Question" },
];

export function threadTypeLabel(type: string | null | undefined): string {
  return THREAD_TYPES.find((item) => item.id === type)?.label ?? type ?? "Thảo luận";
}

export const CAMPUS_OPTIONS = [
  { value: "", label: "Tài liệu này thuộc cơ sở FPT nào" },
  { value: "hn", label: "Hà Nội" },
  { value: "hcm", label: "Hồ Chí Minh" },
  { value: "dn", label: "Đà Nẵng" },
  { value: "ct", label: "Cần Thơ" },
  { value: "qn", label: "Quy Nhơn" },
];

export const SEMESTER_OPTIONS = [
  { value: "", label: "Tài liệu này thuộc học kỳ nào" },
  { value: "ky-0", label: "Kỳ 0" },
  { value: "ky-1", label: "Kỳ 1" },
  { value: "ky-2", label: "Kỳ 2" },
  { value: "ky-3", label: "Kỳ 3" },
  { value: "ky-4", label: "Kỳ 4" },
  { value: "ky-5", label: "Kỳ 5" },
  { value: "ky-6", label: "Kỳ 6" },
  { value: "ky-7", label: "Kỳ 7" },
  { value: "ky-8", label: "Kỳ 8" },
  { value: "ky-9", label: "Kỳ 9" },
  { value: "tong-hop", label: "Tổng hợp - Chưa rõ kỳ" },
];

export const MATERIAL_TYPE_OPTIONS = [
  { value: "", label: "Phân loại tài liệu" },
  { value: "de-thi", label: "Đề thi" },
  { value: "giao-trinh", label: "Giáo trình" },
  { value: "slide", label: "Slide" },
  { value: "code-mau", label: "Code mẫu" },
  { value: "bai-tap", label: "Bài tập" },
  { value: "khac", label: "Khác" },
];
