import { redirect } from "next/navigation";

import { getSession } from "@/auth";
import { DashboardShell } from "@/components/dashboard/dashboard-shell";

import "./dashboard.css";

export const dynamic = "force-dynamic";

export default async function DashboardLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  const session = await getSession();

  if (!session) {
    redirect("/api/auth/login?returnTo=/dashboard");
  }

  return <DashboardShell userName={session.user.name ?? session.user.id}>{children}</DashboardShell>;
}
