# Sistema de Puntos para Campamento
Un backend en Java puro con una interfaz de bot de Telegram para que los monitores de un campamento puedan asignar, restar y consultar puntos de niños (`kids`) y grupos (`camp_groups`) en tiempo real desde sus móviles.

## Stack
- Lenguaje: Java 25 LTS (Vanilla Java estricto y orientado a objetos).
- Framework / runtime: API de TelegramBots de Rubenlagus (**Versión 7.11.0 o superior, Arquitectura Modular**). **CERO Spring Boot**.
    - Dependencias usadas: `telegrambots-longpolling` (para escuchar) y `telegrambots-client` (para enviar).
    - Uso intensivo de *Inline Keyboards* (botones interactivos) mediante el patrón `Builder` (`SendMessage.builder()...build()`).
- Base de datos: MySQL 8.0 (en Docker) gestionado exclusivamente con **JDBC Puro** (`java.sql.*`).
- Pool de Conexiones: HikariCP (Implementado vía patrón Singleton en una clase estática).
- Gestor de dependencias: Maven.
- Seguridad: Variables de entorno (`.env`) o propiedades estáticas temporales para inyectar credenciales y tokens.

## Comandos
- `mvn clean compile` — compila el código fuente y descarga dependencias.
- `mvn exec:java -Dexec.mainClass="com.campamento.Main"` — arranca el servidor del bot en local.

## Estructura del proyecto (`src/main/java/com/campamento/`)
- `config/` — Setup de infraestructura: Inicialización de `HikariDataSource` (Pool de conexiones).
- `model/` — Entidades del dominio (POJOs equivalentes a `structs` en C: `Kid`, `Group`, `Monitor`, `Log`, `Config`). Sin lógica SQL.
- `dao/` — Interfaces y clases de persistencia. Es el **único lugar** donde está permitido escribir código SQL crudo (`PreparedStatement`, `ResultSet`).
- `service/` — El Cerebro. Lógica de negocio, validación de límites de puntos, manejo de transacciones ACID y coordinación de DAOs.
- `bot/` — Presentación. Controladores y parseadores de Telegram. Implementa `LongPollingSingleThreadUpdateConsumer`. Recibe `Update` y delega la lógica al `Service`. Usa el `TelegramClient` inyectado para responder.
- `dto/` — Objetos de transferencia de datos ligeros (ej. `RankingDTO`).
- `Main.java` — Archivo ensamblador (Fábrica). Único lugar donde se instancian DAOs, Servicios y el Bot para la inyección de dependencias manual.

## Convenciones
- **Arquitectura en 3 capas:** El flujo es siempre unidireccional y hacia abajo: `Bot` -> `Service` -> `DAO`. Nunca al revés.
- **Inversión de Dependencias:** Los servicios deben depender de Interfaces DAO (ej. `KidDAO`), nunca de la implementación técnica concreta (`KidDAOMySQL`).
- **Inyección por constructor:** Prohibido instanciar dependencias con `new` dentro de los Servicios o Controladores. Todo se inyecta a través del constructor desde `Main.java`.
- **Manejo de Recursos y Memoria:** Uso obligatorio del bloque `try-with-resources` para cerrar siempre `Connection`, `PreparedStatement` y `ResultSet` (prevención de fugas de memoria).
- **Patrón de Transacciones ACID:** El `Service` es quien pide la `Connection` al Pool de `DatabaseConfig`, apaga el autocommit (`conn.setAutoCommit(false)`), inyecta esa misma conexión como parámetro a los métodos del DAO (ej. actualizar `kids` e insertar en `logs`), y finalmente hace `conn.commit()` o `conn.rollback()`.
- **Seguridad:** Uso exclusivo de `PreparedStatement` para evitar Inyección SQL.

