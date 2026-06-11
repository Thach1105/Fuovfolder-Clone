"use client";

import dynamic from "next/dynamic";

const RichTextEditorInner = dynamic(() => import("@/components/forum/RichTextEditorInner"), {
  ssr: false,
  loading: () => (
    <div className="input-field min-h-[160px] animate-pulse text-sm text-slate-400">
      Đang tải trình soạn thảo...
    </div>
  ),
});

interface RichTextEditorProps {
  value: string;
  onChange: (value: string) => void;
  minHeight?: number;
  placeholder?: string;
  enableImageUpload?: boolean;
}

export function RichTextEditor(props: RichTextEditorProps) {
  return <RichTextEditorInner {...props} />;
}

/** @deprecated Dùng RichTextEditor */
export const MarkdownEditor = RichTextEditor;
