const RETURN_TO_BASE = new URL("https://taskmigo.invalid");

export function safeReturnTo(value: string | null, fallback: string): string {
  if (!value?.startsWith("/")) return fallback;

  try {
    return new URL(value, RETURN_TO_BASE).origin === RETURN_TO_BASE.origin ? value : fallback;
  } catch {
    return fallback;
  }
}
