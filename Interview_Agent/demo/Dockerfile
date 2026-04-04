FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw mvnw
RUN mvn -DskipTests dependency:go-offline

COPY src src
RUN mvn -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd -r appgroup && useradd -r -g appgroup appuser
RUN mkdir -p /app/data && chown -R appuser:appgroup /app

COPY --from=build /app/target/*.jar /app/app.jar

ENV PORT=8080
ENV JAVA_OPTS=""

EXPOSE 8080
USER appuser

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT} -jar /app/app.jar"]

