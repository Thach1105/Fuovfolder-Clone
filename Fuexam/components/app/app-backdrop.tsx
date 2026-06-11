"use client";

import { AnimatedSphere } from "@/components/landing/animated-sphere";

export function AppBackdrop() {
  return (
    <div className="pointer-events-none fixed inset-0 -z-10 overflow-hidden">
      <div className="absolute right-[-10%] top-1/2 h-[600px] w-[600px] -translate-y-1/2 opacity-[0.18] lg:h-[760px] lg:w-[760px]">
        <AnimatedSphere />
      </div>
      <div className="absolute inset-0 opacity-30">
        {Array.from({ length: 8 }).map((_, i) => (
          <div
            key={`h-${i}`}
            className="absolute left-0 right-0 h-px bg-foreground/[0.06]"
            style={{ top: `${12.5 * (i + 1)}%` }}
          />
        ))}
        {Array.from({ length: 12 }).map((_, i) => (
          <div
            key={`v-${i}`}
            className="absolute bottom-0 top-0 w-px bg-foreground/[0.06]"
            style={{ left: `${8.33 * (i + 1)}%` }}
          />
        ))}
      </div>
    </div>
  );
}