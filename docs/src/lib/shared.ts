export const appName = 'Taskmigo';
export const docsRoute = '/docs';
export const docsImageRoute = '/og/docs';
export const docsContentRoute = '/llms.mdx/docs';
export const basePath = process.env.NEXT_PUBLIC_BASE_PATH ?? '';
export const siteUrl = process.env.NEXT_PUBLIC_SITE_URL ?? 'http://localhost:3000';

export function withBasePath(path: string) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return `${basePath}${normalizedPath}`;
}

export const gitConfig = {
  user: 'taskmigo',
  repo: 'taskmigo',
  branch: 'next',
  docsPath: 'docs/content/versions',
};
