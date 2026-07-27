"use client";

import { useEffect, useRef, useState } from "react";
import DOMPurify from "dompurify";
import { useAnnouncementStream } from "@/hooks/use-announcement-stream";
import { useAnnouncementRotation } from "@/hooks/use-announcement-rotation";

const ALLOWED_TAGS = ["span", "strong", "em", "u", "br"];
const ALLOWED_ATTR = ["style"];

function sanitize(html: string): string {
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
  });
}

export function AnnouncementBar() {
  const announcements = useAnnouncementStream();
  const current = useAnnouncementRotation(announcements);
  const containerRef = useRef<HTMLDivElement>(null);
  const contentRef = useRef<HTMLDivElement>(null);
  const [duration, setDuration] = useState(10);

  useEffect(() => {
    if (contentRef.current && containerRef.current && current) {
      const contentWidth = contentRef.current.scrollWidth;
      const containerWidth = containerRef.current.offsetWidth;
      const totalDistance = contentWidth + containerWidth;
      setDuration(totalDistance / Math.max(current.scrollSpeed, 10));
    }
  }, [current]);

  if (!current) return null;

  const cleanHtml = sanitize(current.contentHtml);

  return (
    <div
      className="relative z-50 w-full overflow-hidden"
      style={{ backgroundColor: current.backgroundColor }}
    >
      <div ref={containerRef} className="relative h-8 overflow-hidden">
        {current.linkUrl ? (
          <a
            href={current.linkUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="absolute flex h-full items-center whitespace-nowrap text-sm text-white no-underline"
            style={{
              animation: `announcement-marquee ${duration}s linear infinite`,
            }}
          >
            <span ref={contentRef} dangerouslySetInnerHTML={{ __html: cleanHtml }} />
            {current.linkLabel && (
              <span className="ml-3 rounded bg-white/20 px-2 py-0.5 text-xs font-medium">
                {current.linkLabel} →
              </span>
            )}
          </a>
        ) : (
          <div
            className="absolute flex h-full items-center whitespace-nowrap text-sm text-white"
            style={{
              animation: `announcement-marquee ${duration}s linear infinite`,
            }}
          >
            <span ref={contentRef} dangerouslySetInnerHTML={{ __html: cleanHtml }} />
          </div>
        )}
      </div>
      <style>{`
        @keyframes announcement-marquee {
          0% { transform: translateX(100vw); }
          100% { transform: translateX(-100%); }
        }
      `}</style>
    </div>
  );
}
