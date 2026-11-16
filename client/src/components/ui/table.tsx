import type * as React from "react";

export function Table({ className, ...properties }: React.ComponentProps<"table">) {
  return <table data-slot="table" className={["tm-table", className].filter(Boolean).join(" ")} {...properties} />;
}

export function TableHeader(properties: React.ComponentProps<"thead">) {
  return <thead data-slot="table-header" {...properties} />;
}

export function TableBody(properties: React.ComponentProps<"tbody">) {
  return <tbody data-slot="table-body" {...properties} />;
}

export function TableRow(properties: React.ComponentProps<"tr">) {
  return <tr data-slot="table-row" {...properties} />;
}

export function TableHead(properties: React.ComponentProps<"th">) {
  return <th data-slot="table-head" scope="col" {...properties} />;
}

export function TableCell(properties: React.ComponentProps<"td">) {
  return <td data-slot="table-cell" {...properties} />;
}
