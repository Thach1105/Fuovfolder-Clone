export function formatPoints(n: number) {
  return `${n.toLocaleString("vi-VN")} Fuexam Point`;
}

export function formatPointsShort(n: number) {
  return `${n.toLocaleString("vi-VN")} Fuexam`;
}

export { REQUEST_STATUS_LABELS } from "@/lib/constants/status-labels";
