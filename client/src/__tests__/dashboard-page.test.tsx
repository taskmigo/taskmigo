// @vitest-environment jsdom

import { render, screen, within } from "@testing-library/react";
import { describe, expect, test } from "vitest";

import DashboardPage from "../app/dashboard/page";

describe("dashboard layout", () => {
  test("labels sample data and renders its main sections", () => {
    render(<DashboardPage />);

    expect(screen.getByRole("heading", { name: /good to see you/i })).toBeDefined();
    expect(screen.getByText("Sample data")).toBeDefined();
    expect(screen.getByRole("region", { name: "Workspace metrics" })).toBeDefined();
    expect(screen.getByText("Recent activity")).toBeDefined();

    const table = screen.getByRole("table");
    expect(within(table).getByText("TM-1042")).toBeDefined();
    expect(within(table).getAllByRole("row")).toHaveLength(5);
  });

  test("keeps actions without backend integration disabled", () => {
    render(<DashboardPage />);

    expect(screen.getByRole("button", { name: /new ticket/i }).hasAttribute("disabled")).toBe(true);
    expect(screen.getByRole("button", { name: /view all tickets/i }).hasAttribute("disabled")).toBe(true);
  });
});
