import { redirect } from "next/navigation";

import { getSession } from "@/auth";

export const dynamic = "force-dynamic";

export default async function AccountPage() {
  const session = await getSession();
  if (!session) redirect("/api/auth/login?returnTo=/account");

  return (
    <main>
      <h1>Account</h1>
      <p>Signed in as {session.user.name ?? session.user.id}</p>
      <form action="/api/auth/logout" method="post">
        <button type="submit">Sign out</button>
      </form>
    </main>
  );
}
