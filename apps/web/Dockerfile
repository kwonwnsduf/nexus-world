# syntax=docker/dockerfile:1.7
FROM node:24.14.0-alpine AS base
ENV PNPM_HOME=/pnpm
ENV PATH=$PNPM_HOME:$PATH
RUN corepack enable && corepack prepare pnpm@11.9.0 --activate
WORKDIR /workspace

FROM base AS deps
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/web/package.json apps/web/package.json
RUN pnpm install --frozen-lockfile

FROM deps AS test
COPY apps/web apps/web
RUN pnpm --filter @nexus-world/web lint \
 && pnpm --filter @nexus-world/web typecheck \
 && pnpm --filter @nexus-world/web test \
 && pnpm --filter @nexus-world/web build

FROM base AS runner
ENV NODE_ENV=production
ENV PORT=3000
ENV HOSTNAME=0.0.0.0
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs
COPY --from=test --chown=nextjs:nodejs /workspace/apps/web/.next/standalone ./
COPY --from=test --chown=nextjs:nodejs /workspace/apps/web/.next/static ./apps/web/.next/static
USER nextjs
EXPOSE 3000
CMD ["node", "apps/web/server.js"]

