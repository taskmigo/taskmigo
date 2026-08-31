FROM node:26.7.0-alpine3.24@sha256:aadf416b2cdce311a8811ba3f0608a61b77dbf997500e2eafe781b51f6a0b019 AS build

WORKDIR /app

RUN npm install --global pnpm@11.24.0

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY packages/auth/package.json ./packages/auth/package.json
COPY packages/config/package.json ./packages/config/package.json
COPY packages/foundation/package.json ./packages/foundation/package.json
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm build

FROM node:26.7.0-alpine3.24@sha256:aadf416b2cdce311a8811ba3f0608a61b77dbf997500e2eafe781b51f6a0b019

ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1

WORKDIR /app

RUN addgroup -S -g 10001 taskmigo \
    && adduser -S -D -H -u 10001 -G taskmigo taskmigo

COPY --from=build --chown=taskmigo:taskmigo /app/.next/standalone ./
COPY --from=build --chown=taskmigo:taskmigo /app/.next/static ./.next/static

USER 10001:10001
EXPOSE 3000

CMD ["node", "server.js"]
