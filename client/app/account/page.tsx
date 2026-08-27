import { redirect } from "next/navigation";

import { getAuth } from "@/auth";

export const dynamic = "force-dynamic";

export default async function AccountPage() {
  const session = await getAuth().getSession();
  if (!session) redirect("/api/auth/login?returnTo=/account");

  return (
    <main>
      <h1>Account</h1>
      <p>Signed in as {session.name ?? session.subject}</p>
      <form action="/api/auth/logout" method="post">
        <button type="submit">Sign out</button>
      </form>
    </main>
  );
}
