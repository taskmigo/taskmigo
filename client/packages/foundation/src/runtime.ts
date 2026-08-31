export type Clock = () => number;

export const systemClock: Clock = () => Date.now();

export function globalSingleton<T>(key: symbol, create: () => T): T {
  if (Object.hasOwn(globalThis, key)) return Reflect.get(globalThis, key) as T;

  const value = create();
  Reflect.set(globalThis, key, value);
  return value;
}
