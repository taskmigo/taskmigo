---
name: java-javadoc
description: Write and review Javadoc for Java code, using Markdown documentation comments (JEP 467, JDK 23+) instead of the legacy HTML/`@tag` style. Use whenever the user asks to add, write, or fix Javadoc, document a class/method/field, or review Javadoc in a pull request. Also trigger when generating new public Java classes, interfaces, or services that should ship with documentation comments.
---

# Java Javadoc (Markdown style, JEP 467)

Guidance for writing concise, accurate Javadoc using Markdown documentation comments — the `///` style introduced by JEP 467 (JDK 23+) — instead of the legacy `/** ... */` HTML style.

## Baseline assumption

- Target JDK 23 or later. All doc comments in this skill use `///` (Markdown), not `/** */`.
- If the project is confirmed to be on JDK < 23 (can't use `///`), fall back to traditional `/** */` Javadoc but keep every other rule in this skill (what to document, tag usage, what not to write).

## General principles

1. **Only document public API that actually needs explaining.** Public classes/interfaces, public methods on service/repository/controller-style classes, and package-level docs are worth it. Trivial getters/setters, private methods, and self-explanatory names (`isEmpty()`, `getId()`) don't need a doc comment.
2. **The doc comment answers "why / what's the contract", not "what does the name already say".**
   - Bad: `/// Gets the user.`
   - Good: ``/// Returns the user currently authenticated in the request context, or `null` if no user is authenticated.``
3. **The first sentence is a self-contained summary sentence**, ending with a period — tools use it as the summary, so it must stand on its own.
4. **Don't restate what's already visible from an annotation or the signature.**
5. **Keep the doc comment in sync with the code.** If you change a signature or behavior, update the related doc comment in the same change — a wrong doc comment is worse than none.

## Markdown doc comment syntax (JEP 467)

- Use `///` on every line of the comment, immediately above the declaration — not `/**` / `*/`.
- Content is CommonMark Markdown. Use `_emphasis_` or `**bold**`, backtick `` `code spans` ``, and fenced ` ```java ` blocks instead of `<em>`, `{@code}`, and `<pre>`.
- Link to another type or member with Markdown reference syntax instead of `{@link}`:
  - `` [TypeName] `` — link to a type
  - `` [TypeName#method(ArgType)] `` — link to a member
  - `` [text][TypeName#method] `` — custom link text
- `@param`, `@return`, `@throws`, `@since`, `@see`, etc. are still supported — write them as plain Markdown lines at the end of the comment block, each still starting with `///`.
- A blank line inside the comment must still start with `///` (otherwise the comment ends there).

### Class / interface

```java
/// Manages the lifecycle of a [User], including creation, update, and soft
/// deletion.
///
/// All write operations run inside a transaction and publish the
/// corresponding domain event after commit.
public class UserService {
```

### Method

```java
/// Verifies an OTP code and activates the account; fails if the OTP has
/// expired or the retry limit has been exceeded.
///
/// @param userId the id of the user to activate
/// @param otpCode the OTP code entered by the user, not yet normalized
/// @return the activated user
/// @throws OtpExpiredException if the OTP has expired
/// @throws OtpAttemptExceededException if the retry limit was exceeded
public User activateAccount(UUID userId, String otpCode) {
```

- Describe the **business meaning** of each parameter/return value/exception, not just its type.
- Only list `@throws` for checked exceptions or runtime exceptions the caller actually needs to know about — don't list generic ones like `NullPointerException`.
- If a method's behavior is already obvious from its name and signature, skip the doc comment.

### Field / constant

Only document when the value or its reason for existing isn't obvious:

```java
/// Time-to-live for an OTP, in seconds.
public static final int OTP_TTL_SECONDS = 300;
```

## Framework-specific notes

- **REST controllers (Spring MVC, JAX-RS, etc.):** document business meaning and notable edge cases (idempotency, rate limits); don't restate the HTTP method/path already declared by the routing annotation.
- **Entities / persistence classes:** document business invariants (e.g. "email is stored blind-indexed, never in plaintext") rather than persistence mechanics already visible in annotations.
- **Null-safety annotations (e.g. JSpecify `@NullMarked`/`@Nullable`, or similar):** if a type or package already declares its nullability contract via annotations, don't restate it in prose ("may be null") — the annotation is the source of truth. Only add a doc comment note if there's a non-obvious *reason* something can be null.
- **`package-info.java`:** can also be Markdown-based and should describe the package's responsibility in one or two sentences.

## Things to avoid

- Empty template doc comments (`@param x the x`, `@return the result`).
- Adding doc comments to non-public members just "to have coverage".
- Mixing `///` and `/** */` style within the same file inconsistently — match whatever style the file/project already uses; only introduce `///` where none exists yet or where explicitly asked.
- Inventing business behavior you're not sure about — if the actual behavior of a method is unclear, ask instead of guessing and documenting it wrong.

## PR review checklist

1. Is the first line a self-contained summary sentence?
2. Does every parameter / return value / business-relevant exception have a tag?
3. Does anything restate information already in an annotation or the method name? → remove it.
4. Does the doc comment still match the current implementation?
