"use client";

import Link from "next/link";
import { useState } from "react";
import {
  Bell,
  CalendarDays,
  ChartNoAxesColumn,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  CircleHelp,
  FolderKanban,
  LayoutDashboard,
  Menu,
  Search,
  Settings2,
  Sparkles,
  Ticket,
  Users,
  X,
  type LucideIcon,
} from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

type NavigationItem = {
  label: string;
  icon: LucideIcon;
  available: boolean;
};

const navigationItems: NavigationItem[] = [
  { label: "Overview", icon: LayoutDashboard, available: true },
  { label: "My tickets", icon: Ticket, available: false },
  { label: "Projects", icon: FolderKanban, available: false },
  { label: "Calendar", icon: CalendarDays, available: false },
  { label: "Team", icon: Users, available: false },
  { label: "Reports", icon: ChartNoAxesColumn, available: false },
];

export function DashboardShell({ userName, children }: Readonly<{ userName: string; children: React.ReactNode }>) {
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const toggleSidebar = () => setCollapsed((current) => !current);

  return (
    <div className="min-h-screen bg-background font-sans text-foreground">
      {mobileOpen && (
        <button
          aria-label="Close navigation"
          className="fixed inset-0 z-40 bg-black/45 md:hidden"
          onClick={() => setMobileOpen(false)}
        />
      )}

      <aside
        aria-label="Main navigation"
        className={cn(
          "fixed inset-y-0 left-0 z-50 flex w-64 flex-col border-r border-border bg-card px-3 py-5 transition-transform duration-200 md:translate-x-0 md:transition-[width]",
          collapsed && "md:w-20",
          mobileOpen ? "translate-x-0" : "-translate-x-full",
        )}
      >
        <div className="flex h-11 items-center justify-between px-2">
          <Link
            href="/dashboard"
            className="flex min-w-0 items-center gap-3"
            aria-label="Taskmigo dashboard"
            onClick={() => setMobileOpen(false)}
          >
            <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-primary text-primary-foreground shadow-sm">
              <Sparkles className="size-5" />
            </span>
            <span className={cn("flex flex-col", collapsed && "md:hidden")}>
              <span className="text-xl leading-5 font-bold tracking-tight">taskmigo</span>
              <span className="mt-1 text-[9px] font-bold tracking-[0.19em] text-muted-foreground">WORKSPACE</span>
            </span>
          </Link>
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label="Close menu"
            className="md:hidden"
            onClick={() => setMobileOpen(false)}
          >
            <X />
          </Button>
        </div>

        <div
          className={cn(
            "mx-1 mt-8 mb-7 flex items-center gap-3 rounded-xl border bg-muted/50 p-2.5",
            collapsed && "md:hidden",
          )}
        >
          <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-violet-100 text-sm font-bold text-violet-700">
            T
          </span>
          <span className="flex flex-1 flex-col overflow-hidden">
            <span className="truncate text-xs font-semibold">Taskmigo Team</span>
            <span className="mt-0.5 text-[11px] text-muted-foreground">Free workspace</span>
          </span>
          <ChevronDown className="size-4 shrink-0 text-muted-foreground" />
        </div>

        <nav aria-label="Workspace" className={cn("flex flex-col gap-1 px-1", collapsed && "md:mt-8")}>
          <p
            className={cn(
              "mb-2 px-3 text-[10px] font-bold tracking-widest text-muted-foreground",
              collapsed && "md:hidden",
            )}
          >
            WORKSPACE
          </p>
          {navigationItems.map(({ label, icon: Icon, available }) =>
            available ? (
              <Link
                key={label}
                href="/dashboard"
                aria-current="page"
                title={label}
                onClick={() => setMobileOpen(false)}
                className={cn(
                  "flex h-10 items-center gap-3 rounded-lg bg-accent px-3 text-sm font-semibold text-accent-foreground",
                  collapsed && "md:justify-center",
                )}
              >
                <Icon className="size-[18px] shrink-0" />
                <span className={cn(collapsed && "md:hidden")}>{label}</span>
              </Link>
            ) : (
              <span
                key={label}
                title={`${label} — coming soon`}
                aria-disabled="true"
                className={cn(
                  "flex h-10 cursor-not-allowed items-center gap-3 rounded-lg px-3 text-sm text-muted-foreground opacity-70",
                  collapsed && "md:justify-center",
                )}
              >
                <Icon className="size-[18px] shrink-0" />
                <span className={cn(collapsed && "md:hidden")}>{label}</span>
              </span>
            ),
          )}
        </nav>

        <div className="mt-auto space-y-1 px-1">
          <div className={cn("px-2 pb-3", collapsed && "md:hidden")}>
            <Badge variant="secondary" className="text-[9px] tracking-wider">
              LAYOUT PREVIEW
            </Badge>
          </div>
          <span
            title="Help & support — coming soon"
            aria-disabled="true"
            className={cn(
              "flex h-10 cursor-not-allowed items-center gap-3 rounded-lg px-3 text-sm text-muted-foreground opacity-70",
              collapsed && "md:justify-center",
            )}
          >
            <CircleHelp className="size-[18px] shrink-0" />
            <span className={cn(collapsed && "md:hidden")}>Help & support</span>
          </span>
          <Link
            href="/account"
            title="Account settings"
            onClick={() => setMobileOpen(false)}
            className={cn(
              "flex h-10 items-center gap-3 rounded-lg px-3 text-sm text-muted-foreground hover:bg-muted",
              collapsed && "md:justify-center",
            )}
          >
            <Settings2 className="size-[18px] shrink-0" />
            <span className={cn(collapsed && "md:hidden")}>Account settings</span>
          </Link>
          <Button
            variant="ghost"
            onClick={toggleSidebar}
            aria-label={collapsed ? "Expand navigation" : "Collapse navigation"}
            aria-expanded={!collapsed}
            className="mt-3 hidden w-full justify-start border-t text-muted-foreground md:flex"
          >
            {collapsed ? <ChevronRight className="size-4" /> : <ChevronLeft className="size-4" />}
            {!collapsed && <span>Collapse sidebar</span>}
          </Button>
        </div>
      </aside>

      <div className={cn("min-w-0 transition-[padding] duration-200 md:pl-64", collapsed && "md:pl-20")}>
        <header className="sticky top-0 z-30 flex h-[72px] items-center justify-between gap-4 border-b bg-card px-4 md:px-9">
          <div className="flex min-w-0 items-center gap-3">
            <Button
              variant="ghost"
              size="icon"
              aria-label="Open navigation"
              className="md:hidden"
              onClick={() => setMobileOpen(true)}
            >
              <Menu />
            </Button>
            <div className="flex items-center gap-3 text-sm">
              <span className="text-muted-foreground">Workspace</span>
              <span className="text-muted-foreground/50">/</span>
              <strong className="font-semibold">Overview</strong>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              disabled
              className="hidden w-52 justify-start gap-2 text-muted-foreground lg:flex"
            >
              <Search className="size-4" /> Search workspace
            </Button>
            <Button variant="ghost" size="icon" disabled aria-label="Notifications — coming soon">
              <Bell />
            </Button>
            <div className="mx-1 h-7 w-px bg-border" />
            <Link href="/account" aria-label={`Account: ${userName}`} className="flex items-center gap-2">
              <span className="flex size-8 items-center justify-center rounded-full bg-primary/10 text-xs font-bold text-primary">
                {userName.charAt(0).toUpperCase()}
              </span>
              <span className="hidden max-w-32 truncate text-xs font-semibold lg:inline">{userName}</span>
              <ChevronDown className="hidden size-3 text-muted-foreground lg:inline" />
            </Link>
          </div>
        </header>
        <main className="mx-auto w-full max-w-[1540px] p-4 md:p-8 lg:p-10">{children}</main>
      </div>
    </div>
  );
}
