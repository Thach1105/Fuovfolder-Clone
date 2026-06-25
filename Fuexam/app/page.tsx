"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ArrowRight, BookOpenCheck, MessagesSquare, GraduationCap, Check, Facebook } from "lucide-react";
import { Button } from "@/components/ui/button";
import { AnimatedSphere } from "@/components/landing/animated-sphere";
import { AnimatedTetrahedron } from "@/components/landing/animated-tetrahedron";

const words = ["ôn thi", "thảo luận", "chia sẻ", "bứt phá"];

const FEATURES = [
  {
    icon: BookOpenCheck,
    title: "Source — Ngân hàng câu hỏi",
    desc: "Ôn thi theo mã môn với câu hỏi tương tác, chấm điểm tức thì và tỉ lệ trùng lặp đề thi.",
    href: "/suoc",
    cta: "Khám phá Source",
  },
  {
    icon: MessagesSquare,
    title: "Diễn đàn cộng đồng",
    desc: "Hỏi đáp, chia sẻ tài liệu và kết nối với hàng nghìn sinh viên FPT.",
    href: "/forums",
    cta: "Vào diễn đàn",
  },
  {
    icon: GraduationCap,
    title: "Khóa học & Coursera",
    desc: "Tổng hợp khóa học chất lượng, lộ trình học rõ ràng theo từng môn.",
    href: "/coursera",
    cta: "Xem khóa học",
  },
];

const STEPS = [
  {
    number: "I",
    title: "Tạo tài khoản FPT",
    desc: "Đăng ký bằng email sinh viên, xác minh và tham gia cộng đồng trong vài phút.",
  },
  {
    number: "II",
    title: "Chọn Source theo mã môn",
    desc: "Tìm tài liệu ôn thi theo mã môn (MLN111, CSI106...), xem tỉ lệ trùng lặp đề và mở khóa bằng Fuexam Point.",
  },
  {
    number: "III",
    title: "Luyện đề & thảo luận",
    desc: "Làm câu hỏi tương tác có chấm điểm, xem giải thích, rồi trao đổi thêm trên diễn đàn.",
  },
];

const PLANS = [
  {
    name: "Miễn phí",
    price: "0",
    unit: "Fuexam Point",
    description: "Bắt đầu với cộng đồng và tài liệu cơ bản.",
    features: ["Truy cập diễn đàn", "Xem tài liệu công khai", "Tích điểm Fuexam Point"],
    cta: "Tạo tài khoản",
    href: "/register",
    popular: false,
  },
  {
    name: "Source",
    price: "Theo môn",
    unit: "",
    description: "Mở khóa ngân hàng câu hỏi ôn thi theo từng mã môn.",
    features: ["Câu hỏi ôn tập tương tác", "Chấm điểm & giải thích", "Tỉ lệ trùng lặp đề thi", "Truy cập theo thời hạn"],
    cta: "Khám phá Source",
    href: "/suoc",
    popular: true,
  },
  {
    name: "Membership",
    price: "Gói",
    unit: "",
    description: "Đặc quyền thành viên & vai trò nâng cao trong cộng đồng.",
    features: ["Huy hiệu & vai trò riêng", "Ưu đãi đổi điểm", "Quyền lợi mở rộng"],
    cta: "Xem gói membership",
    href: "/membership",
    popular: false,
  },
];

