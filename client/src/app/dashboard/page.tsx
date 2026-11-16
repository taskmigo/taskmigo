import {
  AlertTriangle,
  ArrowRight,
  CheckCheck,
  Clock3,
  Plus,
  Ticket,
} from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

const metrics = [
  { label: "Open tickets", value: "128", note: "+12 this week", icon: Ticket, color: "bg-indigo-50 text-indigo-600", noteColor: "text-emerald-600" },
  { label: "In progress", value: "32", note: "Across 6 projects", icon: Clock3, color: "bg-violet-50 text-violet-600", noteColor: "text-muted-foreground" },
  { label: "Completed", value: "84", note: "+18.6% this month", icon: CheckCheck, color: "bg-emerald-50 text-emerald-600", noteColor: "text-emerald-600" },
  { label: "Overdue", value: "7", note: "Needs attention", icon: AlertTriangle, color: "bg-amber-50 text-amber-600", noteColor: "text-amber-600" },
] as const;

const projects = [
  { name: "Platform foundation", description: "Core services & APIs", done: 38, total: 50, value: 76, color: "[&_[data-slot=progress-indicator]]:bg-indigo-500" },
  { name: "Workspace experience", description: "Navigation & collaboration", done: 27, total: 50, value: 54, color: "[&_[data-slot=progress-indicator]]:bg-violet-500" },
  { name: "Automation engine", description: "Rules & workflow triggers", done: 8, total: 25, value: 32, color: "[&_[data-slot=progress-indicator]]:bg-amber-500" },
] as const;

const activities = [
  { person: "Alex Morgan", initials: "AM", action: "moved", ticket: "TM-1042", description: "to In progress", time: "12 min ago", avatar: "bg-violet-100 text-violet-700" },
  { person: "Jordan Lee", initials: "JL", action: "commented on", ticket: "TM-1038", description: "", time: "1 hour ago", avatar: "bg-indigo-100 text-indigo-700" },
  { person: "Sam Rivera", initials: "SR", action: "created", ticket: "TM-1035", description: "", time: "3 hours ago", avatar: "bg-emerald-100 text-emerald-700" },
] as const;

const tickets = [
  { id: "TM-1042", title: "Improve ticket filtering experience", project: "Workspace", status: "In progress", priority: "High", assignee: "AM" },
  { id: "TM-1038", title: "Define reusable issue templates", project: "Platform", status: "In review", priority: "Medium", assignee: "JL" },
  { id: "TM-1035", title: "Investigate notification delivery", project: "Automation", status: "Todo", priority: "Medium", assignee: "SR" },
  { id: "TM-1029", title: "Update workspace permission rules", project: "Platform", status: "Done", priority: "Low", assignee: "KL" },
] as const;

const ticketStatusClasses = {
  "In progress": "border-indigo-100 bg-indigo-50 text-indigo-700",
  "In review": "border-violet-100 bg-violet-50 text-violet-700",
  Todo: "border-border bg-muted text-muted-foreground",
  Done: "border-emerald-100 bg-emerald-50 text-emerald-700",
} as const;

const priorityClasses = {
  High: "bg-orange-400",
  Medium: "bg-amber-400",
  Low: "bg-emerald-400",
} as const;

