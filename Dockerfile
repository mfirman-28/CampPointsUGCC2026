# Etapa 1: Construcción (Build) usando Maven
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

# Copiar archivos del proyecto
COPY pom.xml .
COPY src ./src

# Compilar el proyecto creando el Fat JAR
RUN mvn clean package -DskipTests

# Etapa 2: Ejecución (Run) usando solo el JRE para ahorrar espacio
FROM eclipse-temurin:25-jre
WORKDIR /app

# Copiar el JAR generado en la etapa anterior
COPY --from=build /app/target/Campamento-1.0-SNAPSHOT-jar-with-dependencies.jar ./bot.jar

# Exponer el puerto para UptimeRobot
EXPOSE 8080

# Comando para ejecutar el bot
CMD ["java", "-jar", "bot.jar"]
