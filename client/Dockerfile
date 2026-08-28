FROM node:26.7.0-bookworm-slim@sha256:cd565714d4da3e84bfd341e31448f81d47c6362198f152345297c9c1154e6341 AS build

WORKDIR /app

RUN npm install --global pnpm@11.24.0

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY packages/auth/package.json ./packages/auth/package.json
COPY packages/config/package.json ./packages/config/package.json
COPY packages/foundation/package.json ./packages/foundation/package.json
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm build

FROM node:26.7.0-bookworm-slim@sha256:cd565714d4da3e84bfd341e31448f81d47c6362198f152345297c9c1154e6341

ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1

WORKDIR /app

RUN groupadd --system --gid 10001 taskmigo \
    && useradd --system --uid 10001 --gid taskmigo --home-dir /app --shell /usr/sbin/nologin taskmigo

COPY --from=build --chown=taskmigo:taskmigo /app/.next/standalone ./
COPY --from=build --chown=taskmigo:taskmigo /app/.next/static ./.next/static

USER 10001:10001
EXPOSE 3000

CMD ["node", "server.js"]
