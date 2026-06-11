"use client";

import { useEffect, useRef, useState } from "react";
import Image from "@tiptap/extension-image";
import Link from "@tiptap/extension-link";
import Placeholder from "@tiptap/extension-placeholder";
import { EditorContent, useEditor } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import { uploadMedia, resolveMediaUrl } from "@/lib/api/media";

interface RichTextEditorInnerProps {
  value: string;
  onChange: (value: string) => void;
  minHeight?: number;
  placeholder?: string;
  enableImageUpload?: boolean;
}

function ToolbarButton({
  active,
  disabled,
  onClick,
  children,
  title,
}: {
  active?: boolean;
  disabled?: boolean;
  onClick: () => void;
  children: React.ReactNode;
  title: string;
}) {
  return (
    <button
      type="button"
      title={title}
      disabled={disabled}
      onClick={onClick}
      className={`rounded px-2 py-1 text-xs font-medium ${
        active ? "bg-fuo-100 text-fuo-800" : "text-slate-600 hover:bg-slate-100"
      } disabled:opacity-50`}
    >
      {children}
    </button>
  );
}

export default function RichTextEditorInner({
  value,
  onChange,
  minHeight = 160,
  placeholder,
  enableImageUpload = true,
}: RichTextEditorInnerProps) {
  const fileRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const syncingRef = useRef(false);

  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        heading: { levels: [2, 3] },
      }),
      Link.configure({
        openOnClick: false,
        HTMLAttributes: { rel: "noopener noreferrer nofollow", target: "_blank" },
      }),
      Image.configure({ inline: false, allowBase64: false }),
      Placeholder.configure({
        placeholder: placeholder ?? "Viết nội dung bài viết...",
      }),
    ],
    content: value || "<p></p>",
    immediatelyRender: false,
    editorProps: {
      attributes: {
        class:
          "prose prose-sm max-w-none focus:outline-none px-3 py-2 text-slate-800",
      },
    },
    onUpdate: ({ editor: current }) => {
      if (syncingRef.current) {
        return;
      }
      const html = current.isEmpty ? "" : current.getHTML();
      onChange(html);
    },
  });

  useEffect(() => {
    if (!editor) {
      return;
    }
    const currentHtml = editor.isEmpty ? "" : editor.getHTML();
    const nextHtml = value || "";
    if (currentHtml === nextHtml || (currentHtml === "<p></p>" && nextHtml === "")) {
      return;
    }
    syncingRef.current = true;
    editor.commands.setContent(nextHtml || "<p></p>", { emitUpdate: false });
    syncingRef.current = false;
  }, [editor, value]);

  async function handleImageUpload(file: File | null) {
    if (!file || !editor) {
      return;
    }
    setError(null);
    setUploading(true);
    try {
      const uploaded = await uploadMedia(file, "forum_image");
      const src = uploaded.publicUrl ?? resolveMediaUrl(uploaded.objectKey) ?? uploaded.objectKey;
      editor.chain().focus().setImage({ src, alt: uploaded.originalFilename }).run();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Upload ảnh thất bại");
    } finally {
      setUploading(false);
    }
  }

  function setLink() {
    if (!editor) {
      return;
    }
    const previous = editor.getAttributes("link").href as string | undefined;
    const url = window.prompt("URL liên kết", previous ?? "https://");
    if (url === null) {
      return;
    }
    if (url.trim() === "") {
      editor.chain().focus().extendMarkRange("link").unsetLink().run();
      return;
    }
    editor.chain().focus().extendMarkRange("link").setLink({ href: url.trim() }).run();
  }

  if (!editor) {
    return (
      <div
        className="input-field animate-pulse text-sm text-slate-400"
        style={{ minHeight }}
      >
        Đang tải trình soạn thảo...
      </div>
    );
  }

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap gap-1 rounded-t-lg border border-b-0 border-slate-300 bg-slate-50 p-2">
        <ToolbarButton
          title="In đậm"
          active={editor.isActive("bold")}
          onClick={() => editor.chain().focus().toggleBold().run()}
        >
          B
        </ToolbarButton>
        <ToolbarButton
          title="In nghiêng"
          active={editor.isActive("italic")}
          onClick={() => editor.chain().focus().toggleItalic().run()}
        >
          I
        </ToolbarButton>
        <ToolbarButton
          title="Tiêu đề"
          active={editor.isActive("heading", { level: 2 })}
          onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}
        >
          H2
        </ToolbarButton>
        <ToolbarButton
          title="Danh sách"
          active={editor.isActive("bulletList")}
          onClick={() => editor.chain().focus().toggleBulletList().run()}
        >
          • List
        </ToolbarButton>
        <ToolbarButton
          title="Danh sách đánh số"
          active={editor.isActive("orderedList")}
          onClick={() => editor.chain().focus().toggleOrderedList().run()}
        >
          1. List
        </ToolbarButton>
        <ToolbarButton title="Liên kết" active={editor.isActive("link")} onClick={setLink}>
          Link
        </ToolbarButton>
        {enableImageUpload && (
          <>
            <input
              ref={fileRef}
              type="file"
              accept="image/png,image/jpeg,image/webp,image/gif"
              className="hidden"
              onChange={(e) => handleImageUpload(e.target.files?.[0] ?? null)}
            />
            <ToolbarButton
              title="Tải ảnh"
              disabled={uploading}
              onClick={() => fileRef.current?.click()}
            >
              {uploading ? "..." : "Ảnh"}
            </ToolbarButton>
          </>
        )}
      </div>
      <div
        className="rounded-b-lg border border-slate-300 bg-white"
        style={{ minHeight }}
      >
        <EditorContent editor={editor} />
      </div>
      <p className="text-xs text-slate-500">
        Soạn thảo trực quan: in đậm, danh sách, liên kết và ảnh từ MinIO.
      </p>
      {error && <p className="text-xs text-red-600">{error}</p>}
    </div>
  );
}
