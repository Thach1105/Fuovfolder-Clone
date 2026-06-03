import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./src/**/*.{js,ts,jsx,tsx,mdx}"],
  theme: {
    extend: {
      colors: {
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
    },
  },
  plugins: [],
};

export default config;
