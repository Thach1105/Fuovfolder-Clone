import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./src/**/*.{js,ts,jsx,tsx,mdx}"],
  theme: {
    extend: {
      fontFamily: {
        sans: ["var(--font-sans)", "system-ui", "sans-serif"],
        display: ["var(--font-display)", "Georgia", "serif"],
      },
      colors: {
        // Warm neutral surface scale inspired by FuExam (oklch -> hex approximations).
        ink: {
          50: "#faf9f7",
          100: "#f3f1ec",
          200: "#e7e3da",
          300: "#d4cec1",
          400: "#a8a193",
          500: "#7c7568",
          600: "#5a5347",
          700: "#403a30",
          800: "#2a261f",
          900: "#1a1712",
        },
        // Brand accent retained for recognition.
        fuo: {
          50: "#eef6ff",
          100: "#d9ebff",
          200: "#bcddff",
          300: "#8ec8ff",
          400: "#59a8ff",
          500: "#3385ff",
          600: "#1a65f5",
          700: "#1350e1",
          800: "#1641b6",
          900: "#183a8f",
        },
      },
      borderRadius: {
        xl: "0.875rem",
        "2xl": "1.25rem",
      },
      boxShadow: {
        soft: "0 1px 2px rgba(26, 23, 18, 0.04), 0 8px 24px -12px rgba(26, 23, 18, 0.12)",
        lift: "0 12px 40px -16px rgba(26, 23, 18, 0.22)",
      },
      keyframes: {
        "fade-in-up": {
          "0%": { opacity: "0", transform: "translateY(12px)" },
          "100%": { opacity: "1", transform: "translateY(0)" },
        },
        "fade-in": {
          "0%": { opacity: "0" },
          "100%": { opacity: "1" },
        },
        shimmer: {
          "100%": { transform: "translateX(100%)" },
        },
      },
      animation: {
        "fade-in-up": "fade-in-up 0.5s cubic-bezier(0.22, 1, 0.36, 1) both",
        "fade-in": "fade-in 0.4s ease both",
      },
    },
  },
  plugins: [],
};

export default config;