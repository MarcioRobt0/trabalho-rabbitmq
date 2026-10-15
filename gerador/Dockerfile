# ------------------------------------------------------------------------------
# Estágio 1: Build da aplicação utilizando Maven e OpenJDK 21
# ------------------------------------------------------------------------------
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copia pom.xml e baixa as dependências previamente para aproveitar o cache do Docker
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copia o código-fonte e compila o pacote JAR
COPY src ./src
RUN mvn clean package -DskipTests

# ------------------------------------------------------------------------------
# Estágio 2: Imagem final de execução com OpenJDK 21 Runtime (JRE)
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Usuário não-root por boas práticas de segurança
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copia o JAR executável gerado pelo estágio de compilação
COPY --from=build /app/target/gerador-1.0.0.jar app.jar

# Variáveis de ambiente padrão para conexão com o RabbitMQ no Docker Compose
ENV RABBITMQ_HOST=rabbitmq \
    RABBITMQ_PORT=5672 \
    RABBITMQ_USER=guest \
    RABBITMQ_PASS=guest \
    EXCHANGE_NAME=app.topic \
    MESSAGE_INTERVAL_MS=180

# Execução da aplicação
ENTRYPOINT ["java", "-jar", "app.jar"]
