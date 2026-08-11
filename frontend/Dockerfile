# ---- Build stage ----
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# Vite inlines VITE_* variables into the JS bundle at build time — they can't be changed
# at runtime like the backend's env vars, so they have to be passed in as build args and
# exported before `vite build` runs (Vite reads them straight from process.env).
ARG VITE_API_BASE_URL
ARG VITE_GOOGLE_CLIENT_ID
ARG VITE_STRIPE_PUBLISHABLE_KEY
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL \
    VITE_GOOGLE_CLIENT_ID=$VITE_GOOGLE_CLIENT_ID \
    VITE_STRIPE_PUBLISHABLE_KEY=$VITE_STRIPE_PUBLISHABLE_KEY
RUN npm run build

# ---- Run stage ----
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html

# Dropped into templates/ (not conf.d/ directly) so the base image's own entrypoint
# script runs it through envsubst at container start, substituting ${PORT} — Railway
# (like most PaaS hosts) assigns the listen port at runtime rather than letting you fix
# one, same reason Hotel-system's application.yml reads server.port from ${PORT:8080}.
# The official entrypoint only substitutes names that are actual env vars, so nginx's
# own $uri/$host/... runtime variables in the config are left alone.
COPY nginx.conf /etc/nginx/templates/default.conf.template
ENV PORT=80

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
