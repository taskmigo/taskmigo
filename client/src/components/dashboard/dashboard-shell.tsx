"use client";

import Link from "next/link";
import { useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

type IconName =
  | "logo"
  | "overview"
  | "tickets"
  | "projects"
  | "calendar"
  | "team"
  | "reports"
  | "settings"
  | "help"
  | "menu"
  | "bell"
  | "search"
  | "user"
  | "plus"
  | "arrow"
  | "up"
  | "down"
  | "check"
  | "clock"
  | "alert";

const paths: Record<IconName, string> = {
  logo: "M12 2.5 14.2 9.8 21.5 12 14.2 14.2 12 21.5 9.8 14.2 2.5 12 9.8 9.8 12 2.5Z",
  overview: "M3 3h8v8H3z M14 3h7v5h-7z M14 11h7v10h-7z M3 14h8v7H3z",
  tickets: "M4 4h16v16H4z M8 9h8 M8 13h8 M8 17h5",
  projects: "M3 7h7l2 2h9v11H3z M3 7V5h7l2 2",
  calendar: "M4 5h16v16H4z M8 3v4 M16 3v4 M4 10h16 M8 14h3 M14 14h3",
  team: "M9 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M2 20v-2a7 7 0 0 1 14 0v2 M17 4a4 4 0 0 1 0 8 M18 15a6 6 0 0 1 4 5",
  reports: "M4 20V11 M10 20V4 M16 20v-8 M22 20H2",
  settings:
    "M12 3v3 M12 18v3 M3 12h3 M18 12h3 M5.6 5.6l2.1 2.1 M16.3 16.3l2.1 2.1 M18.4 5.6l-2.1 2.1 M7.7 16.3l-2.1 2.1 M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0z",
  help: "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z M9.5 9a2.5 2.5 0 1 1 4 2c-1 .7-1.5 1.4-1.5 2.5 M12 17h.01",
  menu: "M4 7h16 M4 12h16 M4 17h16",
  bell: "M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9 M10 21h4",
  search: "M16 16l5 5 M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0z",
  user: "M12 13a5 5 0 1 0 0-10 5 5 0 0 0 0 10z M3 21a9 9 0 0 1 18 0",
  plus: "M12 5v14 M5 12h14",
  arrow: "M5 12h14 M13 6l6 6-6 6",
  up: "M6 17 17 6 M8 6h9v9",
  down: "M6 7 17 18 M17 9v9H8",
  check: "M4 12l5 5L20 6",
  clock: "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z M12 6v6l4 2",
  alert: "M12 3 2 21h20L12 3z M12 9v5 M12 18h.01",
};

export function DashboardIcon({ name, size = 19 }: Readonly<{ name: IconName; size?: number }>) {
  return (
    <svg
      aria-hidden="true"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d={paths[name]} />
    </svg>
  );
}

const navItems: ReadonlyArray<{ label: string; icon: IconName; available: boolean }> = [
  { label: "Overview", icon: "overview", available: true },
  { label: "My tickets", icon: "tickets", available: false },
  { label: "Projects", icon: "projects", available: false },
  { label: "Calendar", icon: "calendar", available: false },
  { label: "Team", icon: "team", available: false },
  { label: "Reports", icon: "reports", available: false },
];

export function DashboardShell({ userName, children }: Readonly<{ userName: string; children: React.ReactNode }>) {
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <div className={`tm-app${collapsed ? " tm-app-collapsed" : ""}${mobileOpen ? " tm-app-mobile-open" : ""}`}>
      {mobileOpen && (
        <button className="tm-backdrop" aria-label="Close navigation" onClick={() => setMobileOpen(false)} />
      )}
      <aside className="tm-sidebar" aria-label="Main navigation">
        <div className="tm-sidebar-top">
          <Link
            href="/dashboard"
            className="tm-brand"
            aria-label="Taskmigo dashboard"
            onClick={() => setMobileOpen(false)}
          >
            <span className="tm-brand-icon">
              <DashboardIcon name="logo" size={21} />
            </span>
            <span className="tm-hide-when-collapsed">
              <strong>taskmigo</strong>
              <small>WORKSPACE</small>
            </span>
          </Link>
          <button className="tm-mobile-close" aria-label="Close menu" onClick={() => setMobileOpen(false)}>
            ×
          </button>
        </div>
        <div className="tm-workspace-select tm-hide-when-collapsed">
          <span className="tm-workspace-avatar">T</span>
          <span>
            <strong>Taskmigo Team</strong>
            <small>Free workspace</small>
          </span>
          <span className="tm-workspace-chevron">⌄</span>
        </div>
        <nav className="tm-nav" aria-label="Workspace">
          <div className="tm-nav-label tm-hide-when-collapsed">WORKSPACE</div>
          {navItems.map(({ label, icon, available }) =>
            available ? (
              <Link
                key={label}
                href="/dashboard"
                aria-current="page"
                className="tm-nav-item tm-nav-active"
                title={label}
                onClick={() => setMobileOpen(false)}
              >
                <DashboardIcon name={icon} />
                <span className="tm-hide-when-collapsed">{label}</span>
              </Link>
            ) : (
              <span
                key={label}
                className="tm-nav-item tm-nav-unavailable"
                aria-disabled="true"
                title={`${label} — coming soon`}
              >
                <DashboardIcon name={icon} />
                <span className="tm-hide-when-collapsed">{label}</span>
              </span>
            ),
          )}
        </nav>
        <div className="tm-sidebar-bottom">
          <Badge variant="secondary" className="tm-preview-label tm-hide-when-collapsed">
            LAYOUT PREVIEW
          </Badge>
          <span className="tm-nav-item tm-nav-unavailable" title="Help — coming soon" aria-disabled="true">
            <DashboardIcon name="help" />
            <span className="tm-hide-when-collapsed">Help & support</span>
          </span>
          <Link href="/account" className="tm-nav-item" title="Account settings" onClick={() => setMobileOpen(false)}>
            <DashboardIcon name="settings" />
            <span className="tm-hide-when-collapsed">Account settings</span>
          </Link>
          <button
            className="tm-collapse"
            aria-label={collapsed ? "Expand navigation" : "Collapse navigation"}
            aria-expanded={!collapsed}
            onClick={() => setCollapsed(!collapsed)}
          >
            <span>{collapsed ? "›" : "‹"}</span>
            <span className="tm-hide-when-collapsed">Collapse sidebar</span>
          </button>
        </div>
      </aside>
      <div className="tm-main">
        <header className="tm-header">
          <div className="tm-header-left">
            <button className="tm-menu-trigger" aria-label="Open navigation" onClick={() => setMobileOpen(true)}>
              <DashboardIcon name="menu" />
            </button>
            <div className="tm-breadcrumb">
              <span>Workspace</span>
              <span className="tm-crumb-divider">/</span>
              <strong>Overview</strong>
            </div>
          </div>
          <div className="tm-header-right">
            <Button variant="outline" className="tm-search" disabled>
              <DashboardIcon name="search" size={16} /> Search workspace <span className="tm-keyboard">⌘ K</span>
            </Button>
            <Button variant="ghost" size="icon" aria-label="Notifications — coming soon" disabled>
              <DashboardIcon name="bell" />
            </Button>
            <div className="tm-header-divider" />
            <Link href="/account" className="tm-account" aria-label={`Account: ${userName}`}>
              <span className="tm-account-avatar">
                <DashboardIcon name="user" size={17} />
              </span>
              <span className="tm-account-name">{userName}</span>
              <span className="tm-account-caret">⌄</span>
            </Link>
          </div>
        </header>
        <main className="tm-content">{children}</main>
      </div>
    </div>
  );
}
