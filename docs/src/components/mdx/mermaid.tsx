import { renderMermaidSVG } from 'beautiful-mermaid';

export async function Mermaid({ chart }: { chart: string }) {
  try {
    const svg = renderMermaidSVG(chart, {
      bg: 'var(--color-fd-background)',
      fg: 'var(--color-fd-foreground)',
      interactive: true,
      transparent: true,
    });

    return (
      <div
        className='my-6 w-full min-w-0 overflow-x-auto [&>svg]:block [&>svg]:h-auto [&>svg]:max-w-full'
        dangerouslySetInnerHTML={{ __html: svg }}
      />
    );
  } catch {
    throw new Error(`Failed to render Mermaid chart:\n\n${chart}`);
  }
}
