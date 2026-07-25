# 🏕️ Sistema de Puntos Campamento 2026

Bot de Telegram en Java para gestionar la puntuación de grupos y niños en un campamento de verano de forma centralizada. 🇺🇦 *La interfaz del bot y los mensajes están íntegramente en ucraniano, ya que fue diseñado específicamente para un campamento ucraniano.*

## 🌟 Características Principales (Features)
- **Modo SPA (Single Page Application):** Los mensajes en Telegram se editan dinámicamente, evitando inundar el historial de chat de basura.
- **Roles y Permisos:** Modo estricto (un monitor solo ve a su grupo) y modo global (todos pueden dar puntos a todos). Incluye límites de puntos diarios.
- **Panel de Administración Integrado:**
  - Aprobación de nuevas solicitudes de acceso para monitores.
  - Modificación del límite diario de puntos.
  - Activación/Desactivación del modo global.
  - Modo auditoría para consultar el historial de puntos repartidos.
- **Sistema Anti-apagado:** Servidor HTTP muy ligero integrado directamente en `Main.java` (sin usar Spring Boot) para conectar servicios como UptimeRobot y evitar que el bot se duerma en alojamientos gratuitos como Render.
- **Concurrencia Segura:** Uso de `ConcurrentHashMap` para gestión del estado en memoria y un Pool de Conexiones a Base de Datos (HikariCP).

## 🛠️ Tecnologías Utilizadas (Stack)
- **Lenguaje:** Java 25
- **Construcción:** Maven
- **Librería de Bot:** TelegramBots (Long Polling API)
- **Base de Datos:** MySQL puro con JDBC (Diseñado para integrarse en Aiven / AWS RDS)

## 🚀 Instalación Local

### Requisitos Previos
- Java 25+ instalado en tu máquina.
- Maven configurado en tus variables de entorno.
- Una base de datos MySQL 8+ accesible.
- Un bot registrado en Telegram a través de [@BotFather](https://t.me/BotFather).

### Variables de Entorno (Environment Variables)
El bot lee su configuración obligatoria a partir de variables de entorno de tu sistema operativo o plataforma en la nube:
```env
BOT_TOKEN=el_token_secreto_de_tu_bot
SUPER_ADMIN_TELEGRAM_ID=tu_numero_id_de_telegram
DB_URL=jdbc:mysql://tu_host:3306/nombre_bbdd?sslMode=REQUIRED
DB_USER=usuario
DB_PASS=contraseña
```

### Ejecutar el Código
Abre la terminal en la raíz del proyecto y ejecuta:
```bash
# Compilar el código
mvn clean install

# Arrancar el Bot
java -jar target/Campamento-1.0-SNAPSHOT-jar-with-dependencies.jar
```

## ☁️ Despliegue en Render
1. Sube este proyecto a tu repositorio de GitHub.
2. Crea un nuevo **Web Service** en Render conectándolo a tu repositorio.
3. Elige Java como entorno.
4. Comando de compilación (Build): `mvn clean install`
5. Comando de inicio (Start): `java -jar target/Campamento-1.0-SNAPSHOT-jar-with-dependencies.jar`
6. Añade las 5 variables de entorno requeridas en el panel de configuración de Render.
7. Registra la URL pública proporcionada por Render en **UptimeRobot** con pings cada 5 minutos.

## 👤 Autor
Desarrollado por **Mykhaylo Firman**.
