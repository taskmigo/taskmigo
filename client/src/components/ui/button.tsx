import type * as React from "react";

export function Button({
  className,
  variant = "default",
  size = "default",
  type = "button",
  ...properties
}: React.ComponentProps<"button"> & {
  variant?: "default" | "outline" | "ghost";
  size?: "default" | "sm" | "icon";
}) {
  return (
    <button
      data-slot="button"
      data-variant={variant}
      data-size={size}
      className={["tm-button", className].filter(Boolean).join(" ")}
      type={type}
      {...properties}
    />
  );
}
