import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  output: "standalone",
  // Avoid SegmentViewNode / React Client Manifest errors from Next devtools in dev (15.5.x).
  devIndicators: false,
};

export default nextConfig;
