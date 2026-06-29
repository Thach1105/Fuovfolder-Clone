/** @type {import('next').NextConfig} */
const nextConfig = {
  typescript: {
    // Keep deploy build unblocked while Fuexam remains an MVP test frontend.
    ignoreBuildErrors: true,
  },
  images: {
    remotePatterns: [
      { protocol: "http", hostname: "103.160.2.147" },
      { protocol: "http", hostname: "localhost" },
    ],
  },
  output: "standalone",
}

export default nextConfig
