# syntax=docker/dockerfile:1
FROM node:24-bookworm-slim
ENV NODE_ENV=production
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=secret,id=proxy_ca \
    if [ -s /run/secrets/proxy_ca ]; then \
      NODE_EXTRA_CA_CERTS=/run/secrets/proxy_ca npm ci --omit=dev --strict-ssl=true; \
    else npm ci --omit=dev --strict-ssl=true; fi
COPY --chown=node:node src ./src
COPY --chown=node:node scripts ./scripts
COPY --chown=node:node migrations ./migrations
COPY --chown=node:node data ./data
COPY --chown=node:node vendor ./vendor
COPY --chown=node:node website ./website
COPY --chown=node:node admin-setup ./admin-setup
COPY --chown=node:node admin ./admin
USER node
EXPOSE 3000
CMD ["node", "src/processes/api.js"]
