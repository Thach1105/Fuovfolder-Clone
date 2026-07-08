"use client";

import { useEffect, type ReactNode } from "react";
import { X, ChevronLeft, ChevronRight } from "lucide-react";
import { ImageWithWatermark } from "@/components/shared/image-with-watermark";

type Props = {
  images: string[];
  currentIndex: number;
  onClose: () => void;
  onNavigate: (index: number) => void;
  /** Optional panel rendered beside the image (e.g. a per-image comment thread). */
  renderSidePanel?: (index: number) => ReactNode;
};

export function Lightbox({ images, currentIndex, onClose, onNavigate, renderSidePanel }: Props) {
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
      if (e.key === "ArrowLeft" && currentIndex > 0) {
        onNavigate(currentIndex - 1);
      }
      if (e.key === "ArrowRight" && currentIndex < images.length - 1) {
        onNavigate(currentIndex + 1);
      }
    }

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [currentIndex, images.length, onClose, onNavigate]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/90"
      onClick={onClose}
    >
      <button
        className="absolute right-4 top-4 rounded-full bg-white/10 p-2 text-white hover:bg-white/20"
        onClick={onClose}
      >
        <X className="h-6 w-6" />
      </button>

      {currentIndex > 0 && (
        <button
          className="absolute left-4 top-1/2 -translate-y-1/2 rounded-full bg-white/10 p-2 text-white hover:bg-white/20"
          onClick={(e) => {
            e.stopPropagation();
            onNavigate(currentIndex - 1);
          }}
        >
          <ChevronLeft className="h-6 w-6" />
        </button>
      )}

      {currentIndex < images.length - 1 && (
        <button
          className="absolute right-4 top-1/2 -translate-y-1/2 rounded-full bg-white/10 p-2 text-white hover:bg-white/20"
          onClick={(e) => {
            e.stopPropagation();
            onNavigate(currentIndex + 1);
          }}
        >
          <ChevronRight className="h-6 w-6" />
        </button>
      )}

      <div
        className="flex max-h-[92vh] w-full max-w-[95vw] flex-col items-stretch gap-4 lg:flex-row"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="relative flex min-h-0 flex-1 items-center justify-center">
          <ImageWithWatermark
            src={images[currentIndex]}
            alt={`Anh ${currentIndex + 1}`}
            className="max-h-[85vh] max-w-full rounded-lg object-contain lg:max-h-[92vh]"
          />
          <div className="absolute bottom-4 left-1/2 -translate-x-1/2 rounded-full bg-black/70 px-3 py-1 text-sm text-white">
            {currentIndex + 1} / {images.length}
          </div>
        </div>
        {renderSidePanel && (
          <div className="max-h-[92vh] w-full shrink-0 overflow-y-auto rounded-2xl bg-background p-4 lg:w-[380px]">
            {renderSidePanel(currentIndex)}
          </div>
        )}
      </div>
    </div>
  );
}
