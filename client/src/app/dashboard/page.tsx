import { DashboardIcon } from "@/components/dashboard/dashboard-shell";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

// Illustrative values only. Keep dashboard actions disabled until backed by authorized APIs.
const metrics = [
  { label: "Open tickets", value: "128", delta: "+12 this week", icon: "tickets", tone: "blue" },
  { label: "In progress", value: "32", delta: "Across 6 projects", icon: "clock", tone: "violet" },
  { label: "Completed", value: "84", delta: "+18.6% this month", icon: "check", tone: "green" },
  { label: "Overdue", value: "7", delta: "Needs attention", icon: "alert", tone: "amber" },
] as const;

const projects = [
  { name: "Platform foundation", area: "Core services & APIs", completed: 38, total: 50, percent: 76, tone: "blue" },
  { name: "Workspace experience", area: "Navigation & collaboration", completed: 27, total: 50, percent: 54, tone: "violet" },
  { name: "Automation engine", area: "Rules & workflow triggers", completed: 8, total: 25, percent: 32, tone: "amber" },
] as const;

const activity = [
  { person: "Alex Morgan", initials: "AM", color: "violet", action: "moved", target: "TM-1042", detail: "to In progress", time: "12 min ago" },
  { person: "Jordan Lee", initials: "JL", color: "blue", action: "commented on", target: "TM-1038", detail: "", time: "1 hour ago" },
  { person: "Sam Rivera", initials: "SR", color: "green", action: "created", target: "TM-1035", detail: "", time: "3 hours ago" },
] as const;

const tickets = [
  { id: "TM-1042", title: "Improve ticket filtering experience", project: "Workspace", status: "In progress", priority: "High", assignee: "AM", color: "violet" },
  { id: "TM-1038", title: "Define reusable issue templates", project: "Platform", status: "In review", priority: "Medium", assignee: "JL", color: "blue" },
  { id: "TM-1035", title: "Investigate notification delivery", project: "Automation", status: "Todo", priority: "Medium", assignee: "SR", color: "green" },
  { id: "TM-1029", title: "Update workspace permission rules", project: "Platform", status: "Done", priority: "Low", assignee: "KL", color: "amber" },
] as const;

export default function DashboardPage() {
  return (
    <div className="tm-dashboard">
      <div className="tm-page-heading">
        <div>
          <div className="tm-eyebrow">YOUR WORKSPACE <span className="tm-eyebrow-dot">·</span> MONDAY, NOV 16</div>
          <h1>Good to see you <span className="tm-wave" aria-hidden="true">✳</span></h1>
          <p>Here&apos;s what&apos;s happening with your team today.</p>
        </div>
        <div className="tm-heading-actions">
          <Badge variant="secondary" className="tm-demo-badge">Sample data</Badge>
          <Button variant="outline" disabled>View projects</Button>
          <Button disabled><DashboardIcon name="plus" size={17} /> New ticket</Button>
        </div>
      </div>

      <section aria-label="Workspace metrics" className="tm-metrics">
        {metrics.map((metric) => (
          <Card key={metric.label} className="tm-metric">
            <CardContent>
              <div className="tm-metric-top">
                <span className="tm-metric-label">{metric.label}</span>
                <span className={`tm-metric-icon tm-tone-${metric.tone}`}>
                  <DashboardIcon name={metric.icon} size={19} />
                </span>
              </div>
              <div className="tm-metric-value">{metric.value}</div>
              <div className={`tm-metric-delta tm-delta-${metric.tone}`}>{metric.delta}</div>
            </CardContent>
          </Card>
        ))}
      </section>

      <div className="tm-two-columns">
        <Card>
          <CardHeader>
            <div><CardTitle>Project progress</CardTitle><CardDescription>Track how your active projects are moving.</CardDescription></div>
            <span className="tm-card-extra">3 active projects</span>
          </CardHeader>
          <CardContent className="tm-project-list">
            {projects.map((project) => (
              <div className="tm-project" key={project.name}>
                <div className="tm-project-top">
                  <div><strong>{project.name}</strong><span>{project.area}</span></div>
                  <div className="tm-project-total"><strong>{project.percent}%</strong><span>{project.completed}/{project.total} tasks</span></div>
                </div>
                <div className={`tm-progress-tone-${project.tone}`}>
                  <Progress value={project.percent} label={`${project.name}: ${project.percent}% complete`} />
                </div>
              </div>
            ))}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <div><CardTitle>Recent activity</CardTitle><CardDescription>Latest updates across your workspace.</CardDescription></div>
            <span className="tm-activity-live"><span /> Activity</span>
          </CardHeader>
          <CardContent>
            <ol className="tm-activity-list">
              {activity.map((event) => (
                <li key={event.target}>
                  <span className={`tm-person-avatar tm-avatar-${event.color}`}>{event.initials}</span>
                  <div><p><strong>{event.person}</strong> {event.action} <b>{event.target}</b> {event.detail}</p><span>{event.time}</span></div>
                </li>
              ))}
            </ol>
            <Button variant="outline" className="tm-activity-button" disabled>View all activity <DashboardIcon name="arrow" size={16} /></Button>
          </CardContent>
        </Card>
      </div>

      <Card className="tm-tickets-card">
        <CardHeader>
          <div><CardTitle>Recent tickets</CardTitle><CardDescription>A snapshot of work that matters right now.</CardDescription></div>
          <Button variant="outline" disabled>View all tickets <DashboardIcon name="arrow" size={15} /></Button>
        </CardHeader>
        <CardContent>
          <div className="tm-table-scroll">
            <Table>
              <TableHeader><TableRow>
                <TableHead>Ticket</TableHead><TableHead>Title</TableHead><TableHead>Project</TableHead>
                <TableHead>Status</TableHead><TableHead>Priority</TableHead><TableHead>Assignee</TableHead>
              </TableRow></TableHeader>
              <TableBody>{tickets.map((ticket) => (
                <TableRow key={ticket.id}>
                  <TableCell><span className="tm-ticket-id">{ticket.id}</span></TableCell>
                  <TableCell><span className="tm-ticket-title">{ticket.title}</span></TableCell>
                  <TableCell><span className="tm-table-muted">{ticket.project}</span></TableCell>
                  <TableCell><Badge variant={ticket.status === "Done" ? "success" : ticket.status === "In progress" ? "default" : "secondary"}>{ticket.status}</Badge></TableCell>
                  <TableCell><span className={`tm-priority tm-priority-${ticket.priority.toLowerCase()}`}><i />{ticket.priority}</span></TableCell>
                  <TableCell><span className={`tm-person-avatar tm-avatar-${ticket.color}`} aria-label={`Assignee ${ticket.assignee}`}>{ticket.assignee}</span></TableCell>
                </TableRow>
              ))}</TableBody>
            </Table>
          </div>
        </CardContent>
      </Card>

      <p className="tm-demo-footnote">Dashboard layout preview. Metrics, tickets, projects and activity are illustrative — not live Taskmigo data.</p>
    </div>
  );
}
