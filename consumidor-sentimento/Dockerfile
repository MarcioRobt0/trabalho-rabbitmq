# ------------------------------------------------------------------------------
# Estágio 1: Build da aplicação utilizando Maven e OpenJDK 21
# ------------------------------------------------------------------------------
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copia pom.xml e fontes
COPY pom.xml .
COPY src ./src

# Compila o pacote JAR
RUN mvn clean package -DskipTests

# ------------------------------------------------------------------------------
# Estágio 2: Imagem final de execução com OpenJDK 21 Runtime (JRE)
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build /app/target/consumidor-sentimento-1.0.0.jar app.jar

ENV RABBITMQ_HOST=rabbitmq \
    RABBITMQ_PORT=5672 \
    RABBITMQ_USER=guest \
    RABBITMQ_PASS=guest \
    MAIN_EXCHANGE=app.topic \
    DLX_NAME=app.dlx

ENTRYPOINT ["java", "-jar", "app.jar"]
