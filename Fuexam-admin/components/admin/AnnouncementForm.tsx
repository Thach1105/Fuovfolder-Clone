"use client";

import { useCallback, useState } from "react";
import { useRouter } from "next/navigation";
import { useEditor, EditorContent } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import { Color } from "@tiptap/extension-color";
import { TextStyle } from "@tiptap/extension-text-style";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ApiError } from "@/lib/api/client";
import * as announcementApi from "@/lib/api/admin-announcements";
import { AnnouncementPreview } from "./AnnouncementPreview";
import type { AnnouncementResponse } from "@/types/api";

interface AnnouncementFormProps {
  initial?: AnnouncementResponse;
}

const COLOR_PRESETS = [
  "#ef4444", "#f97316", "#eab308", "#22c55e",
  "#3b82f6", "#8b5cf6", "#ec4899", "#ffffff",
];

export function AnnouncementForm({ initial }: AnnouncementFormProps) {
  const router = useRouter();
  const isEdit = !!initial;

  const [title, setTitle] = useState(initial?.title ?? "");
  const [backgroundColor, setBackgroundColor] = useState(initial?.backgroundColor ?? "#1e40af");
  const [linkUrl, setLinkUrl] = useState(initial?.linkUrl ?? "");
  const [linkLabel, setLinkLabel] = useState(initial?.linkLabel ?? "");
  const [priority, setPriority] = useState(initial?.priority ?? 0);
  const [scrollSpeed, setScrollSpeed] = useState(initial?.scrollSpeed ?? 50);
  const [stepMinutes, setStepMinutes] = useState(
    initial ? Math.round(initial.stepSeconds / 60) : 5,
  );
  const [startAt, setStartAt] = useState(
    initial?.startAt ? initial.startAt.slice(0, 16) : "",
  );
  const [endAt, setEndAt] = useState(
    initial?.endAt ? initial.endAt.slice(0, 16) : "",
  );
  const [submitting, setSubmitting] = useState(false);

  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        heading: false,
        bulletList: false,
        orderedList: false,
        blockquote: false,
        codeBlock: false,
        horizontalRule: false,
      }),
      TextStyle,
      Color,
    ],
    content: initial?.contentHtml ?? "",
    immediatelyRender: false,
  });

  const contentHtml = editor?.getHTML() ?? "";

  const formValid =
    title.trim().length > 0 &&
    contentHtml.trim().length > 0 &&
    contentHtml !== "<p></p>" &&
    startAt.length > 0 &&
    endAt.length > 0 &&
    scrollSpeed >= 10 &&
    stepMinutes >= 1;

  const buildBody = useCallback(() => ({
    title: title.trim(),
    contentHtml,
    backgroundColor,
    linkUrl: linkUrl.trim() || undefined,
    linkLabel: linkLabel.trim() || undefined,
    priority,
    scrollSpeed,
    stepSeconds: stepMinutes * 60,
    startAt: new Date(startAt).toISOString(),
    endAt: new Date(endAt).toISOString(),
  }), [title, contentHtml, backgroundColor, linkUrl, linkLabel, priority, scrollSpeed, stepMinutes, startAt, endAt]);

  const handleSubmit = async (status: string) => {
    if (!formValid) return;
    setSubmitting(true);
    try {
      if (isEdit) {
        await announcementApi.updateAnnouncement(initial.id, buildBody());
        toast.success("Đã cập nhật thông báo.");
      } else {
        await announcementApi.createAnnouncement(buildBody(), status);
        toast.success(
          status === "ACTIVE" ? "Đã kích hoạt thông báo." :
          status === "SCHEDULED" ? "Đã lên lịch thông báo." :
          "Đã lưu nháp.",
        );
      }
      router.push("/announcements");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Lưu thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">
            {isEdit ? `Sửa: ${initial.title}` : "Tạo thông báo mới"}
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="title">Tên nội bộ</Label>
            <Input
              id="title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="VD: Khuyến mãi hè 2026"
              required
            />
          </div>

          <div className="space-y-2">
            <Label>Nội dung thông báo</Label>
            {editor && (
              <div className="rounded-md border border-border">
                <div className="flex items-center gap-1 border-b border-border px-2 py-1">
                  <button
                    type="button"
                    onClick={() => editor.chain().focus().toggleBold().run()}
                    className={`rounded px-2 py-1 text-xs font-bold ${editor.isActive("bold") ? "bg-primary text-primary-foreground" : "hover:bg-muted"}`}
                  >
                    B
                  </button>
                  <button
                    type="button"
                    onClick={() => editor.chain().focus().toggleItalic().run()}
                    className={`rounded px-2 py-1 text-xs italic ${editor.isActive("italic") ? "bg-primary text-primary-foreground" : "hover:bg-muted"}`}
                  >
                    I
                  </button>
                  <div className="mx-1 h-4 w-px bg-border" />
                  {COLOR_PRESETS.map((color) => (
                    <button
                      key={color}
                      type="button"
                      onClick={() => editor.chain().focus().setColor(color).run()}
                      className="h-5 w-5 rounded-full border border-border"
                      style={{ backgroundColor: color }}
                      title={color}
                    />
                  ))}
                  <input
                    type="color"
                    className="h-5 w-5 cursor-pointer"
                    onChange={(e) => editor.chain().focus().setColor(e.target.value).run()}
                    title="Chọn màu khác"
                  />
                </div>
                <EditorContent
                  editor={editor}
                  className="prose prose-sm max-w-none px-3 py-2 text-foreground [&_.ProseMirror]:min-h-[60px] [&_.ProseMirror]:outline-none"
                />
              </div>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="bgColor">Màu nền</Label>
            <div className="flex items-center gap-2">
              <input
                type="color"
                id="bgColor"
                value={backgroundColor}
                onChange={(e) => setBackgroundColor(e.target.value)}
                className="h-8 w-10 cursor-pointer rounded border border-border"
              />
              <Input
                value={backgroundColor}
                onChange={(e) => setBackgroundColor(e.target.value)}
                className="w-28"
                maxLength={9}
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label htmlFor="linkUrl">Link URL (tuỳ chọn)</Label>
              <Input
                id="linkUrl"
                value={linkUrl}
                onChange={(e) => setLinkUrl(e.target.value)}
                placeholder="https://..."
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="linkLabel">Label link</Label>
              <Input
                id="linkLabel"
                value={linkLabel}
                onChange={(e) => setLinkLabel(e.target.value)}
                placeholder="Xem ngay"
              />
            </div>
          </div>

          <div className="grid grid-cols-3 gap-3">
            <div className="space-y-2">
              <Label htmlFor="priority">Ưu tiên</Label>
              <Input
                id="priority"
                type="number"
                value={priority}
                onChange={(e) => setPriority(parseInt(e.target.value, 10) || 0)}
              />
              <p className="text-[10px] text-muted-foreground">Cao = hiện trước</p>
            </div>
            <div className="space-y-2">
              <Label htmlFor="scrollSpeed">Tốc độ (px/s)</Label>
              <Input
                id="scrollSpeed"
                type="number"
                min={10}
                value={scrollSpeed}
                onChange={(e) => setScrollSpeed(parseInt(e.target.value, 10) || 50)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="stepMinutes">Lặp mỗi (phút)</Label>
              <Input
                id="stepMinutes"
                type="number"
                min={1}
                value={stepMinutes}
                onChange={(e) => setStepMinutes(parseInt(e.target.value, 10) || 5)}
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label htmlFor="startAt">Bắt đầu</Label>
              <Input
                id="startAt"
                type="datetime-local"
                value={startAt}
                onChange={(e) => setStartAt(e.target.value)}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="endAt">Kết thúc</Label>
              <Input
                id="endAt"
                type="datetime-local"
                value={endAt}
                onChange={(e) => setEndAt(e.target.value)}
                required
              />
            </div>
          </div>

          <div className="flex flex-wrap gap-2 pt-2">
            {isEdit ? (
              <>
                <Button onClick={() => handleSubmit("DRAFT")} disabled={submitting || !formValid}>
                  {submitting ? "Đang lưu..." : "Cập nhật"}
                </Button>
                {(initial.status === "DRAFT" || initial.status === "SCHEDULED") && (
                  <>
                    <Button
                      variant="secondary"
                      onClick={async () => {
                        await handleSubmit("DRAFT");
                        if (!submitting) {
                          try {
                            await announcementApi.activateAnnouncement(initial.id);
                            toast.success("Đã kích hoạt thông báo.");
                            router.push("/announcements");
                          } catch (err) {
                            toast.error(err instanceof ApiError ? err.message : "Kích hoạt thất bại.");
                          }
                        }
                      }}
                      disabled={submitting || !formValid}
                    >
                      Lưu & Kích hoạt
                    </Button>
                  </>
                )}
              </>
            ) : (
              <>
                <Button variant="outline" onClick={() => handleSubmit("DRAFT")} disabled={submitting || !formValid}>
                  Lưu nháp
                </Button>
                <Button variant="secondary" onClick={() => handleSubmit("SCHEDULED")} disabled={submitting || !formValid}>
                  Lên lịch
                </Button>
                <Button onClick={() => handleSubmit("ACTIVE")} disabled={submitting || !formValid}>
                  Kích hoạt ngay
                </Button>
              </>
            )}
            <Button variant="ghost" onClick={() => router.push("/announcements")}>
              Hủy
            </Button>
          </div>
        </CardContent>
      </Card>

      <div>
        <AnnouncementPreview
          contentHtml={contentHtml}
          backgroundColor={backgroundColor}
          scrollSpeed={scrollSpeed}
        />
      </div>
    </div>
  );
}
