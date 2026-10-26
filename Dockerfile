FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY api/pom.xml .
COPY ./api/src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar /app/
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]