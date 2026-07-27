"use client";

import { useEffect, useMemo, useRef, useState } from "react";

interface AnnouncementItem {
  id: string;
  contentHtml: string;
  backgroundColor: string;
  linkUrl: string | null;
  linkLabel: string | null;
  priority: number;
  scrollSpeed: number;
  stepSeconds: number;
}

export function useAnnouncementRotation(
  announcements: AnnouncementItem[],
): AnnouncementItem | null {
  const [index, setIndex] = useState(0);
  const timerRef = useRef<ReturnType<typeof setTimeout>>();

  const sorted = useMemo(
    () => [...announcements].sort((a, b) => b.priority - a.priority),
    [announcements],
  );

  useEffect(() => {
    setIndex(0);
  }, [sorted.length]);

  useEffect(() => {
    if (sorted.length <= 1) return;

    const current = sorted[index];
    if (!current) return;

    timerRef.current = setTimeout(() => {
      setIndex((prev) => (prev + 1) % sorted.length);
    }, current.stepSeconds * 1000);

    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [index, sorted]);

  if (sorted.length === 0) return null;
  return sorted[index] ?? sorted[0];
}
