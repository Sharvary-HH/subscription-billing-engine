FROM node:20-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build -- --configuration production

# nginx serves the static build and proxies /api to the backend so the browser only ever talks
# to one origin. BACKEND_URL is substituted into the config at start-up.
FROM nginx:1.27-alpine
COPY --from=build /src/dist/frontend/browser /usr/share/nginx/html
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
ENV BACKEND_URL=http://backend:8080
EXPOSE 80
