"use client";

import { ImageUploader } from "@/components/media/ImageUploader";

type Props = {
  value: string | null;
  onChange: (url: string | null) => void;
  label?: string;
};

export function SourceMediaUploader({ value, onChange, label = "Ảnh" }: Props) {
  return (
    <ImageUploader
      purpose="source_question"
      value={value}
      onChange={(objectKey) => onChange(objectKey)}
      label={label}
    />
  );
}
