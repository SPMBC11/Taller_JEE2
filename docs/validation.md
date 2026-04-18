# Validacion funcional

## Precondiciones

1. Levantar stack completo:

```bash
docker compose up -d --build
```

2. Confirmar contenedores en estado `Up`:

- `wildfly-logica`
- `wildfly-datos`
- `postgres-examenes`
- `postgres-notas`
- `queue`
- `email-service`

## Caso A - Flujo completo (2 BD + queue + email)

### Request al servicio de logica

```bash
curl -X POST http://localhost:8080/api/logic/exams/finish \
  -H "Content-Type: application/json" \
  -d '{
    "studentId": 1,
    "answers": [
      {"questionId": 1, "selectedOption": "API de transacciones distribuidas", "correct": true},
      {"questionId": 2, "selectedOption": "Queue", "correct": true},
      {"questionId": 3, "selectedOption": "Compensacion", "correct": false}
    ]
  }'
```

Resultado esperado: `HTTP 200` con `examId`, `score`, `status`, `evaluatedAt`.

### Verificacion en BD de examenes

```bash
docker exec postgres-examenes psql -U exam_user -d exam_db -c "SELECT COUNT(*) AS exam_attempts FROM exam_attempt;"
docker exec postgres-examenes psql -U exam_user -d exam_db -c "SELECT COUNT(*) AS answers FROM answer;"
docker exec postgres-examenes psql -U exam_user -d exam_db -c "SELECT COUNT(*) AS evaluations FROM evaluation;"
docker exec postgres-examenes psql -U exam_user -d exam_db -c "SELECT id, status, attempt_count FROM outbox_event ORDER BY id DESC LIMIT 5;"
```

### Verificacion en BD de notas

```bash
docker exec postgres-notas psql -U student_user -d student_db -c "SELECT COUNT(*) AS grades FROM grade;"
docker exec postgres-notas psql -U student_user -d student_db -c "SELECT COUNT(*) AS history_rows FROM result_history;"
```

### Verificacion asincrona (queue + email)

```bash
docker logs email-service --tail 80
docker exec queue rabbitmqctl list_queues name messages_ready messages_unacknowledged
```

Resultado esperado:

- En logs aparece `[MOCK EMAIL]` con `to=ana.perez@example.com`.
- Cola `exam.notifications` sin pendientes (`messages_ready=0`).
- Evento de `outbox_event` termina en estado `SENT`.

## Caso B - Rollback por fallo de una BD

1. Detener `postgres-notas`.
2. Repetir request de finalizacion.
3. Esperar error HTTP.
4. Verificar en `postgres-examenes` que no haya nuevo intento confirmado.

Resultado esperado: rollback total de la transaccion distribuida.

## Caso D - Confiabilidad del outbox si RabbitMQ cae

1. Detener `queue`.
2. Enviar un examen finalizado (debe persistir en ambas BD).
3. Verificar en `outbox_event` estado `PENDING` o `FAILED`.
4. Levantar `queue`.
5. Verificar que el evento pasa a `SENT` y aparece `[MOCK EMAIL]`.

Resultado esperado: no se pierde el mensaje aunque RabbitMQ estuviera caido al momento del commit.

## Caso C - Fallo de email

1. Configurar `MAIL_MOCK_MODE=false` y SMTP invalido en `email-service`.
2. Enviar un examen finalizado.
3. Revisar logs de `email-service` para intentos de retry.
4. Verificar que los datos en ambas BD se mantienen.

Resultado esperado: fallo de email no revierte datos de negocio.