## No hagas
- **PROHIBIDO** usar Spring Boot, Spring Data, Hibernate, JPA o cualquier ORM. El objetivo formativo es dominar Java puro y JDBC a bajo nivel.
- **PROHIBIDO** usar anotaciones mágicas como `@Autowired`, `@Service`, o `@Component`. Todo el cableado de dependencias es manual en el `Main`.
- No uses la clase monolítica `TelegramLongPollingBot` (sintaxis de la versión 6). Usa la interfaz `LongPollingSingleThreadUpdateConsumer` y el `TelegramClient` (sintaxis de la versión 7+).
- No pongas sentencias SQL (`SELECT`, `UPDATE`) en los Controladores del Bot ni en los Servicios.
- No pongas lógica de validación de reglas de negocio en los DAOs (los DAOs son fontanería "tonta").
- No guardes estado temporal en RAM (listas o contadores en Java) si puede derivarse directamente de la base de datos vía SQL (excepto sesiones efímeras de UI como el modo incógnito o anti-spam).
- No permitas bajo ninguna circunstancia la ejecución de consultas SQL crudas introducidas por los usuarios a través del chat de Telegram.

## Matriz de Funcionalidades (Especificación del Sistema)

1. **Gestión de Roles de Interfaz (Admin vs Monitor):**
    - *Áreas:* `Bot`, `Service`, `DAO`.
    - *Solución:* El Bot valida el `telegram_id` del usuario consultando al `MonitorDAO`. Si `is_admin=true`, se despliega un panel ampliado; si no, la interfaz se limita a operaciones de puntos y rankings.

2. **Modificación de Ajustes Globales (`config`):**
    - *Áreas:* `Bot`, `Service`, `DAO`.
    - *Solución:* El Admin pulsa botones para alterar el límite diario o el modo estricto. El `Service` valida permisos y el `DAO` ejecuta un `UPDATE config SET ... WHERE id = 1`.

3. **Gestión de Monitores (Altas, Permisos y Roles):**
    - *Áreas:* `Bot`, `Service`, `DAO`.
    - *Solución:* Flujos guiados por texto/botones en el Bot. El `Service` valida duplicados de ID de Telegram y el `DAO` realiza inserciones y actualizaciones en la tabla `monitors`.

4. **Gestión de Campamento (Grupos y Niños):**
    - *Áreas:* `Bot`, `Service`, `DAO`.
    - *Solución:* Creación manual de registros relacionales. El `DAO` maneja los comandos `INSERT INTO camp_groups` e `INSERT INTO kids` asegurando la integridad referencial.

5. **Flujo de Puntos Dinámico (`global_points_enable`):**
    - *Áreas:* `Bot`, `Service`, `DAO`.
    - *Solución:* Si está activo (`true`), el bot muestra todos los grupos antes de listar los niños. Si está desactivado (`false`), filtra automáticamente los niños pertenecientes al grupo asignado al monitor logueado.

6. **Asignación Múltiple de Puntos (Batching):**
    - *Áreas:* `Bot`, `Service`, `DAO`.
    - *Solución:* El monitor selecciona múltiples niños mediante una interfaz interactiva de marcado. El `Service` empaqueta la orden y el `DAO` utiliza JDBC Batching (`addBatch` y `executeBatch`) para optimizar el rendimiento y asegurar atomicidad.

7. **Prevención de Clics Múltiples (Anti-Spam / Concurrencia de red):**
    - *Áreas:* `Bot`, `Service`.
    - *Solución:* Al pulsar un botón, el bot invoca inmediatamente a `answerCallbackQuery()` para apagar el icono de carga y edita el mensaje (desactivando los botones). En paralelo, un `ConcurrentHashMap` en el `Service` implementa un *rate limiter* temporal (bloqueo de 2 segundos por usuario).

8. **Modo Incógnito del Administrador:**
    - *Áreas:* `Bot`, `Service`.
    - *Solución:* Un mapa efímero en memoria RAM gestionado por el `Service` permite a un administrador alternar entre su interfaz avanzada y la vista estándar de monitor de forma temporal.

