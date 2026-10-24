# HidroGest Sync Service

Microservicio de replicación asíncrona de datos desde el monolito HidroGest (MySQL) hacia MongoDB, sin modificar el sistema original.

## Descripción General

Este servicio expone una API REST que permite iniciar una sincronización en segundo plano y consultar su progreso. Solo replica diferencias — registros nuevos o modificados desde la última sincronización exitosa — y nunca elimina datos ya replicados en MongoDB.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/sync` | Inicia una sincronización asíncrona. Devuelve un `jobId`. |
| `GET` | `/sync/{jobId}` | Consulta el progreso del job (0–100). |

Documentación interactiva disponible en `http://localhost:8081/swagger-ui/index.html`.

## Flujo de Sincronización

1. El cliente llama a `POST /sync` y recibe un `jobId`.
2. El servicio lanza un hilo en segundo plano que:
   - Lee de MongoDB cuándo fue el último sync exitoso (`sync_metadata`).
   - Consulta cada tabla de MySQL solo los registros con `updated_at` mayor a ese timestamp.
   - Inserta o actualiza (upsert) cada registro en MongoDB.
   - Guarda el nuevo timestamp al finalizar.
3. El cliente consulta `GET /sync/{jobId}` hasta ver `progress: 100`.

Si se llama a `POST /sync` mientras ya hay uno en curso, el servicio responde `409 Conflict`.

## Tablas Replicadas

- `tb_clientes`
- `tb_contadores`
- `tb_tarifas`
- `tb_usuarios`
- `tb_lecturas`
- `tb_pagos`

## Stack Tecnológico

| Componente | Tecnología |
|---|---|
| Framework | Spring Boot 4.1.1 |
| Lenguaje | Java 17 |
| Base de datos origen | MySQL / MariaDB (HidroGest) |
| Base de datos destino | MongoDB 7 |
| Documentación API | Springdoc OpenAPI 3 (Swagger UI) |
| Build | Maven |

## Requisitos Previos

- Java 17 (Amazon Corretto 17 recomendado)
- Maven (o usar el wrapper incluido `mvnw`)
- Docker (para levantar MongoDB local)
- XAMPP con MySQL/MariaDB corriendo en puerto `3307` con la base de datos `hydrogest`

## Instalación y Ejecución

### 1. Clonar el repositorio

```bash
git clone https://github.com/HydroGest-Team/hidrogest-sync-service.git
cd hidrogest-sync-service
```

### 2. Levantar MongoDB con Docker

```bash
docker compose up -d
```

Esto levanta un contenedor MongoDB en `localhost:27017` con la base de datos `hidrogest_replica`.

> **Importante:** nunca ejecutar `docker compose down -v` — ese comando elimina los datos replicados.

### 3. Configurar credenciales

Las credenciales de MySQL van en `src/main/resources/application.properties`. Este archivo **no se sube a GitHub** con credenciales reales — configurarlo localmente:

```properties
spring.datasource.url=jdbc:mysql://localhost:3307/hydrogest?useSSL=false&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=
```

### 4. Ejecutar la aplicación

```bash
./mvnw spring-boot:run
```

O desde IntelliJ: botón Run (▶).

La app inicia en `http://localhost:8081`.

## Uso

### Iniciar una sincronización

```http
POST http://localhost:8081/sync
```

Respuesta:
```json
{
    "jobId": "a1b2c3d4-..."
}
```

### Consultar progreso

```http
GET http://localhost:8081/sync/{jobId}
```

Respuesta:
```json
{
    "jobId": "a1b2c3d4-...",
    "progress": 67
}
```

Cuando `progress` llega a `100`, la sincronización finalizó correctamente.

### Swagger UI

```
http://localhost:8081/swagger-ui/index.html
```

## Consideraciones de Seguridad

- Las credenciales reales no se suben a GitHub.
- En producción se recomienda crear un usuario MySQL de solo lectura con `GRANT SELECT ON hydrogest.* TO 'sync_reader'@'localhost'`.
- El microservicio nunca escribe ni modifica datos en el monolito HidroGest.

## Estructura del Proyecto

```
src/main/java/com/paqueteria/paqueteria_tracker/
├── controller/
│   └── SyncController.java   # Endpoints POST /sync y GET /sync/{jobId}
├── service/
│   └── SyncService.java      # Lógica de replicación, detección de diferencias
└── hidrogest_sync_service.java  # Clase principal Spring Boot
```
