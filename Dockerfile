FROM node:24-alpine AS frontend-build
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-21 AS backend-build
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
COPY backend/src backend/src
COPY --from=frontend-build /workspace/frontend/dist frontend/dist
RUN mvn --batch-mode --no-transfer-progress -f backend/pom.xml package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S poker && adduser -S poker -G poker
WORKDIR /app
COPY --from=backend-build /workspace/backend/target/xidao-poker-*.jar app.jar
USER poker
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--server.address=0.0.0.0"]
