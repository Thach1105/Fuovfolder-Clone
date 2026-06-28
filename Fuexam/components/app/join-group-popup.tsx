'use client'

import { useEffect, useState } from 'react'
import { Facebook, Users, ArrowUpRight } from 'lucide-react'
import {
  Dialog,
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
        showCloseButton
        className="overflow-hidden border-0 p-0 sm:max-w-md"
      >
        {/* Gradient banner */}
        <div className="relative bg-gradient-to-br from-[#1877F2] via-[#2d8bff] to-[#0a5fd6] px-6 pt-8 pb-10 text-center">
          <div className="pointer-events-none absolute inset-0 opacity-20 [background:radial-gradient(circle_at_20%_20%,white,transparent_40%),radial-gradient(circle_at_80%_60%,white,transparent_35%)]" />
          <div className="relative mx-auto flex size-16 items-center justify-center rounded-2xl bg-white/15 shadow-lg ring-1 ring-white/25 backdrop-blur">
            <Users className="size-8 text-white" strokeWidth={1.75} />
          </div>
          <DialogHeader className="mt-4">
            <DialogTitle className="text-center text-xl font-bold text-white">
              Tham gia cộng đồng Fuexam
            </DialogTitle>
            <DialogDescription className="text-center text-sm text-white/85">
              Vào các nhóm Facebook để nhận thông báo, hỏi đáp nhanh và cập nhật
              tài liệu, đề thi mới nhất.
            </DialogDescription>
          </DialogHeader>
        </div>

        {/* Body */}
        <div className="space-y-3 px-6 pt-5 pb-6">
          {COMMUNITY_GROUPS.map((group) => (
            <a
              key={group.href}
              href={group.href}
              target="_blank"
              rel="noopener noreferrer"
              className="group flex items-center gap-3 rounded-xl border bg-card px-4 py-3 shadow-sm transition-all hover:border-[#1877F2]/40 hover:shadow-md"
            >
              <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-[#1877F2]/10 text-[#1877F2]">
                <Facebook className="size-5" />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-semibold">
                  {group.name}
                </span>
                <span className="block truncate text-xs text-muted-foreground">
                  {group.desc}
                </span>
              </span>
              <ArrowUpRight className="size-5 shrink-0 text-muted-foreground transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5 group-hover:text-[#1877F2]" />
            </a>
          ))}

          <label className="flex cursor-pointer items-center justify-center gap-2 pt-1 text-sm text-muted-foreground select-none">
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
