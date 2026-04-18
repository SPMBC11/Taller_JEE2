param(
    [switch]$SkipBuild,
    [switch]$ResetData
)

$ErrorActionPreference = 'Stop'

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

Wait-ContainerReady -ContainerName 'postgres-examenes' -RequireHealthy $true
Wait-ContainerReady -ContainerName 'postgres-notas' -RequireHealthy $true
Wait-ContainerReady -ContainerName 'queue' -RequireHealthy $true
Wait-ContainerReady -ContainerName 'wildfly-datos' -RequireHealthy $true
Wait-ContainerReady -ContainerName 'wildfly-logica' -RequireHealthy $true
Wait-ContainerReady -ContainerName 'email-service'

$queueStopped = $false
$notesStopped = $false

try {
    Write-Output 'Caso 1: flujo feliz + persistencia + cola + correo'
    $beforeExam = [int](Get-SqlScalar -Container 'postgres-examenes' -User 'exam_user' -Database 'exam_db' -Query 'SELECT COUNT(*) FROM exam_attempt;')
    $beforeGrade = [int](Get-SqlScalar -Container 'postgres-notas' -User 'student_user' -Database 'student_db' -Query 'SELECT COUNT(*) FROM grade;')

    $happyResponse = Invoke-FinishExam
    $happyExamId = [long]$happyResponse.examId

    $afterExam = [int](Get-SqlScalar -Container 'postgres-examenes' -User 'exam_user' -Database 'exam_db' -Query 'SELECT COUNT(*) FROM exam_attempt;')
    $afterGrade = [int](Get-SqlScalar -Container 'postgres-notas' -User 'student_user' -Database 'student_db' -Query 'SELECT COUNT(*) FROM grade;')

    Assert-True -Condition ($afterExam -eq ($beforeExam + 1)) -Message 'No aumento exam_attempt en flujo feliz'
    Assert-True -Condition ($afterGrade -eq ($beforeGrade + 1)) -Message 'No aumento grade en flujo feliz'

    Wait-OutboxStatus -ExamId $happyExamId -ExpectedStatus 'SENT'
    Assert-QueueEmpty
    Assert-EmailLogContainsExam -ExamId $happyExamId

    Write-Output 'Caso 2: rollback XA cuando falla BD academica'
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

    Write-Output 'Caso 3: confiabilidad Outbox con RabbitMQ caido'
    docker stop queue | Out-Null
    $queueStopped = $true

    $outboxResponse = Invoke-FinishExam
    $outboxExamId = [long]$outboxResponse.examId

    $outboxStatus = Get-SqlScalar -Container 'postgres-examenes' -User 'exam_user' -Database 'exam_db' -Query "SELECT status FROM outbox_event WHERE aggregate_id = $outboxExamId ORDER BY id DESC LIMIT 1;"
    Assert-True -Condition ($outboxStatus -eq 'PENDING' -or $outboxStatus -eq 'FAILED') -Message 'El evento outbox debio quedar pendiente o fallido con queue caida'

    docker start queue | Out-Null
    $queueStopped = $false
    Wait-ContainerReady -ContainerName 'queue' -RequireHealthy $true

    Wait-OutboxStatus -ExamId $outboxExamId -ExpectedStatus 'SENT'
    Assert-QueueEmpty
    Assert-EmailLogContainsExam -ExamId $outboxExamId

    Write-Output 'RESULTADO: OK - flujo distribuido y desacoplado validado'
} finally {
    if ($notesStopped) {
        docker start postgres-notas | Out-Null
    }
    if ($queueStopped) {
        docker start queue | Out-Null
    }
}
