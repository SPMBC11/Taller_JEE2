param(
    [switch]$SkipBuild,
    [switch]$ResetData,
    [switch]$VerboseReport,
    [switch]$ExportEvidence,
    [string]$EvidenceDir = 'tests/evidence'
)

$ErrorActionPreference = 'Stop'
$script:EvidenceRunDir = $null
$script:OverallStatus = 'FAILED'

function Convert-ToSafeName {
    param(
        [string]$Value,
        [string]$Fallback = 'snapshot'
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $Fallback
    }

    $safe = $Value.ToLowerInvariant() -replace '[^a-z0-9]+', '-'
    $safe = $safe.Trim('-')

    if ([string]::IsNullOrWhiteSpace($safe)) {
        return $Fallback
    }

    return $safe
}

function Initialize-EvidenceExport {
    if (-not $ExportEvidence) {
        return
    }

    $basePath = $EvidenceDir
    if (-not [System.IO.Path]::IsPathRooted($basePath)) {
        $basePath = Join-Path -Path (Get-Location) -ChildPath $basePath
    }

    New-Item -Path $basePath -ItemType Directory -Force | Out-Null
    $runFolder = Get-Date -Format 'yyyyMMdd-HHmmss'
    $script:EvidenceRunDir = Join-Path -Path $basePath -ChildPath $runFolder
    New-Item -Path $script:EvidenceRunDir -ItemType Directory -Force | Out-Null

    $runInfo = @(
        "generated_at=$(Get-Date -Format o)"
        "skip_build=$SkipBuild"
        "reset_data=$ResetData"
        "verbose_report=$VerboseReport"
        "evidence_dir=$script:EvidenceRunDir"
    )

    Set-Content -Path (Join-Path -Path $script:EvidenceRunDir -ChildPath 'run-info.txt') -Value $runInfo -Encoding UTF8
    Write-Output "Exportando evidencia en: $script:EvidenceRunDir"
}

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )
    if (-not $Condition) {
        throw "ASSERTION FAILED: $Message"
    }
}

function Get-ContainerStatus {
    param([string]$ContainerName)

    $line = docker compose ps --all --format "{{.Name}}|{{.State}}|{{.Health}}" |
        Where-Object { $_.StartsWith("$ContainerName|") } |
        Select-Object -First 1

    if ([string]::IsNullOrWhiteSpace($line)) {
        return @{ name = $ContainerName; state = 'missing'; health = '' }
    }

    $parts = $line.Split('|')
    return @{ name = $parts[0]; state = $parts[1]; health = $parts[2] }
}

function Wait-ContainerReady {
    param(
        [string]$ContainerName,
        [bool]$RequireHealthy = $false,
        [int]$MaxAttempts = 80
    )

    for ($i = 0; $i -lt $MaxAttempts; $i++) {
        $status = Get-ContainerStatus -ContainerName $ContainerName
        if ($status.state -eq 'running') {
            if (-not $RequireHealthy -or $status.health -eq 'healthy') {
                return
            }
        }
    }

    $status = Get-ContainerStatus -ContainerName $ContainerName
    throw "Container $ContainerName no esta listo. state=$($status.state), health=$($status.health)"
}

function Get-SqlScalar {
    param(
        [string]$Container,
        [string]$User,
        [string]$Database,
        [string]$Query
    )

    $result = docker exec $Container psql -U $User -d $Database -t -A -c $Query
    return (($result | Select-Object -Last 1).Trim())
}

function Invoke-FinishExam {
    param([int]$TimeoutSec = 60)

    $bodyObject = [ordered]@{
        studentId = 1
        answers = @(
            @{ questionId = 1; selectedOption = 'API de transacciones distribuidas' },
            @{ questionId = 2; selectedOption = 'Queue' },
            @{ questionId = 3; selectedOption = 'Compensacion' }
        )
    }

    $body = $bodyObject | ConvertTo-Json -Depth 5
    return Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/logic/exams/finish' -ContentType 'application/json' -Body $body -TimeoutSec $TimeoutSec
}

function Wait-OutboxStatus {
    param(
        [long]$ExamId,
        [string]$ExpectedStatus,
        [int]$MaxAttempts = 120
    )

    for ($i = 0; $i -lt $MaxAttempts; $i++) {
        $status = Get-SqlScalar -Container 'postgres-examenes' -User 'exam_user' -Database 'exam_db' -Query "SELECT status FROM outbox_event WHERE aggregate_id = $ExamId ORDER BY id DESC LIMIT 1;"
        if ($status -eq $ExpectedStatus) {
            return
        }
    }

    $current = Get-SqlScalar -Container 'postgres-examenes' -User 'exam_user' -Database 'exam_db' -Query "SELECT status FROM outbox_event WHERE aggregate_id = $ExamId ORDER BY id DESC LIMIT 1;"
    throw "Outbox no alcanzo estado $ExpectedStatus para examId=$ExamId. Estado actual=$current"
}

