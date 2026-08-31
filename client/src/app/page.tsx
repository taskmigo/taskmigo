import Link from "next/link";

export default function Home() {
  return (
    <main>
      <h1>Taskmigo</h1>
      <p>
        <Link href="/api/auth/login?returnTo=/account">Sign in</Link>
      </p>
      <p>
        <Link href="/account">Account</Link>
      </p>
    </main>
  );
}
