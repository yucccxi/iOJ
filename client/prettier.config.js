// Everything else stays at Prettier's defaults, which the editor already formatted with.
/** @type {import("prettier").Config} */
export default {
  // Both plugins rewrite class strings and Prettier only runs the last one,
  // so merge chains them: sort first, then wrap to printWidth.
  plugins: [
    "prettier-plugin-tailwindcss",
    "prettier-plugin-classnames",
    "prettier-plugin-merge",
  ],
  // Without it the sorter doesn't know the theme's tokens and custom variants.
  tailwindStylesheet: "./src/index.css",
  tailwindAttributes: ["labelClassName"],
  customAttributes: ["labelClassName"],
};
