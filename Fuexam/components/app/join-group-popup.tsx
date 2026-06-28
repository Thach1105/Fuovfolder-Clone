'use client'

import { useEffect, useState } from 'react'
import { Users, ArrowUpRight, X } from 'lucide-react'
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog'
import { Checkbox } from '@/components/ui/checkbox'
import { COMMUNITY_GROUPS } from '@/lib/community-groups'

const STORAGE_KEY = 'fuov:join-group-dismissed'

/** Returns YYYY-MM-DD in local time. */
function today(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export function JoinGroupPopup() {
  const [open, setOpen] = useState(false)
  const [dontShow, setDontShow] = useState(false)

  useEffect(() => {
    // Defer so it doesn't fight first paint.
    const t = setTimeout(() => {
      try {
        if (localStorage.getItem(STORAGE_KEY) !== today()) setOpen(true)
      } catch {
        setOpen(true)
      }
    }, 700)
    return () => clearTimeout(t)
  }, [])

  function handleOpenChange(next: boolean) {
    setOpen(next)
    if (!next && dontShow) {
      try {
        localStorage.setItem(STORAGE_KEY, today())
      } catch {
        /* ignore */
      }
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent
        showCloseButton={false}
        className="block gap-0 overflow-hidden rounded-2xl border border-foreground/10 p-0 shadow-2xl sm:max-w-md"
      >
        {/* Header — monochrome block, đồng bộ tone landing */}
        <div className="relative overflow-hidden bg-foreground px-6 pb-7 pt-6 text-background">
          {/* grid lines mờ cho cảm giác "technology" */}
          <div className="pointer-events-none absolute inset-0 opacity-[0.07]">
            {[20, 40, 60, 80].map((top) => (
              <div
                key={`h-${top}`}
                className="absolute inset-x-0 h-px bg-background"
                style={{ top: `${top}%` }}
              />
            ))}
            {[12.5, 25, 37.5, 50, 62.5, 75, 87.5].map((left) => (
              <div
                key={`v-${left}`}
                className="absolute inset-y-0 w-px bg-background"
                style={{ left: `${left}%` }}
              />
            ))}
          </div>

          <DialogClose className="absolute right-4 top-4 rounded-md p-1 text-background/60 transition-colors hover:bg-background/10 hover:text-background focus:outline-none">
            <X className="size-4" />
            <span className="sr-only">Đóng</span>
          </DialogClose>

          <div className="relative">
            <span className="inline-flex items-center gap-2 font-mono text-[11px] uppercase tracking-[0.18em] text-background/55">
              <Users className="size-3.5" strokeWidth={2} />
              Cộng đồng
            </span>
            <DialogHeader className="mt-3 space-y-1.5 text-left">
              <DialogTitle className="font-display text-2xl leading-tight tracking-tight text-background">
                Tham gia cộng đồng Fuexam
              </DialogTitle>
              <DialogDescription className="text-sm leading-relaxed text-background/60">
                Vào các nhóm Facebook để nhận thông báo, hỏi đáp nhanh và cập
                nhật tài liệu, đề thi mới nhất.
              </DialogDescription>
            </DialogHeader>
          </div>
        </div>

        {/* Body */}
        <div className="space-y-2.5 bg-background p-5">
          {COMMUNITY_GROUPS.map((group, index) => (
            <a
              key={group.href}
              href={group.href}
              target="_blank"
              rel="noopener noreferrer"
              className="group flex items-center gap-3 rounded-lg border border-foreground/10 bg-card px-3.5 py-3 transition-all hover:border-foreground/30 hover:bg-accent/40"
            >
              <span className="flex size-9 shrink-0 items-center justify-center rounded-md border border-foreground/10 bg-background font-mono text-xs text-muted-foreground transition-colors group-hover:border-foreground/30 group-hover:text-foreground">
                {String(index + 1).padStart(2, "0")}
              </span>
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-semibold text-foreground">
                  {group.name}
                </span>
                <span className="block truncate text-xs text-muted-foreground">
                  {group.desc}
                </span>
              </span>
              <ArrowUpRight className="size-4 shrink-0 text-muted-foreground transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5 group-hover:text-foreground" />
            </a>
          ))}

          <label className="mt-1 flex cursor-pointer items-center gap-2 px-1 pt-2 text-xs text-muted-foreground select-none">
            <Checkbox
              checked={dontShow}
              onCheckedChange={(v) => setDontShow(v === true)}
            />
            Không hiện lại trong hôm nay
          </label>
        </div>
      </DialogContent>
    </Dialog>
  )
}
