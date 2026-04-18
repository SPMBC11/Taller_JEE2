# Arquitectura obligatoria aplicada

```text
Usuario
  -> wildfly-logica (API REST + orquestacion)
    -> wildfly-datos (validacion + persistencia + JTA/XA)
          -> postgres-examenes
          -> postgres-notas
      -> outbox_event (en postgres-examenes)
    -> queue (RabbitMQ, via outbox publisher)
          -> email-service
```

## Componentes

- `wildfly-logica`:
  - endpoint publico `POST /api/logic/exams/finish`
  - valida estructura del request y orquesta el proceso
  - invoca `wildfly-datos`
  - no publica eventos directamente en RabbitMQ

- `wildfly-datos`:
  - endpoint interno `POST /api/data/evaluations`
  - valida respuestas contra `question.correct_option`
  - calcula `correctAnswers`, `score` y `status` (`PASSED`/`FAILED`)
  - persiste en dos bases de datos distintas
  - usa JTA + XA para 2PC atomico (`ExamDS` y `StudentDS`)
  - registra eventos en `outbox_event` para entrega confiable a RabbitMQ
  - publica eventos pendientes desde outbox hacia RabbitMQ

- `postgres-examenes`:
  - `question`, `exam_attempt`, `answer`, `evaluation`

- `postgres-notas`:
  - `student`, `grade`, `result_history`

- `queue`:
  - exchange `exam.exchange`
  - queue `exam.notifications`
  - routing key `exam.finished`

- `email-service`:
  - consumidor desacoplado (`@RabbitListener`)
  - construye y envia correo
  - en local puede operar en modo mock (`MAIL_MOCK_MODE=true`)

## Garantias

- Consistencia fuerte entre ambas bases de datos mediante 2PC.
- Commit atomico o rollback total en persistencia de negocio.
- Notificacion por correo desacoplada del commit transaccional.
- Error en email no revierte datos ya confirmados.
