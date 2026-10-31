Nombre: Naser Daniel Martínez Morales
Carnet: 0905-23-3623
Repositorio: https://github.com/HydroGest-Team/hidrogest-sync-service
Modulo asignado: Replicación asíncrona a NoSQL

---

# HidroGest Sync Service

Microservicio de replicación asíncrona que copia los datos del monolito HidroGest (MySQL) hacia MongoDB, sin tocar ni modificar el sistema original.

---

## Arquitectura General de la Solución Integrada (Fase 2)

La Fase 2 de HidroGest añade cuatro microservicios independientes sobre el monolito heredado. Cada uno corre en su propio proceso y puerto; ninguno modifica el código ni la base de datos del monolito.

### Componentes de la solución

| Componente | Stack | Puerto | Rol |
|---|---|---|---|
| **Monolito HidroGest** | Laravel · MySQL | :8080 | Sistema principal — clientes, lecturas, pagos, auth |
| **Consulta Pública + Captcha** | Next.js / PHP · Captcha API | :3000 | Permite consultar estado de cuenta sin login |
| **Chatbot con RAG** | Python / Node.js · LLM · VectorDB | :8082 | Chatbot con recuperación de contexto sobre la oficina del agua |
| **Pagos por Colas** | Java / Node.js · RabbitMQ/SQS · PSP | :8083 | Procesa pagos de forma asíncrona hacia sistemas externos |
| **Replicación Asíncrona a NoSQL** ★ | Spring Boot · Java 17 · MongoDB | :8081 | **Este repositorio** — replica datos MySQL → MongoDB |

### Diagrama de arquitectura

<img width="1536" height="1024" alt="Arquitectura de Sincronización Asíncrona HidroGest" src="https://github.com/user-attachments/assets/6be963c1-2039-444b-b58c-332f813e93eb" />

### Integración entre componentes

- El monolito **no se modifica** en ningún caso. Cada microservicio lo complementa de forma externa.
- La **consulta pública** llama a los endpoints de lectura del monolito (o accede a su MySQL de forma controlada) y valida el captcha con un servicio externo.
- El **chatbot RAG** indexa documentos de la oficina del agua en una base de datos vectorial y consulta un LLM para generar respuestas contextualizadas.
- El **servicio de pagos** recibe eventos de pago del monolito a través de una cola de mensajes y los reenvía a la pasarela de cobro externa; la cola garantiza que un fallo en la pasarela no bloquea la aplicación principal.
- El **sync service (este módulo)** lee directamente la base de datos MySQL del monolito (`hydrogest`) usando JDBC de solo lectura y escribe en MongoDB. No expone ningún endpoint hacia el monolito; trabaja completamente de forma independiente.

### Ejecución del sistema completo

Cada microservicio define su propio `docker-compose.yml`. Para levantar solo este módulo:

```bash
# 1. Levantar MongoDB
docker compose up -d

# 2. Correr el microservicio
./mvnw spring-boot:run
```

Para la solución integrada completa, cada equipo levanta su servicio por separado. El monolito debe estar corriendo en `:8080` con MySQL en `:3307` antes de iniciar el sync service.

---

## Módulo: Replicación Asíncrona a NoSQL ★

### 4.1 Funcionalidad y objetivo

**Problema que resuelve:** El monolito HidroGest usa MySQL como única fuente de datos. Hacer reportes analíticos, búsquedas complejas o integraciones externas directamente sobre esa base de datos en producción es riesgoso: cualquier consulta pesada compite con las lecturas del sistema operativo. Además, MySQL no es ideal para consultas de tipo documento o para escenarios donde se necesita una copia independiente de los datos.

**Solución:** Este microservicio mantiene una réplica actualizada de los datos de HidroGest en MongoDB. Cualquier sistema de reportería, analítica o integración externa puede consultar MongoDB sin tocar el MySQL de producción.

**Cómo funciona:**

1. Un cliente (Swagger, dashboard o cualquier sistema) llama a `POST /sync`.
2. El servicio devuelve un `jobId` de inmediato (respuesta 202 Accepted) y lanza un hilo de fondo.
3. El hilo consulta en MongoDB cuándo fue el último sync exitoso (`sync_metadata`).
4. Lee de MySQL **solo los registros nuevos o modificados** desde ese timestamp (`WHERE updated_at > ?`). Esto es delta sync — no descarga toda la tabla cada vez.
5. Inserta o actualiza (upsert) cada registro en MongoDB. Nunca borra datos ya replicados.
6. Al finalizar, guarda el nuevo timestamp en `sync_metadata`.
7. El cliente puede consultar el progreso en tiempo real con `GET /sync/{jobId}` (retorna 0–100).
8. `GET /sync/status` da el estado global: si hay un sync activo, cuántos documentos hay en cada colección y cuándo fue el último sync.

