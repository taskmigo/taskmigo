import { createMDX } from 'fumadocs-mdx/next';

const withMDX = createMDX();

const isGithubPages = process.env.GITHUB_ACTIONS === 'true';
const repoName = process.env.GITHUB_REPOSITORY?.split('/')[1];
const repoOwner = process.env.GITHUB_REPOSITORY?.split('/')[0];
const isUserOrOrgPage = repoName?.endsWith('.github.io');
const basePath = isGithubPages && repoName && !isUserOrOrgPage ? `/${repoName}` : '';
const defaultSiteUrl =
  isGithubPages && repoOwner ? `https://${repoOwner}.github.io${basePath}` : 'http://localhost:3000';

/** @type {import('next').NextConfig} */
const config = {
  output: 'export',
  reactStrictMode: true,
  basePath,
  assetPrefix: basePath ? `${basePath}/` : undefined,
  env: {
    NEXT_PUBLIC_BASE_PATH: basePath,
    NEXT_PUBLIC_SITE_URL: process.env.NEXT_PUBLIC_SITE_URL || defaultSiteUrl,
  },
  serverExternalPackages: ['@takumi-rs/core'],
};

export default withMDX(config);
