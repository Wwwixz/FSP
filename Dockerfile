# DocHelper — один контейнер: бэкенд + собранный фронтенд на одном порту.
# Деплой, например, на Hugging Face Spaces (Docker): порт 7860, ссылка живёт постоянно.

# --- 1. Фронтенд (статика) ---
FROM node:22-alpine AS fe
WORKDIR /fe
COPY frontend/package*.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ .
RUN npm run build

# --- 2. Бэкенд (Spring Boot jar + статика фронта внутри) ---
FROM maven:3.9-eclipse-temurin-21 AS be
WORKDIR /be
COPY backend/pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY backend/src backend/src
# статика фронта внутрь jar: один origin, без CORS
COPY --from=fe /fe/dist backend/src/main/resources/static
RUN mvn -q -DskipTests package

# --- 3. Рантайм ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=be /be/target/docgen-backend.jar app.jar
# Hugging Face Spaces слушает 7860
ENV PORT=7860
# В дата-центрах HF сертификаты НУЦ Минцифры не в cacerts — включаем TrustAll.
# Ключ GigaChat задаётся секретом Space: AI_GIGACHAT_AUTH_KEY
ENV AI_GIGACHAT_INSECURE_SSL=true
EXPOSE 7860
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
