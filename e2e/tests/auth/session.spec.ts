import { test } from "#taskmigo-sdk";

test.describe("Browser session", { tag: ["@auth", "@session"] }, () => {
  test("authenticated session survives a page reload", async ({ taskmigo }) => {
    const username = await taskmigo.signIn();

    await taskmigo.web.account.reload();

    await taskmigo.web.account.expectSignedIn(username);
  });
});
