export function formatPoints(n: number) {
  return `${n.toLocaleString("vi-VN")} Fuexam Point`;
}

export const REQUEST_STATUS_LABELS: Record<string, string> = {
  pending: "Chờ xử lý",
  in_progress: "Đang thực hiện",
  completed: "Hoàn thành",
  cancelled: "Đã hủy",
};