function Assert-QueueEmpty {
    $queueLine = docker exec queue rabbitmqctl list_queues name messages_ready messages_unacknowledged |
        Where-Object { $_ -match '^exam.notifications\s+' } |
        Select-Object -First 1

    Assert-True -Condition ($null -ne $queueLine) -Message 'No se encontro la cola exam.notifications'
    $parts = ($queueLine -split '\s+') | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    Assert-True -Condition ($parts.Count -ge 3) -Message "Formato inesperado de estado de cola: $queueLine"
    Assert-True -Condition ($parts[1] -eq '0') -Message "messages_ready no es 0 en exam.notifications"
    Assert-True -Condition ($parts[2] -eq '0') -Message "messages_unacknowledged no es 0 en exam.notifications"
}

function Assert-EmailLogContainsExam {
    param([long]$ExamId)

    $logs = docker logs email-service --tail 400
    $logText = ($logs -join [Environment]::NewLine)
    Assert-True -Condition ([bool]($logText -match "Resultado de evaluacion #$ExamId")) -Message "No aparece correo para examId=$ExamId en logs de email-service"
}

function Show-DbSnapshot {
    param(
        [string]$Title,
        [long]$ExamId = 0,
        [string]$SnapshotTag = ''
    )

    if (-not $VerboseReport -and -not $ExportEvidence) {
        return
    }

    $resolvedTag = Convert-ToSafeName -Value $SnapshotTag -Fallback (Convert-ToSafeName -Value $Title)
    $snapshotLines = @(
        "=== SNAPSHOT: $Title ==="
        "captured_at=$(Get-Date -Format o)"
        ''
    )

    $sections = @(
        @{ Label = 'BD1 exam_attempt (ultimos 5)'; Command = { docker exec postgres-examenes psql -U exam_user -d exam_db -c "SELECT id, student_id, score, correct_answers, total_questions FROM exam_attempt ORDER BY id DESC LIMIT 5;" } },
        @{ Label = 'BD1 evaluation (ultimos 5)'; Command = { docker exec postgres-examenes psql -U exam_user -d exam_db -c "SELECT id, exam_attempt_id, status, score, evaluated_at FROM evaluation ORDER BY id DESC LIMIT 5;" } },
        @{ Label = 'BD1 outbox_event (ultimos 5)'; Command = { docker exec postgres-examenes psql -U exam_user -d exam_db -c "SELECT id, aggregate_id, status, attempt_count, next_attempt_at, sent_at FROM outbox_event ORDER BY id DESC LIMIT 5;" } },
        @{ Label = 'BD2 grade (ultimos 5)'; Command = { docker exec postgres-notas psql -U student_user -d student_db -c "SELECT id, exam_attempt_id, student_id, status, score, created_at FROM grade ORDER BY id DESC LIMIT 5;" } },
        @{ Label = 'BD2 result_history (ultimos 5)'; Command = { docker exec postgres-notas psql -U student_user -d student_db -c "SELECT id, exam_attempt_id, student_id, status, score, evaluated_at FROM result_history ORDER BY id DESC LIMIT 5;" } },
        @{ Label = 'BD2 processed_messages (ultimos 5)'; Command = { docker exec postgres-notas psql -U student_user -d student_db -c "SELECT message_id, exam_id, processed_at FROM processed_messages ORDER BY processed_at DESC LIMIT 5;" } },
        @{ Label = 'Queue exam.notifications'; Command = { docker exec queue rabbitmqctl list_queues name messages_ready messages_unacknowledged } },
        @{ Label = 'Logs email-service (ultimos 40)'; Command = { docker logs email-service --tail 40 } }
    )

    if ($ExamId -gt 0) {
        $sections = @(
            @{ Label = 'BD1 exam_attempt (ultimos 5)'; Command = { docker exec postgres-examenes psql -U exam_user -d exam_db -c "SELECT id, student_id, score, correct_answers, total_questions FROM exam_attempt ORDER BY id DESC LIMIT 5;" } },
            @{ Label = 'BD1 evaluation (ultimos 5)'; Command = { docker exec postgres-examenes psql -U exam_user -d exam_db -c "SELECT id, exam_attempt_id, status, score, evaluated_at FROM evaluation ORDER BY id DESC LIMIT 5;" } },
            @{ Label = 'BD1 outbox_event (ultimos 5)'; Command = { docker exec postgres-examenes psql -U exam_user -d exam_db -c "SELECT id, aggregate_id, status, attempt_count, next_attempt_at, sent_at FROM outbox_event ORDER BY id DESC LIMIT 5;" } },
            @{ Label = "BD1 answer para exam_attempt_id=$ExamId"; Command = { docker exec postgres-examenes psql -U exam_user -d exam_db -c "SELECT question_id, selected_option, is_correct, points FROM answer WHERE exam_attempt_id = $ExamId ORDER BY id;" } },
            @{ Label = 'BD2 grade (ultimos 5)'; Command = { docker exec postgres-notas psql -U student_user -d student_db -c "SELECT id, exam_attempt_id, student_id, status, score, created_at FROM grade ORDER BY id DESC LIMIT 5;" } },
            @{ Label = 'BD2 result_history (ultimos 5)'; Command = { docker exec postgres-notas psql -U student_user -d student_db -c "SELECT id, exam_attempt_id, student_id, status, score, evaluated_at FROM result_history ORDER BY id DESC LIMIT 5;" } },
            @{ Label = 'BD2 processed_messages (ultimos 5)'; Command = { docker exec postgres-notas psql -U student_user -d student_db -c "SELECT message_id, exam_id, processed_at FROM processed_messages ORDER BY processed_at DESC LIMIT 5;" } },
            @{ Label = 'Queue exam.notifications'; Command = { docker exec queue rabbitmqctl list_queues name messages_ready messages_unacknowledged } },
            @{ Label = 'Logs email-service (ultimos 40)'; Command = { docker logs email-service --tail 40 } }
        )
    }

    if ($VerboseReport) {
        Write-Output ''
        Write-Output "=== SNAPSHOT: $Title ==="
    }

    foreach ($section in $sections) {
        $header = "-- $($section.Label) --"
        $captured = @((& $section.Command 2>&1) | ForEach-Object { $_.ToString() })

        if ($VerboseReport) {
            Write-Output $header
            if ($captured.Count -eq 0) {
                Write-Output '(sin salida)'
            } else {
                $captured | ForEach-Object { Write-Output $_ }
            }
        }

        $snapshotLines += $header
        if ($captured.Count -eq 0) {
            $snapshotLines += '(sin salida)'
        } else {
            $snapshotLines += $captured
        }
        $snapshotLines += ''
    }

    if ($ExportEvidence -and $script:EvidenceRunDir) {
        $snapshotPath = Join-Path -Path $script:EvidenceRunDir -ChildPath "$resolvedTag.txt"
        Set-Content -Path $snapshotPath -Value $snapshotLines -Encoding UTF8
        Write-Output "Evidencia guardada: $snapshotPath"
    }
}

