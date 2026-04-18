CREATE TABLE IF NOT EXISTS exam_attempt (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP NOT NULL,
    total_questions INT NOT NULL,
    correct_answers INT NOT NULL,
    score NUMERIC(5,2) NOT NULL
);

CREATE TABLE IF NOT EXISTS question (
    id BIGSERIAL PRIMARY KEY,
    statement VARCHAR(300) NOT NULL,
    correct_option VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS answer (
    id BIGSERIAL PRIMARY KEY,
    exam_attempt_id BIGINT NOT NULL REFERENCES exam_attempt(id) ON DELETE CASCADE,
    question_id BIGINT NOT NULL REFERENCES question(id),
    selected_option VARCHAR(100) NOT NULL,
    is_correct BOOLEAN NOT NULL,
    points NUMERIC(5,2) NOT NULL
);

CREATE TABLE IF NOT EXISTS evaluation (
    id BIGSERIAL PRIMARY KEY,
    exam_attempt_id BIGINT NOT NULL REFERENCES exam_attempt(id) ON DELETE CASCADE,
    score NUMERIC(5,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    evaluated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS outbox_event (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL,
    next_attempt_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outbox_event_pending
    ON outbox_event(status, next_attempt_at, created_at);

INSERT INTO question (statement, correct_option)
VALUES
    ('Que es JTA?', 'API de transacciones distribuidas'),
    ('Que patron desacopla productor y consumidor?', 'Queue'),
    ('Que garantiza 2PC?', 'Commit atomico entre recursos')
ON CONFLICT DO NOTHING;
