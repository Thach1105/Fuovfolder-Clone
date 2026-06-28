import React from "react"
import type { Metadata } from 'next'
import { Instrument_Sans, Instrument_Serif, JetBrains_Mono } from 'next/font/google'
import { Analytics } from '@vercel/analytics/next'
import { PendingProfileGate } from '@/components/auth/PendingProfileGate'
import { AuthProvider } from '@/lib/auth/AuthProvider'
import './globals.css'

const instrumentSans = Instrument_Sans({ 
  subsets: ["latin", "latin-ext"],
  variable: '--font-instrument'
});

const instrumentSerif = Instrument_Serif({ 
  subsets: ["latin", "latin-ext"],
  weight: "400",
  variable: '--font-instrument-serif'
});

const jetbrainsMono = JetBrains_Mono({ 
  subsets: ["latin"],
  variable: '--font-jetbrains'
});

export const metadata: Metadata = {
  title: 'Fuexam — Cộng đồng sinh viên FPT',
  description: 'Diễn đàn, tài liệu ôn thi (Source) và khóa học cho sinh viên FPT.',
  generator: 'Fuexam',
  icons: {
    icon: '/fa-icon.ico',
    shortcut: '/fa-icon.ico',
    apple: '/apple-icon.png',
  },
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="vi">
      <body className={`${instrumentSans.variable} ${instrumentSerif.variable} ${jetbrainsMono.variable} font-sans antialiased`}>
        <AuthProvider>
          <PendingProfileGate />
          {children}
        </AuthProvider>
        <Analytics />
      </body>
    </html>
  )
}