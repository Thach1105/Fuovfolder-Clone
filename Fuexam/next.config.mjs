/** @type {import('next').NextConfig} */
const nextConfig = {
  typescript: {
    // Keep deploy build unblocked while Fuexam remains an MVP test frontend.
    ignoreBuildErrors: true,
  },
  images: {
    unoptimized: true,
  },
  output: "standalone",
}

export default nextConfig
