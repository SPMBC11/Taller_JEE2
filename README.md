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
  -> wildfly-logica (REST / negocio)
      -> wildfly-datos (JPA + JTA/XA)
          -> postgres-examenes
          -> postgres-notas
      -> queue (RabbitMQ)
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
2. `wildfly-logica` calcula nota y estado.
3. `wildfly-logica` llama a `wildfly-datos` (`/api/data/evaluations`).
4. `wildfly-datos` persiste en las 2 BD dentro de una transaccion distribuida.
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
    { "questionId": 1, "selectedOption": "API de transacciones distribuidas", "correct": true },
    { "questionId": 2, "selectedOption": "Queue", "correct": true },
    { "questionId": 3, "selectedOption": "Compensacion", "correct": false }
  ]
}
```

## 5) Ejecucion

```bash
docker compose up -d --build
```

Si vienes de una version previa con esquema distinto, recrea volumenes de BD:

```bash
docker compose down -v
docker compose up -d --build
```

Servicios esperados:

- `wildfly-logica` (8080)
- `wildfly-datos` (8081)
- `postgres-examenes` (5433)
- `postgres-notas` (5434)
- `queue` (5672, 15672)
- `email-service`

## 6) Validacion

La validacion funcional detallada (caso feliz y verificaciones en BD/cola/email) esta en `docs/validation.md`.

## 7) Backend final y modulo legacy

- Backend final para el taller: `wildfly-logica` + `wildfly-datos` + `email-service`.
- El modulo `backend` (Spring + Atomikos) se conserva solo como referencia historica y no forma parte de la arquitectura final evaluable.
