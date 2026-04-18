# Solucion empresarial - Taller 2 JEE

Implementacion alineada con la arquitectura obligatoria del taller:

- `wildfly-logica`: capa de presentacion y logica.
- `wildfly-datos`: capa de persistencia y transaccion distribuida.
- `postgres-examenes` y `postgres-notas`: dos BD separadas.
- `queue` (RabbitMQ): comunicacion asincrona.
- `email-service`: consumidor desacoplado para notificaciones por correo.

## 1) Arquitectura por capas (obligatoria)

```text
Usuario
  -> wildfly-logica (REST / orquestacion)
    -> wildfly-datos (validacion + JPA + JTA/XA)
      -> postgres-examenes
      -> postgres-notas
      -> outbox_event
    -> queue (RabbitMQ, via outbox publisher en wildfly-datos)
      -> email-service
```

## 2) Transaccion distribuida (JTA + 2PC)

- `wildfly-datos` usa dos `PersistenceContext` JTA:
  - `examPU` -> `java:/jdbc/ExamDS`
  - `studentPU` -> `java:/jdbc/StudentDS`
- Ambos datasources son XA de PostgreSQL.
- El commit se coordina en 2 fases (2PC):
  1. `prepare` en ambos recursos.
  2. `commit` o `rollback` atomico en ambos recursos.

Nota: para PostgreSQL XA se habilita `max_prepared_transactions=100` en ambos contenedores.

## 3) Flujo funcional

1. Cliente invoca `POST /api/logic/exams/finish` en `wildfly-logica`.
2. `wildfly-logica` valida estructura del request y orquesta el proceso.
3. `wildfly-logica` llama a `wildfly-datos` (`/api/data/evaluations`).
4. `wildfly-datos` valida respuestas contra `question.correct_option`, calcula `correctAnswers`, `score` y `status` (`PASSED`/`FAILED`) y persiste en las 2 BD dentro de una transaccion distribuida.
5. En la misma transaccion, `wildfly-datos` registra un evento en la tabla `outbox_event` (patron outbox).
6. Un publisher programado en `wildfly-datos` publica eventos pendientes a `exam.exchange`.
7. `email-service` consume `exam.notifications` y envia correo (mock/local).

## 4) Endpoints

- Servicio logica (publico):
  - `POST http://localhost:8080/api/logic/exams/finish`
- Servicio datos (interno):
  - `POST http://localhost:8081/api/data/evaluations`

Ejemplo de request a logica:

```json
{
  "studentId": 1,
  "answers": [
    { "questionId": 1, "selectedOption": "API de transacciones distribuidas" },
    { "questionId": 2, "selectedOption": "Queue" },
    { "questionId": 3, "selectedOption": "Compensacion" }
  ]
}
```

## 5) Guia de ejecucion para profesor

Esta seccion esta pensada para compilar y ejecutar el proyecto en evaluacion.

### 5.1) Prerrequisitos

1. Docker Desktop (con Docker Compose v2 habilitado).
2. Git.
3. PowerShell (para correr el script E2E en Windows).
4. Opcional para compilacion local: Java 21 + Maven 3.9.x.

### 5.2) Opcion recomendada (Docker, build completo)

Desde la raiz del proyecto:

```bash
docker compose down -v
docker compose up -d --build
docker compose ps --all
```

Servicios esperados:

- `wildfly-logica` (8080)
- `wildfly-datos` (8081)
- `postgres-examenes` (5433)
- `postgres-notas` (5434)
- `queue` (5672, 15672)
- `email-service`

### 5.3) Prueba automatica recomendada (E2E)

Primera ejecucion o si se quiere limpiar datos:

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\workshop-e2e.ps1 -ResetData
```

Re-ejecucion rapida sin rebuild:

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\workshop-e2e.ps1 -SkipBuild
```

Resultado esperado al final:

```text
RESULTADO: OK - flujo distribuido y desacoplado validado
```

### 5.4) Prueba manual minima

Request al endpoint publico de logica:

```bash
curl -X POST http://localhost:8080/api/logic/exams/finish \
  -H "Content-Type: application/json" \
  -d '{
    "studentId": 1,
    "answers": [
      {"questionId": 1, "selectedOption": "API de transacciones distribuidas"},
      {"questionId": 2, "selectedOption": "Queue"},
      {"questionId": 3, "selectedOption": "Compensacion"}
    ]
  }'
```

Checks rapidos:

```bash
docker exec postgres-examenes psql -U exam_user -d exam_db -c "SELECT id, status FROM outbox_event ORDER BY id DESC LIMIT 5;"
docker exec postgres-notas psql -U student_user -d student_db -c "SELECT message_id, exam_id FROM processed_messages ORDER BY processed_at DESC LIMIT 5;"
docker exec queue rabbitmqctl list_queues name messages_ready messages_unacknowledged
docker logs email-service --tail 80
```

### 5.5) Compilacion local por modulo (sin Docker)

Si el profesor desea compilar directamente con Maven:

```bash
mvn -f wildfly-logica/pom.xml clean package -DskipTests
mvn -f wildfly-datos/pom.xml clean package -DskipTests
mvn -f email-service/pom.xml clean test
```

Nota: el modulo `backend` es legacy y no forma parte del backend final evaluable.

### 5.6) Apagar entorno

```bash
docker compose down
```

## 6) Validacion

La validacion funcional detallada (caso feliz, rollback XA, outbox y correo) esta en `docs/validation.md`.

## 7) Backend final y modulo legacy

- Backend final para el taller: `wildfly-logica` + `wildfly-datos` + `email-service`.
- El modulo `backend` (Spring + Atomikos) se conserva solo como referencia historica y no forma parte de la arquitectura final evaluable.