9. **Rankings de Campistas (Top 10 Global / Top 5 por Grupo):**
    - *Áreas:* `Bot`, `Service`, `DAO`, `DTO`.
    - *Solución:* El `DAO` ejecuta consultas optimizadas con cláusulas `ORDER BY points DESC LIMIT` y retorna objetos `RankingDTO`. El `Bot` formatea la salida con elementos visuales limpios.

10. **Ranking de Grupos (Puntuación Media):**
    - *Áreas:* `Bot`, `Service`, `DAO`, `DTO`.
    - *Solución:* El `DAO` emplea funciones de agregación SQL (`AVG(points)` con `JOIN` entre `kids` y `camp_groups`) para calcular la media aritmética de puntuación por grupo.

11. **Estadísticas de Actividad de Monitores:**
    - *Áreas:* `Bot`, `Service`, `DAO`, `DTO`.
    - *Solución:* Consultas analíticas sobre la tabla de auditoría (`logs`) agrupadas por monitor y fecha utilizando funciones de fecha de MySQL (`DATE(time)`).

12. **Auditoría Exhaustiva de Logs Diarios:**
    - *Áreas:* `Bot`, `Service`, `DAO`, `DTO`.
    - *Solución:* El Administrador solicita los registros filtrados por fecha. El `DAO` extrae los datos y, si el volumen es elevado, el `Bot` genera y envía un archivo de texto dinámico (`.txt`) para evitar desbordar el límite de caracteres de Telegram.

## Flujo de trabajo
- Antes de escribir código para una tarea no trivial, propón un plan (nombres de clases, métodos, firmas) y espera mi OK.
- Una tarea a la vez; al terminar, explícame qué hace el código usando analogías con C (memoria, punteros, structs) cuando sea posible.
- Aplica **Spec-Driven Development** en este orden estricto: 1. Crear Modelo (`struct`) -> 2. Crear Contrato (Interfaz DAO) -> 3. Implementar SQL (DAO Concreto) -> 4. Lógica de Negocio (Service) -> 5. Interfaz (Bot) -> 6. Conectar en `Main`.
- Al realizar ediciones de código en archivos existentes, utiliza tus herramientas de edición (Search & Replace / Diff) para modificar solo las líneas necesarias. No reescribas la clase entera en el chat.
- Si no estás seguro de la sintaxis exacta (especialmente de la librería Telegram API v7), detente y pregúntame. No inventes clases que no existen.

## Documentación del Dominio
- **Entidades (Tablas relacionales explícitas para MySQL 8.0):**
    - `camp_groups` (id, name) *(Nota: No usar 'groups' por ser palabra reservada)*.
    - `kids` (id, name, points, group_id).
    - `monitors` (id, name, telegram_id [BIGINT], group_id, is_admin, is_solo_monitor).
    - `logs` (id, kid_id, monitor_id, num_points, time [TIMESTAMP DEFAULT CURRENT_TIMESTAMP]).
    - `config` (id, daily_limit, global_points_enable) *(Nota: Tiene un `CHECK (id = 1)` para actuar como Singleton)*.
- **Reglas de Puntos (Gamificación):** Los monitores SOLO pueden dar 1, 2 o 3 puntos exactos. Se usarán *Inline Keyboards* en Telegram para que pulsen el valor sin tener que teclearlo.
- **Límites Anti-Abuso:** Existe un límite global alto de puntos diarios por monitor extraído de la tabla `config`. Si un monitor tiene `is_solo_monitor = true`, su límite base se multiplica por 2 en el Servicio.
- **Restricción de Grupos:** La tabla `config` tiene la variable booleana `global_points_enable`. Si es `true`, un monitor puede dar puntos a cualquiera. Si es `false`, el monitor **solo** puede dar puntos a los niños de su propio grupo.
- **El Super Admin:** El primer administrador del sistema se valida a través de una variable de entorno (`SUPER_ADMIN_TELEGRAM_ID`). Si el ID de Telegram entrante coincide con este valor, tiene acceso total para arrancar el sistema y registrar a otros monitores sin depender del estado inicial de la base de datos.