Si se llama a `POST /sync` mientras hay uno en curso, el servicio responde `409 Conflict` — hay protección contra ejecuciones concurrentes.

**Tablas replicadas:**

- `tb_clientes` · `tb_contadores` · `tb_tarifas` · `tb_usuarios` · `tb_lecturas` · `tb_pagos`

### 4.2 Integración y responsabilidades

| Frontera | Detalle |
|---|---|
| **MySQL (monolito)** | El sync service accede con un usuario de **solo lectura**. Nunca escribe ni modifica datos del monolito. La conexión es directa vía JDBC (`JdbcTemplate`). |
| **MongoDB** | Es el destino exclusivo de escritura. El sync service hace `replaceOne` con `upsert=true` — si el registro existe lo actualiza, si no existe lo crea. Nunca llama a `deleteOne`. |
| **Monolito HidroGest** | **No se modifica**. El sync service no requiere ningún cambio en el monolito: no agrega endpoints, no instala librerías, no requiere configuración del lado del monolito. |
| **Sistemas externos** | El sync service no se comunica con ningún sistema externo más allá de MySQL y MongoDB. No hay dependencia de Internet. |
| **Swagger UI / Dashboard** | El punto de entrada es Swagger (`/swagger-ui/index.html`) o el dashboard estático (`/dashboard.html`), ambos en el mismo puerto 8081. |

### 4.3 Decisión técnica

**Decisión: MongoDB en lugar de DynamoDB**

El enunciado del módulo menciona replicación asíncrona a NoSQL. La opción natural de AWS es DynamoDB, pero se eligió **MongoDB por las siguientes razones:**

| Criterio | DynamoDB | MongoDB (elegida) |
|---|---|---|
| Requiere cuenta AWS | Sí | No |
| Costo en desarrollo | Puede generar costos si se excede free tier | Gratis con Docker |
| Instalación local | `aws-sdk` + credenciales IAM | `docker compose up -d` (una línea) |
| Configuración | Variables de entorno AWS, región, tabla | Una URI: `mongodb://localhost:27017/...` |
| Concepto NoSQL | Documentos | Documentos (mismo modelo) |

MongoDB ejecuta el **mismo concepto** de base de datos de documentos NoSQL que DynamoDB, pero con cero fricción en entorno local. Esto permite que cualquier integrante del equipo levante el entorno sin necesidad de una cuenta AWS, acelerando el desarrollo.

**Decisión: patrón async job (jobId + polling)**

Se eligió el patrón de job asíncrono en lugar de una respuesta HTTP de larga duración porque:
- Una sincronización de cientos de miles de registros puede tardar varios minutos.
- Un timeout HTTP a los 30 s rompería una respuesta de larga duración.
- Con el patrón jobId, el cliente recibe respuesta inmediata y puede hacer polling liviano (`GET /sync/{jobId}`) a la frecuencia que quiera.

### 4.4 Instrucciones para probar el módulo

#### Requisitos previos

| Requisito | Verificación |
|---|---|
| Java 17+ | `java -version` |
| Docker Desktop corriendo | `docker info` |
| XAMPP con MySQL en puerto **3307** | Panel de control XAMPP → MySQL Started |
| Base de datos `hydrogest` con las tablas del monolito | Importar el SQL del monolito si aplica |

> **Importante:** MySQL debe estar en el puerto `3307` (no el 3306 por defecto). Ajustar en `application.properties` si el puerto es diferente.

#### Paso 1 — Clonar y configurar

```bash
git clone https://github.com/HydroGest-Team/hidrogest-sync-service.git
cd hidrogest-sync-service
```

Crear o editar `src/main/resources/application.properties` con las credenciales locales:

```properties
spring.datasource.url=jdbc:mysql://localhost:3307/hydrogest?useSSL=false&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=
```

> Las credenciales reales **no se suben a GitHub**. El archivo `application.properties` en el repositorio tiene los campos vacíos o con valores de ejemplo.

#### Paso 2 — Levantar MongoDB

```bash
docker compose up -d
```

Verifica que el contenedor está corriendo:

```bash
docker ps
# Debe aparecer: hydrogest-mongo
```

#### Paso 3 — Iniciar el microservicio

```bash
./mvnw spring-boot:run
```

La app está lista cuando aparece en consola:

```
Started HidrogestSyncServiceApplication on port 8081
```

#### Paso 4 — Probar los endpoints

**Opción A — Swagger UI (recomendado para el docente):**

Abrir en el navegador: `http://localhost:8081/swagger-ui/index.html`

