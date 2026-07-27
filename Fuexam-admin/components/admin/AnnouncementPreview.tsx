"use client";

import { useEffect, useRef, useState } from "react";

interface AnnouncementPreviewProps {
  contentHtml: string;
  backgroundColor: string;
  scrollSpeed: number;
}

export function AnnouncementPreview({ contentHtml, backgroundColor, scrollSpeed }: AnnouncementPreviewProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const contentRef = useRef<HTMLDivElement>(null);
  const [animationDuration, setAnimationDuration] = useState(10);

  useEffect(() => {
    if (contentRef.current && containerRef.current) {
      const contentWidth = contentRef.current.scrollWidth;
      const containerWidth = containerRef.current.offsetWidth;
      const totalDistance = contentWidth + containerWidth;
      setAnimationDuration(totalDistance / Math.max(scrollSpeed, 10));
    }
  }, [contentHtml, scrollSpeed]);

  if (!contentHtml.trim()) {
    return (
      <div className="rounded-md border border-dashed border-border px-4 py-3 text-center text-xs text-muted-foreground">
        Nhập nội dung để xem trước
      </div>
    );
  }

  return (
    <div className="space-y-2">
      <p className="text-xs font-medium text-muted-foreground">Xem trước:</p>
      <div
        ref={containerRef}
        className="relative overflow-hidden rounded-md py-2"
        style={{ backgroundColor }}
      >
        <div
          ref={contentRef}
          className="inline-block whitespace-nowrap text-sm text-white"
          style={{
            animation: `marquee ${animationDuration}s linear infinite`,
          }}
          dangerouslySetInnerHTML={{ __html: contentHtml }}
        />
        <style>{`
          @keyframes marquee {
            0% { transform: translateX(100%); }
            100% { transform: translateX(-100%); }
          }
        `}</style>
      </div>
    </div>
  );
}
