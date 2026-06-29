import { z } from "zod";

export const replySchema = z.object({
  body: z.string().min(1, "Nội dung không được để trống"),
});
export type ReplyFormValues = z.infer<typeof replySchema>;

export const createThreadSchema = z.object({
  threadType: z.enum(["discussion", "article", "poll", "question"]),
  title: z.string().min(1, "Không được để trống").max(300, "Tối đa 300 ký tự"),
  body: z.string().min(1, "Nội dung không được để trống"),
  campus: z.string().min(1, "Vui lòng chọn cơ sở"),
  semester: z.string().min(1, "Vui lòng chọn học kỳ"),
  materialType: z.string().min(1, "Vui lòng chọn loại tài liệu"),
  tags: z.string().optional(),
  watchThread: z.boolean(),
});
export type CreateThreadFormValues = z.infer<typeof createThreadSchema>;
