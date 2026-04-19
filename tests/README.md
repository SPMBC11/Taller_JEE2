# Pruebas automaticas del taller

## E2E de arquitectura distribuida

Script principal:

- `tests/workshop-e2e.ps1`

Valida automaticamente:

1. Flujo feliz extremo a extremo.
2. Persistencia en ambas bases de datos.
3. Publicacion/consumo por RabbitMQ.
4. Envio de correo (modo mock).
5. Rollback XA cuando falla una base.
6. Confiabilidad del outbox cuando RabbitMQ esta caido.

## Ejecucion

Desde la raiz del proyecto:

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\workshop-e2e.ps1
```

Si el stack ya esta levantado y no deseas reconstruir:

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\workshop-e2e.ps1 -SkipBuild
```

Si quieres forzar recreacion completa de bases y contenedores:

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\workshop-e2e.ps1 -ResetData
```

Si quieres ver evidencia detallada en consola (estado de BD1, BD2, outbox, cola y logs de email en cada caso):

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\workshop-e2e.ps1 -SkipBuild -VerboseReport
```

Si quieres exportar evidencia a archivos (una carpeta por ejecucion, con snapshots por caso):

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\workshop-e2e.ps1 -SkipBuild -ExportEvidence
```

Si quieres consola detallada y archivos al mismo tiempo:

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\workshop-e2e.ps1 -SkipBuild -VerboseReport -ExportEvidence
```

Por defecto la evidencia se guarda en `tests/evidence/<timestamp>`. Si quieres otra ruta:

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\workshop-e2e.ps1 -SkipBuild -ExportEvidence -EvidenceDir .\tests\evidence-profesor
```
