import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  async rewrites() {
    const gatewayUrl = process.env.GATEWAY_URL ?? "http://localhost:9000";
    return [
      {
        source: "/api/gateway/:path*",
        destination: `${gatewayUrl}/:path*`,
      },
    ];
  },
  images: {
    remotePatterns: [
      {
        protocol: "http",
        hostname: "localhost",
        port: "9002",
      },
    ],
  },
};

export default nextConfig;
