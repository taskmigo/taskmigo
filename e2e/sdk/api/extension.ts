/**
 * Marks an SDK API method as a Taskmigo convenience that is not defined by an OpenAPI operation.
 *
 * Methods that map one-to-one to an OpenAPI operation must not use this decorator.
 */
export function extension<This, Args extends unknown[], Return>(
  _method: (this: This, ...args: Args) => Return,
  _context: ClassMethodDecoratorContext<This, (this: This, ...args: Args) => Return>,
): void {
  // Marker decorator only; it intentionally does not change runtime behavior.
}
