import { expect, test } from "#taskmigo-sdk";

test.describe("OAuth login", { tag: ["@auth", "@login"] }, () => {
  test("system user completes the deployed Authorization Code flow", { tag: "@smoke" }, async ({ taskmigo }) => {
    const username = await taskmigo.signIn();

    const session = await taskmigo.web.account.session();
    expect(session.authenticated).toBe(true);
    expect(session.user?.id).toBe(username);

    const sessionCookie = await taskmigo.web.account.sessionCookie();
    expect(sessionCookie).toBeDefined();
    expect(sessionCookie?.httpOnly).toBe(true);
  });
});