Desde ahí se pueden ejecutar todos los endpoints con un formulario visual.

**Opción B — Dashboard:**

Abrir en el navegador: `http://localhost:8081/dashboard.html`

El dashboard muestra el estado de las colecciones y tiene un botón para iniciar el sync con barra de progreso.

**Opción C — curl / Postman:**

```bash
# 1. Iniciar sincronización
curl -X POST http://localhost:8081/sync
# Respuesta: {"jobId":"a1b2c3d4-..."}

# 2. Consultar progreso (reemplazar {jobId} con el valor recibido)
curl http://localhost:8081/sync/{jobId}
# Respuesta: {"jobId":"a1b2c3d4-...","progress":67}
# Repetir hasta que progress llegue a 100.

# 3. Consultar estado global
curl http://localhost:8081/sync/status
# Respuesta: {"syncEnCurso":false,"totalDocumentos":{...},"ultimoSync":"2026-..."}

# 4. Intentar dos syncs simultáneos (debe responder 409)
curl -X POST http://localhost:8081/sync &
curl -X POST http://localhost:8081/sync
# El segundo debe responder: {"error":"Ya hay una sincronización en curso"}
```

#### Resultado esperado

| Escenario | Resultado |
|---|---|
| `POST /sync` sin sync activo | `202 Accepted` con `jobId` |
| `POST /sync` con sync en curso | `409 Conflict` con mensaje de error |
| `GET /sync/{jobId}` válido | `200 OK` con `progress` entre 0 y 100 |
| `GET /sync/{jobId}` completado | `200 OK` con `progress: 100` |
| `GET /sync/status` | `200 OK` con conteos por colección y timestamp del último sync |
| MongoDB después del sync | Las 6 colecciones tienen los mismos registros que MySQL |

---

## Stack Tecnológico

| Componente | Tecnología |
|---|---|
| Framework | Spring Boot 4.1.1 |
| Lenguaje | Java 17 (Amazon Corretto 17) |
| Base de datos origen | MySQL / MariaDB — `hydrogest` |
| Base de datos destino | MongoDB 7 — `hidrogest_replica` |
| Acceso MySQL | Spring JdbcTemplate (solo lectura) |
| Acceso MongoDB | MongoDB Java Driver (native) |
| Documentación API | Springdoc OpenAPI 3 — Swagger UI |
| Build | Maven (wrapper incluido `mvnw`) |
| Contenedores | Docker / Docker Compose |

## Endpoints de la API

| Método | Ruta | Código éxito | Descripción |
|---|---|---|---|
| `POST` | `/sync` | `202 Accepted` | Inicia sync asíncrono. Devuelve `jobId`. Retorna `409` si hay uno activo. |
| `GET` | `/sync/{jobId}` | `200 OK` | Progreso del job (0–100). Retorna `404` si el jobId no existe. |
| `GET` | `/sync/status` | `200 OK` | Estado global: sync activo, conteos por colección, timestamp último sync. |

Documentación interactiva: `http://localhost:8081/swagger-ui/index.html`

## Estructura del Proyecto

```
hidrogest-sync-service/
├── docker-compose.yml                         # MongoDB local (puerto 27017)
├── docs/
│   └── diagrama-arquitectura.svg              # Diagrama de arquitectura integrada
├── src/main/
│   ├── java/com/paqueteria/paqueteria_tracker/
│   │   ├── controller/
│   │   │   └── SyncController.java            # POST /sync · GET /sync/{jobId} · GET /sync/status
│   │   ├── service/
│   │   │   └── SyncService.java               # Lógica de replicación, delta sync, progreso
│   │   └── HidrogestSyncServiceApplication.java
│   └── resources/
│       ├── application.properties             # Configuración (sin credenciales reales)
│       └── static/
│           └── dashboard.html                 # Dashboard de monitoreo
└── pom.xml
```

## Consideraciones de Seguridad

- **Las credenciales reales no se suben a GitHub.** El archivo `application.properties` en el repositorio tiene los campos de usuario y contraseña vacíos o con valores de ejemplo. Cada desarrollador configura sus credenciales localmente.
- En producción se recomienda crear un usuario MySQL de solo lectura:
  ```sql
  CREATE USER 'sync_reader'@'localhost' IDENTIFIED BY 'password_seguro';
  GRANT SELECT ON hydrogest.* TO 'sync_reader'@'localhost';
  FLUSH PRIVILEGES;
  ```
- El microservicio **nunca escribe ni modifica** datos en el monolito HidroGest.
- No exponer el puerto 8081 a Internet sin un proxy reverso con autenticación.
- Nunca ejecutar `docker compose down -v` en producción: ese comando elimina los datos replicados en MongoDB.
