import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  transpilePackages: ["@taskmigo/auth", "@taskmigo/config"],
};

export default nextConfig;
