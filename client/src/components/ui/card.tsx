import type * as React from "react";

function join(base: string, optional?: string) {
  return [base, optional].filter(Boolean).join(" ");
}

export function Card({ className, ...properties }: React.ComponentProps<"div">) {
  return <div data-slot="card" className={join("tm-card", className)} {...properties} />;
}

export function CardHeader({ className, ...properties }: React.ComponentProps<"div">) {
  return <div data-slot="card-header" className={join("tm-card-header", className)} {...properties} />;
}

export function CardTitle({ className, ...properties }: React.ComponentProps<"h3">) {
  return <h3 data-slot="card-title" className={join("tm-card-title", className)} {...properties} />;
}

export function CardDescription({ className, ...properties }: React.ComponentProps<"p">) {
  return <p data-slot="card-description" className={join("tm-card-description", className)} {...properties} />;
}

export function CardContent({ className, ...properties }: React.ComponentProps<"div">) {
  return <div data-slot="card-content" className={join("tm-card-content", className)} {...properties} />;
}
