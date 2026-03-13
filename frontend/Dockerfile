# === Build stage ===
FROM node:20-alpine AS build
WORKDIR /app
# Installer dépendances (cache npm)
COPY package*.json ./
RUN npm ci --no-audit --no-fund --legacy-peer-deps
# Copier le code et builder
COPY . .
# Remplace "production" par l'env Angular cible si différent
RUN npm run build -- --configuration production

# === Runtime stage (Nginx) ===
FROM nginx:1.25-alpine
# gettext fournit envsubst (substitution du domaine dans nginx.conf)
RUN apk add --no-cache gettext
# Supprimer la conf par défaut
RUN rm /etc/nginx/conf.d/default.conf
# Copier le template nginx (${DOMAIN} sera substitué au démarrage via env.sh)
COPY nginx.conf /etc/nginx/conf.d/default.conf.template
# Copier le build Angular
COPY --from=build /app/dist/lhspla-ldc/* /usr/share/nginx/html/
# Copier le script d'injection des variables d'environnement Angular
COPY env.sh /env.sh
RUN chmod +x /env.sh
EXPOSE 443
ENTRYPOINT ["/env.sh"]
CMD ["nginx", "-g", "daemon off;"]

