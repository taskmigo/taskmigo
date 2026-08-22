interface FieldTypeProps {
  supportsExpression?: boolean;
  children: React.ReactNode;
}

export function FieldType({ supportsExpression = false, children }: FieldTypeProps) {
  if (supportsExpression) {
    return (
      <span className='flex items-center gap-2'>
        {children}
        {supportsExpression && <ExpressionTag />}
      </span>
    );
  }

  return children;
}

function ExpressionTag() {
  return (
    <span className='not-prose inline-flex items-center rounded-md bg-green-500/10 px-1.5 py-0.5 text-xs font-medium text-green-700 ring-1 ring-green-500/20 ring-inset dark:text-green-400 dark:ring-green-500/30'>
      Supports Expression
    </span>
  );
}
