/** @type {import('next').NextConfig} */
const nextConfig = {
  output: "standalone",
  typescript: {
    ignoreBuildErrors: true,
  },
  images: {
    remotePatterns: [
      { protocol: "http", hostname: "103.160.2.147" },
      { protocol: "http", hostname: "localhost" },
    ],
  },
}

export default nextConfig