export default function Home() {
  const [wordIndex, setWordIndex] = useState(0);
  const [visible, setVisible] = useState(false);

  useEffect(() => { setVisible(true); }, []);
  useEffect(() => {
    const id = setInterval(() => setWordIndex((p) => (p + 1) % words.length), 2500);
    return () => clearInterval(id);
  }, []);

  return (
    <main className="relative min-h-screen overflow-x-hidden noise-overlay">
      {/* Top nav */}
      <header className="fixed inset-x-0 top-0 z-50 px-4 pt-4">
        <nav className="mx-auto flex h-14 max-w-[1200px] items-center gap-6 rounded-2xl border border-foreground/10 bg-background/70 px-5 backdrop-blur-xl">
          <Link href="/" className="flex items-center gap-2">
            <span className="font-display text-xl tracking-tight">Fuexam</span>
            <span className="mt-1 font-mono text-[10px] text-muted-foreground">FPT</span>
          </Link>
          <div className="ml-auto flex items-center gap-3">
            <Link href="/suoc" className="hidden text-sm text-foreground/70 transition hover:text-foreground sm:inline">Source</Link>
            <Link href="/forums" className="hidden text-sm text-foreground/70 transition hover:text-foreground sm:inline">Diễn đàn</Link>
            <Link href="/membership" className="hidden text-sm text-foreground/70 transition hover:text-foreground sm:inline">Membership</Link>
            <Link href="/login" className="text-sm text-foreground/70 transition hover:text-foreground">Đăng nhập</Link>
            <Button asChild size="sm" className="rounded-full bg-foreground px-4 text-background hover:bg-foreground/90">
              <Link href="/register">Tạo tài khoản</Link>
            </Button>
          </div>
        </nav>
      </header>

      {/* Hero */}
      <section className="relative flex min-h-screen flex-col justify-center overflow-hidden">
        <div className="pointer-events-none absolute right-0 top-1/2 h-[600px] w-[600px] -translate-y-1/2 opacity-40 lg:h-[800px] lg:w-[800px]">
          <AnimatedSphere />
        </div>
        <div className="pointer-events-none absolute inset-0 opacity-30">
          {Array.from({ length: 8 }).map((_, i) => (
            <div key={`h-${i}`} className="absolute inset-x-0 h-px bg-foreground/10" style={{ top: `${12.5 * (i + 1)}%` }} />
          ))}
          {Array.from({ length: 12 }).map((_, i) => (
            <div key={`v-${i}`} className="absolute inset-y-0 w-px bg-foreground/10" style={{ left: `${8.33 * (i + 1)}%` }} />
          ))}
        </div>

        <div className="relative z-10 mx-auto w-full max-w-[1200px] px-6 py-32 lg:px-12">
          <div className={`mb-8 transition-all duration-700 ${visible ? "translate-y-0 opacity-100" : "translate-y-4 opacity-0"}`}>
            <span className="inline-flex items-center gap-3 font-mono text-sm text-muted-foreground">
              <span className="h-px w-8 bg-foreground/30" />
              Cộng đồng sinh viên Đại học FPT
            </span>
          </div>

          <h1 className={`font-display text-[clamp(3rem,11vw,9rem)] leading-[0.9] tracking-tight transition-all duration-1000 ${visible ? "translate-y-0 opacity-100" : "translate-y-8 opacity-0"}`}>
            <span className="block">Nền tảng để</span>
            <span className="block">
              cùng nhau{" "}
              <span className="relative inline-block">
                <span key={wordIndex} className="inline-flex">
                  {words[wordIndex].split("").map((char, i) => (
                    <span key={`${wordIndex}-${i}`} className="inline-block animate-char-in" style={{ animationDelay: `${i * 50}ms` }}>
                      {char === " " ? "\u00A0" : char}
                    </span>
                  ))}
                </span>
                <span className="absolute -bottom-2 left-0 right-0 h-3 bg-foreground/10" />
              </span>
            </span>
          </h1>

          <div className="mt-12 grid items-end gap-12 lg:grid-cols-2 lg:gap-24">
            <p className={`max-w-xl text-xl leading-relaxed text-muted-foreground transition-all delay-200 duration-700 lg:text-2xl ${visible ? "translate-y-0 opacity-100" : "translate-y-4 opacity-0"}`}>
              Ôn thi với ngân hàng câu hỏi Source, thảo luận trên diễn đàn và học theo lộ trình — tất cả trong một nơi.
            </p>
            <div className={`flex flex-col items-start gap-4 transition-all delay-300 duration-700 sm:flex-row ${visible ? "translate-y-0 opacity-100" : "translate-y-4 opacity-0"}`}>
              <Button asChild size="lg" className="group h-14 rounded-full bg-foreground px-8 text-base text-background hover:bg-foreground/90">
                <Link href="/suoc">
                  Bắt đầu ôn thi
                  <ArrowRight className="ml-2 h-4 w-4 transition-transform group-hover:translate-x-1" />
                </Link>
              </Button>
              <Button asChild size="lg" variant="outline" className="h-14 rounded-full border-foreground/20 px-8 text-base hover:bg-foreground/5">
                <Link href="/register">Tạo tài khoản</Link>
              </Button>
            </div>
          </div>
        </div>
      </section>

      {/* Features */}
      <section className="relative z-10 mx-auto max-w-[1200px] px-6 pb-24 lg:px-12">
        <div className="grid gap-6 md:grid-cols-3">
          {FEATURES.map((f) => (
            <Link
              key={f.title}
              href={f.href}
              className="group flex flex-col rounded-2xl border border-foreground/10 bg-background/60 p-7 backdrop-blur transition-all duration-300 hover:-translate-y-1 hover:border-foreground/25 hover:shadow-lg"
            >
              <f.icon className="h-7 w-7" />
              <h3 className="mt-5 font-display text-2xl">{f.title}</h3>
              <p className="mt-2 flex-1 text-sm text-muted-foreground">{f.desc}</p>
              <span className="mt-5 inline-flex items-center gap-1 text-sm font-medium">
                {f.cta}
                <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" />
              </span>
            </Link>
          ))}
        </div>
      </section>

      {/* How it works (dark, with tetrahedron) */}
      <section className="relative overflow-hidden bg-foreground py-24 text-background lg:py-32">
        <div className="absolute inset-0 opacity-[0.04]" style={{ backgroundImage: "repeating-linear-gradient(-45deg, transparent, transparent 40px, currentColor 40px, currentColor 41px)" }} />
        <div className="relative z-10 mx-auto max-w-[1200px] px-6 lg:px-12">
          <div className="mb-16">
            <span className="mb-6 inline-flex items-center gap-3 font-mono text-sm text-background/50">
              <span className="h-px w-8 bg-background/30" />
              Cách hoạt động
            </span>
            <h2 className="font-display text-4xl tracking-tight lg:text-6xl">
              Ba bước.<br /><span className="text-background/50">Học hiệu quả hơn.</span>
            </h2>
          </div>
          <div className="grid items-center gap-16 lg:grid-cols-2 lg:gap-24">
            <div>
              {STEPS.map((s) => (
                <div key={s.number} className="border-b border-background/10 py-8">
                  <div className="flex items-start gap-6">
                    <span className="font-display text-3xl text-background/30">{s.number}</span>
                    <div>
                      <h3 className="font-display text-2xl lg:text-3xl">{s.title}</h3>
                      <p className="mt-2 leading-relaxed text-background/60">{s.desc}</p>
                    </div>
                  </div>
                </div>
              ))}
            </div>
            <div className="relative mx-auto h-[320px] w-full max-w-md text-background/80 lg:h-[420px] [&_canvas]:invert">
              <AnimatedTetrahedron />
            </div>
          </div>
        </div>
      </section>

      {/* Pricing / plans */}
      <section className="relative border-t border-foreground/10 py-24 lg:py-32">
        <div className="mx-auto max-w-[1200px] px-6 lg:px-12">
          <div className="mb-16 max-w-2xl">
            <span className="mb-6 block font-mono text-xs uppercase tracking-widest text-muted-foreground">Gói &amp; quyền lợi</span>
            <h2 className="font-display text-4xl tracking-tight lg:text-6xl">
              Bắt đầu miễn phí,<br /><span className="text-stroke">mở khóa khi cần</span>
            </h2>
            <p className="mt-5 max-w-xl text-muted-foreground">
              Dùng Fuexam Point để mở Source theo môn hoặc nâng cấp membership. Không phí ẩn.
            </p>
          </div>

          <div className="grid gap-px bg-foreground/10 md:grid-cols-3">
            {PLANS.map((plan, idx) => (
              <div key={plan.name} className={`relative bg-background p-8 lg:p-10 ${plan.popular ? "md:-my-4 md:py-12 border-2 border-foreground" : ""}`}>
                {plan.popular && (
                  <span className="absolute -top-3 left-8 bg-foreground px-3 py-1 font-mono text-xs uppercase tracking-widest text-background">Phổ biến</span>
                )}
                <span className="font-mono text-xs text-muted-foreground">{String(idx + 1).padStart(2, "0")}</span>
                <h3 className="mt-2 font-display text-3xl">{plan.name}</h3>
                <p className="mt-2 text-sm text-muted-foreground">{plan.description}</p>
                <div className="mt-6 mb-6 flex items-baseline gap-2 border-b border-foreground/10 pb-6">
                  <span className="font-display text-4xl lg:text-5xl">{plan.price}</span>
                  {plan.unit && <span className="text-muted-foreground">{plan.unit}</span>}
                </div>
                <ul className="mb-8 space-y-3">
                  {plan.features.map((ft) => (
                    <li key={ft} className="flex items-start gap-3 text-sm text-muted-foreground">
                      <Check className="mt-0.5 h-4 w-4 shrink-0 text-foreground" />
                      {ft}
                    </li>
                  ))}
                </ul>
                <Button asChild className={`group w-full rounded-full ${plan.popular ? "bg-foreground text-background hover:bg-foreground/90" : "border border-foreground/20 bg-transparent text-foreground hover:bg-foreground/5"}`}>
                  <Link href={plan.href}>
                    {plan.cta}
                    <ArrowRight className="ml-2 h-4 w-4 transition-transform group-hover:translate-x-1" />
                  </Link>
                </Button>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="relative border-t border-foreground/10 py-24 lg:py-32">
        <div className="mx-auto max-w-[1200px] px-6 text-center lg:px-12">
          <h2 className="mx-auto max-w-3xl font-display text-4xl leading-tight tracking-tight lg:text-6xl">
            Sẵn sàng cho kỳ thi tiếp theo?
          </h2>
          <p className="mx-auto mt-5 max-w-xl text-muted-foreground">
            Tham gia cùng cộng đồng sinh viên FPT trên Fuexam ngay hôm nay.
          </p>
          <div className="mt-8 flex flex-wrap justify-center gap-4">
            <Button asChild size="lg" className="group h-14 rounded-full bg-foreground px-8 text-base text-background hover:bg-foreground/90">
              <Link href="/register">
                Tạo tài khoản
                <ArrowRight className="ml-2 h-4 w-4 transition-transform group-hover:translate-x-1" />
              </Link>
            </Button>
            <Button asChild size="lg" variant="outline" className="h-14 rounded-full border-foreground/20 px-8 text-base hover:bg-foreground/5">
              <Link href="/suoc">Khám phá Source</Link>
            </Button>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-foreground/10 py-10">
        <div className="mx-auto flex max-w-[1200px] flex-col items-center justify-between gap-4 px-6 text-sm text-muted-foreground sm:flex-row lg:px-12">
          <div className="flex items-center gap-2">
            <span className="font-display text-lg tracking-tight text-foreground">Fuexam</span>
            <span className="font-mono text-[10px]">FPT</span>
          </div>
          <p>© {new Date().getFullYear()} Fuexam — Cộng đồng sinh viên FPT.</p>
          <div className="flex flex-wrap items-center justify-center gap-4">
            <Link href="/suoc" className="hover:text-foreground">Source</Link>
            <Link href="/forums" className="hover:text-foreground">Diễn đàn</Link>
            <Link href="/membership" className="hover:text-foreground">Membership</Link>
            <a
              href="https://www.facebook.com/groups/976684067613564"
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 hover:text-foreground"
            >
              <Facebook className="h-4 w-4" />
              Group 1
            </a>
            <a
              href="https://www.facebook.com/groups/720202890383681"
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 hover:text-foreground"
            >
              <Facebook className="h-4 w-4" />
              Group 2
            </a>
          </div>
        </div>
      </footer>
    </main>
  );
}