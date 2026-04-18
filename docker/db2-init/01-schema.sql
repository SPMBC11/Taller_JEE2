CREATE TABLE IF NOT EXISTS student (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(200) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS grade (
    id BIGSERIAL PRIMARY KEY,
    exam_attempt_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL REFERENCES student(id),
    score NUMERIC(5,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS result_history (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES student(id),
    exam_attempt_id BIGINT NOT NULL,
    score NUMERIC(5,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    evaluated_at TIMESTAMP NOT NULL
);

INSERT INTO student (id, full_name, email)
VALUES (1, 'Ana Perez', 'ana.perez@example.com')
ON CONFLICT (id) DO NOTHING;