Write-Output '=== Workshop E2E Validation ==='

if ($ResetData) {
    Write-Output 'Reiniciando entorno con borrado de volumenes...'
    docker compose down -v | Out-Null
    $SkipBuild = $false
}

if (-not $SkipBuild) {
    Write-Output 'Levantando stack...'
    docker compose up -d --build | Out-Null
}

Initialize-EvidenceExport

Wait-ContainerReady -ContainerName 'postgres-examenes' -RequireHealthy $true
Wait-ContainerReady -ContainerName 'postgres-notas' -RequireHealthy $true
Wait-ContainerReady -ContainerName 'queue' -RequireHealthy $true
Wait-ContainerReady -ContainerName 'wildfly-datos' -RequireHealthy $true
Wait-ContainerReady -ContainerName 'wildfly-logica' -RequireHealthy $true
Wait-ContainerReady -ContainerName 'email-service'

$queueStopped = $false
$notesStopped = $false
$happyExamId = $null
$outboxExamId = $null

try {
    Write-Output 'Caso 1: flujo feliz + persistencia + cola + correo'
    Show-DbSnapshot -Title 'Antes de Caso 1' -SnapshotTag 'case1-before'

    $beforeExam = [int](Get-SqlScalar -Container 'postgres-examenes' -User 'exam_user' -Database 'exam_db' -Query 'SELECT COUNT(*) FROM exam_attempt;')
    $beforeGrade = [int](Get-SqlScalar -Container 'postgres-notas' -User 'student_user' -Database 'student_db' -Query 'SELECT COUNT(*) FROM grade;')

    $happyResponse = Invoke-FinishExam
    $happyExamId = [long]$happyResponse.examId
    Write-Output "Caso 1 examId=$happyExamId status=$($happyResponse.status) score=$($happyResponse.score)"

    $afterExam = [int](Get-SqlScalar -Container 'postgres-examenes' -User 'exam_user' -Database 'exam_db' -Query 'SELECT COUNT(*) FROM exam_attempt;')
    $afterGrade = [int](Get-SqlScalar -Container 'postgres-notas' -User 'student_user' -Database 'student_db' -Query 'SELECT COUNT(*) FROM grade;')

    Assert-True -Condition ($afterExam -eq ($beforeExam + 1)) -Message 'No aumento exam_attempt en flujo feliz'
    Assert-True -Condition ($afterGrade -eq ($beforeGrade + 1)) -Message 'No aumento grade en flujo feliz'

    Wait-OutboxStatus -ExamId $happyExamId -ExpectedStatus 'SENT'
    Assert-QueueEmpty
    Assert-EmailLogContainsExam -ExamId $happyExamId
    Show-DbSnapshot -Title 'Despues de Caso 1' -ExamId $happyExamId -SnapshotTag 'case1-after'

    Write-Output 'Caso 2: rollback XA cuando falla BD academica'
    Show-DbSnapshot -Title 'Antes de Caso 2' -SnapshotTag 'case2-before'

    $beforeRollback = [int](Get-SqlScalar -Container 'postgres-examenes' -User 'exam_user' -Database 'exam_db' -Query 'SELECT COUNT(*) FROM exam_attempt;')

    docker stop postgres-notas | Out-Null
    $notesStopped = $true

    $failed = $false
    try {
        $null = Invoke-FinishExam -TimeoutSec 20
    } catch {
        $failed = $true
    }

    Assert-True -Condition $failed -Message 'La solicitud debio fallar con postgres-notas detenido'

    docker start postgres-notas | Out-Null
    $notesStopped = $false
    Wait-ContainerReady -ContainerName 'postgres-notas' -RequireHealthy $true

    $afterRollback = [int](Get-SqlScalar -Container 'postgres-examenes' -User 'exam_user' -Database 'exam_db' -Query 'SELECT COUNT(*) FROM exam_attempt;')
    Assert-True -Condition ($afterRollback -eq $beforeRollback) -Message 'Se insertaron datos en BD1 pese a falla de BD2'
    Show-DbSnapshot -Title 'Despues de Caso 2 (rollback esperado)' -SnapshotTag 'case2-after'

    Write-Output 'Caso 3: confiabilidad Outbox con RabbitMQ caido'
    Show-DbSnapshot -Title 'Antes de Caso 3' -SnapshotTag 'case3-before'

    docker stop queue | Out-Null
    $queueStopped = $true

    $outboxResponse = Invoke-FinishExam
    $outboxExamId = [long]$outboxResponse.examId
    Write-Output "Caso 3 examId=$outboxExamId status=$($outboxResponse.status) score=$($outboxResponse.score)"

    $outboxStatus = Get-SqlScalar -Container 'postgres-examenes' -User 'exam_user' -Database 'exam_db' -Query "SELECT status FROM outbox_event WHERE aggregate_id = $outboxExamId ORDER BY id DESC LIMIT 1;"
    Assert-True -Condition ($outboxStatus -eq 'PENDING' -or $outboxStatus -eq 'FAILED') -Message 'El evento outbox debio quedar pendiente o fallido con queue caida'

    docker start queue | Out-Null
    $queueStopped = $false
    Wait-ContainerReady -ContainerName 'queue' -RequireHealthy $true

    Wait-OutboxStatus -ExamId $outboxExamId -ExpectedStatus 'SENT'
    Assert-QueueEmpty
    Assert-EmailLogContainsExam -ExamId $outboxExamId
    Show-DbSnapshot -Title 'Despues de Caso 3' -ExamId $outboxExamId -SnapshotTag 'case3-after'

    $script:OverallStatus = 'OK'
    Write-Output 'RESULTADO: OK - flujo distribuido y desacoplado validado'
} finally {
    if ($ExportEvidence -and $script:EvidenceRunDir) {
        $summary = @(
            "overall_status=$script:OverallStatus"
            "completed_at=$(Get-Date -Format o)"
        )

        if ($null -ne $happyExamId) {
            $summary += "case1_exam_id=$happyExamId"
        }

        if ($null -ne $outboxExamId) {
            $summary += "case3_exam_id=$outboxExamId"
        }

        Set-Content -Path (Join-Path -Path $script:EvidenceRunDir -ChildPath 'summary.txt') -Value $summary -Encoding UTF8
        Write-Output "Resumen guardado: $(Join-Path -Path $script:EvidenceRunDir -ChildPath 'summary.txt')"
    }

    if ($notesStopped) {
        docker start postgres-notas | Out-Null
    }
    if ($queueStopped) {
        docker start queue | Out-Null
    }
}
