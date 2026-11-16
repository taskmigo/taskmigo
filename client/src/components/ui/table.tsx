import type * as React from "react";

export function Table({ className, ...props }: React.ComponentProps<"table">) {
  return <table data-slot="table" className={["tm-table", className].filter(Boolean).join(" ")} {...props} />;
}

export function TableHeader(props: React.ComponentProps<"thead">) {
  return <thead data-slot="table-header" {...props} />;
}

export function TableBody(props: React.ComponentProps<"tbody">) {
  return <tbody data-slot="table-body" {...props} />;
}

export function TableRow(props: React.ComponentProps<"tr">) {
  return <tr data-slot="table-row" {...props} />;
}

export function TableHead(props: React.ComponentProps<"th">) {
  return <th data-slot="table-head" scope="col" {...props} />;
}

export function TableCell(props: React.ComponentProps<"td">) {
  return <td data-slot="table-cell" {...props} />;
}
