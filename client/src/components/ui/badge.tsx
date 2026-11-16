import type * as React from "react";

type BadgeVariant = "default" | "secondary" | "outline" | "success" | "warning";

export function Badge({
  className,
  variant = "default",
  ...properties
}: React.ComponentProps<"span"> & { variant?: BadgeVariant }) {
  return (
    <span
      data-slot="badge"
      data-variant={variant}
      className={["tm-badge", className].filter(Boolean).join(" ")}
      {...properties}
    />
  );
}
