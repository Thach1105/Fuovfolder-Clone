"use client";

import { Avatar, AvatarImage, AvatarFallback } from "@/components/ui/avatar";
import { cn } from "@/lib/utils";

interface UserAvatarProps {
  src: string | null | undefined;
  displayName: string;
  size?: "sm" | "md" | "lg" | "xl";
  className?: string;
}

const SIZE_CLASS = {
  sm: "size-8 text-xs",
  md: "size-12 text-sm",
  lg: "size-16 text-lg",
  xl: "size-24 text-3xl",
};

export function UserAvatar({ src, displayName, size = "md", className }: UserAvatarProps) {
  return (
    <Avatar className={cn(SIZE_CLASS[size], className)}>
      {src && <AvatarImage src={src} alt={displayName} loading="lazy" />}
      <AvatarFallback className="font-bold">
        {displayName.charAt(0).toUpperCase()}
      </AvatarFallback>
    </Avatar>
  );
}
