import type { ImgHTMLAttributes } from "react";
import { cn } from "@/lib/utils";

type Props = Omit<ImgHTMLAttributes<HTMLImageElement>, "ref"> & {
  containerClassName?: string;
};

export function ImageWithWatermark({ containerClassName, className, ...imgProps }: Props) {
  return (
    <span className={cn("relative inline-block", containerClassName)}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img className={className} {...imgProps} />
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src="/logo-full.png"
        alt=""
        aria-hidden
        draggable={false}
        className="pointer-events-none absolute inset-0 m-auto w-1/2 select-none opacity-15"
      />
    </span>
  );
}
