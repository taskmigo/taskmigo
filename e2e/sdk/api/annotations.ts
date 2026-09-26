/**
 * Marks an SDK API method that maps one-to-one to an OpenAPI operation.
 *
 * The method name must match the operation's `operationId`.
 */
export function OpenApi<This, Args extends unknown[], Return>(
  _method: (this: This, ...args: Args) => Return,
  _context: ClassMethodDecoratorContext<This, (this: This, ...args: Args) => Return>,
): void {
  // Marker decorator only; it intentionally does not change runtime behavior.
}

/**
 * Marks an SDK API convenience method that is not defined by an OpenAPI operation.
 */
export function Extension<This, Args extends unknown[], Return>(
  _method: (this: This, ...args: Args) => Return,
  _context: ClassMethodDecoratorContext<This, (this: This, ...args: Args) => Return>,
): void {
  // Marker decorator only; it intentionally does not change runtime behavior.
}
