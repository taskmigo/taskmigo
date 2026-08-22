import { RootProvider } from 'fumadocs-ui/provider/next';
import type { Metadata } from 'next';

import './global.css';
import { Inter } from 'next/font/google';

import { siteUrl } from '@/lib/shared';

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl),
};

const inter = Inter({
  subsets: ['latin'],
});

export default function Layout({ children }: LayoutProps<'/'>) {
  return (
    <html lang='en' className={inter.className} suppressHydrationWarning>
      <body className='flex min-h-screen flex-col'>
        <RootProvider>{children}</RootProvider>
      </body>
    </html>
  );
}
