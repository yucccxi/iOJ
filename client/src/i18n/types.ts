/** Keeps the dictionary shape while widening every literal string to `string`. */
export type Widen<T> = {
  [K in keyof T]: T[K] extends (...args: infer A) => infer R
    ? (...args: A) => R
    : T[K] extends object
      ? Widen<T[K]>
      : string;
};
