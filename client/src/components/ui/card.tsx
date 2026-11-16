import type * as React from "react";

function join(base: string, optional?: string) {
  return [base, optional].filter(Boolean).join(" ");
}

export function Card({ className, ...props }: React.ComponentProps<"div">) {
  return <div data-slot="card" className={join("tm-card", className)} {...props} />;
}

export function CardHeader({ className, ...props }: React.ComponentProps<"div">) {
  return <div data-slot="card-header" className={join("tm-card-header", className)} {...props} />;
}

export function CardTitle({ className, ...props }: React.ComponentProps<"h3">) {
  return <h3 data-slot="card-title" className={join("tm-card-title", className)} {...props} />;
}

export function CardDescription({ className, ...props }: React.ComponentProps<"p">) {
  return <p data-slot="card-description" className={join("tm-card-description", className)} {...props} />;
}

export function CardContent({ className, ...props }: React.ComponentProps<"div">) {
  return <div data-slot="card-content" className={join("tm-card-content", className)} {...props} />;
}
