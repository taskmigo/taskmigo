export function Progress({ value, label }: Readonly<{ value: number; label: string }>) {
  const percent = Math.max(0, Math.min(100, value));

  return (
    <div
      data-slot="progress"
      className="tm-progress"
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={percent}
    >
      <div className="tm-progress-indicator" style={{ width: `${percent}%` }} />
    </div>
  );
}
