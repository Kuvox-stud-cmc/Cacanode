FROM node:22-alpine AS dependencies
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:22-alpine AS build
WORKDIR /app
COPY --from=dependencies /app/node_modules node_modules
COPY . .
ARG NEXT_PUBLIC_API_BASE_URL=http://localhost/api/v1
ARG NEXT_PUBLIC_WIDGET_URL=http://localhost/widget/v1/cacanode-chat.js
ARG NEXT_PUBLIC_DEFAULT_LOCALE=vi-VN
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL
ENV NEXT_PUBLIC_WIDGET_URL=$NEXT_PUBLIC_WIDGET_URL
ENV NEXT_PUBLIC_DEFAULT_LOCALE=$NEXT_PUBLIC_DEFAULT_LOCALE
RUN npm run build

FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /app/package.json /app/package-lock.json ./
COPY --from=build /app/.next .next
COPY --from=build /app/public public
COPY --from=dependencies /app/node_modules node_modules
EXPOSE 3000
CMD ["npm", "run", "start"]
