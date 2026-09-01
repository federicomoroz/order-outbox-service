# Build context: ./dashboard (ver docker-compose.yml).
#
# Multi-stage igual que los dos servicios Java: una imagen para compilar y otra, minima, para
# servir. La imagen final no tiene Node ni node_modules — solo nginx y el bundle estatico.

FROM node:22-alpine AS build
WORKDIR /app

# Capa de dependencias separada de la de fuentes: editar un .tsx no invalida el npm ci.
COPY package.json package-lock.json ./
RUN npm ci

COPY tsconfig.json vite.config.ts index.html ./
COPY public ./public
COPY src ./src

# Las URLs de las APIs se hornean en el bundle en tiempo de build (asi funciona Vite), por eso
# llegan como ARG y no como variables de entorno del contenedor final.
ARG VITE_ORDER_API_URL=http://localhost:8006
ARG VITE_NOTIFICATION_API_URL=http://localhost:8007
ENV VITE_ORDER_API_URL=$VITE_ORDER_API_URL
ENV VITE_NOTIFICATION_API_URL=$VITE_NOTIFICATION_API_URL

RUN npm run build

FROM nginx:alpine
WORKDIR /usr/share/nginx/html

# curl para el healthcheck de docker-compose, mismo criterio que en los Dockerfile de Java.
RUN apk add --no-cache curl

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist ./

EXPOSE 8008