export default function DashboardPage() {
  return (
    <div className="space-y-6">
      <div className="flex flex-col justify-between gap-5 lg:flex-row lg:items-end">
        <div>
          <p className="text-[10px] font-bold tracking-[0.17em] text-muted-foreground">
            YOUR WORKSPACE · OVERVIEW
          </p>
          <h1 className="mt-2 text-3xl font-bold tracking-tight md:text-[34px]">Good to see you <span aria-hidden="true" className="text-amber-500">✳</span></h1>
          <p className="mt-2 text-sm text-muted-foreground">Here&apos;s what&apos;s happening with your team today.</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="secondary" className="mr-auto rounded-md text-[11px] lg:mr-1">Sample data</Badge>
          <Button variant="outline" disabled>View projects</Button>
          <Button disabled><Plus /> New ticket</Button>
        </div>
      </div>

      <section aria-label="Workspace metrics" className="grid grid-cols-2 gap-3 lg:grid-cols-4 lg:gap-5">
        {metrics.map(({ label, value, note, icon: Icon, color, noteColor }) => (
          <Card key={label} className="gap-0 py-5">
            <CardContent className="px-4 md:px-6">
              <div className="flex items-start justify-between gap-2">
                <span className="pt-1 text-xs font-semibold text-muted-foreground">{label}</span>
                <span className={`flex size-9 shrink-0 items-center justify-center rounded-lg ${color}`}>
                  <Icon className="size-[18px]" />
                </span>
              </div>
              <strong className="mt-1 block text-3xl font-bold tracking-tight tabular-nums md:text-4xl">{value}</strong>
              <p className={`mt-2 text-[11px] font-medium ${noteColor}`}>{note}</p>
            </CardContent>
          </Card>
        ))}
      </section>

      <div className="grid grid-cols-1 gap-5 xl:grid-cols-[1.2fr_1fr]">
        <Card>
          <CardHeader className="flex flex-row items-start justify-between gap-3">
            <div>
              <CardTitle className="text-[15px]">Project progress</CardTitle>
              <CardDescription className="mt-1">Track how your active projects are moving.</CardDescription>
            </div>
            <span className="shrink-0 pt-1 text-[11px] text-muted-foreground">3 active projects</span>
          </CardHeader>
          <CardContent className="space-y-7">
            {projects.map((project) => (
              <div key={project.name}>
                <div className="mb-3 flex items-start justify-between gap-3">
                  <div>
                    <p className="text-xs font-semibold">{project.name}</p>
                    <p className="mt-1 text-[11px] text-muted-foreground">{project.description}</p>
                  </div>
                  <div className="shrink-0 text-right">
                    <p className="text-xs font-semibold tabular-nums">{project.value}%</p>
                    <p className="mt-1 text-[11px] text-muted-foreground">{project.done}/{project.total} tasks</p>
                  </div>
                </div>
                <Progress value={project.value} aria-label={`${project.name}: ${project.value}% complete`} className={`h-2 bg-muted ${project.color}`} />
              </div>
            ))}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-start justify-between gap-3">
            <div>
              <CardTitle className="text-[15px]">Recent activity</CardTitle>
              <CardDescription className="mt-1">Latest updates across your workspace.</CardDescription>
            </div>
            <span className="flex shrink-0 items-center gap-2 pt-1 text-[11px] text-muted-foreground">
              <span className="size-1.5 rounded-full bg-emerald-500" /> Activity
            </span>
          </CardHeader>
          <CardContent>
            <ol className="space-y-5">
              {activities.map((activity) => (
                <li key={activity.ticket} className="flex items-start gap-3">
                  <span className={`flex size-8 shrink-0 items-center justify-center rounded-full text-[10px] font-bold ${activity.avatar}`}>
                    {activity.initials}
                  </span>
                  <div>
                    <p className="text-xs leading-relaxed text-muted-foreground">
                      <strong className="text-foreground">{activity.person}</strong> {activity.action}{" "}
                      <b className="text-indigo-600">{activity.ticket}</b> {activity.description}
                    </p>
                    <p className="mt-1 text-[11px] text-muted-foreground">{activity.time}</p>
                  </div>
                </li>
              ))}
            </ol>
            <Button variant="outline" className="mt-6 w-full" disabled>
              View all activity <ArrowRight className="size-4" />
            </Button>
          </CardContent>
        </Card>
      </div>

      <Card className="gap-3">
        <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-3">
          <div>
            <CardTitle className="text-[15px]">Recent tickets</CardTitle>
            <CardDescription className="mt-1">A snapshot of work that matters right now.</CardDescription>
          </div>
          <Button variant="outline" disabled>View all tickets <ArrowRight className="size-4" /></Button>
        </CardHeader>
        <CardContent className="px-4 md:px-6">
          <Table className="min-w-[690px] text-xs">
            <TableHeader className="bg-muted/50 text-muted-foreground">
              <TableRow>
                <TableHead>Ticket</TableHead>
                <TableHead>Title</TableHead>
                <TableHead>Project</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Priority</TableHead>
                <TableHead>Assignee</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {tickets.map((ticket) => (
                <TableRow key={ticket.id}>
                  <TableCell className="font-semibold text-indigo-600">{ticket.id}</TableCell>
                  <TableCell className="font-medium">{ticket.title}</TableCell>
                  <TableCell className="text-muted-foreground">{ticket.project}</TableCell>
                  <TableCell>
                    <Badge variant="outline" className={`rounded-md ${ticketStatusClasses[ticket.status]}`}>
                      {ticket.status}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <span className="inline-flex items-center gap-2 text-muted-foreground">
                      <span className={`size-1.5 rounded-full ${priorityClasses[ticket.priority]}`} />
                      {ticket.priority}
                    </span>
                  </TableCell>
                  <TableCell>
                    <span className="flex size-7 items-center justify-center rounded-full bg-primary/10 text-[10px] font-bold text-primary" aria-label={`Assignee ${ticket.assignee}`}>
                      {ticket.assignee}
                    </span>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <p className="text-center text-[11px] text-muted-foreground">
        Dashboard layout preview. Metrics, tickets, projects and activity are illustrative — not live Taskmigo data.
      </p>
    </div>
  );
}
