-- 科研项目全生命周期管理系统 DDL
-- 兼容 H2(MODE=MySQL) 与 MySQL 8（不使用方言专有语法）
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL,
    password    VARCHAR(100) NOT NULL,
    real_name   VARCHAR(64)  NOT NULL,
    role        VARCHAR(32)  NOT NULL,
    dept        VARCHAR(64),
    title       VARCHAR(64),
    email       VARCHAR(128),
    phone       VARCHAR(32),
    status      INT          DEFAULT 1,
    created_at  TIMESTAMP
);

CREATE TABLE IF NOT EXISTS project (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    code             VARCHAR(32)  NOT NULL,
    name             VARCHAR(200) NOT NULL,
    category         VARCHAR(32)  NOT NULL,
    type             VARCHAR(32)  NOT NULL,
    leader_id        BIGINT       NOT NULL,
    leader_name      VARCHAR(64),
    dept             VARCHAR(64),
    source           VARCHAR(100),
    budget_total     DECIMAL(14, 2) DEFAULT 0,
    start_date       DATE,
    end_date         DATE,
    status           VARCHAR(32)  NOT NULL,
    summary          VARCHAR(4000),
    expected_outcome VARCHAR(1000),
    ai_score         INT,
    ai_report        VARCHAR(4000),
    created_by       BIGINT,
    created_at       TIMESTAMP,
    updated_at       TIMESTAMP,
    submitted_at     TIMESTAMP,
    approved_at      TIMESTAMP,
    completed_at     TIMESTAMP,
    deleted          INT          DEFAULT 0
);

CREATE TABLE IF NOT EXISTS project_member (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT      NOT NULL,
    user_id     BIGINT,
    user_name   VARCHAR(64) NOT NULL,
    member_role VARCHAR(32),
    workload    INT         DEFAULT 0
);

CREATE TABLE IF NOT EXISTS milestone (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT       NOT NULL,
    name       VARCHAR(200) NOT NULL,
    plan_date  DATE,
    actual_date DATE,
    status     VARCHAR(20) DEFAULT 'PENDING',
    sort_no    INT         DEFAULT 0
);

CREATE TABLE IF NOT EXISTS progress_report (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id       BIGINT NOT NULL,
    reporter_id      BIGINT,
    reporter_name    VARCHAR(64),
    report_date      DATE,
    progress_percent INT,
    content          VARCHAR(2000),
    issues           VARCHAR(1000),
    next_plan        VARCHAR(1000),
    created_at       TIMESTAMP
);

CREATE TABLE IF NOT EXISTS budget_item (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    category   VARCHAR(32) NOT NULL,
    amount     DECIMAL(14, 2) DEFAULT 0
);

CREATE TABLE IF NOT EXISTS expense (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id      BIGINT NOT NULL,
    category        VARCHAR(32) NOT NULL,
    amount          DECIMAL(14, 2) NOT NULL,
    purpose         VARCHAR(500),
    expense_date    DATE,
    applicant_id    BIGINT,
    applicant_name  VARCHAR(64),
    status          VARCHAR(20) DEFAULT 'PENDING',
    approver_id     BIGINT,
    approver_name   VARCHAR(64),
    opinion         VARCHAR(500),
    created_at      TIMESTAMP,
    approved_at     TIMESTAMP
);

CREATE TABLE IF NOT EXISTS achievement (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id    BIGINT NOT NULL,
    type          VARCHAR(32) NOT NULL,
    title         VARCHAR(300) NOT NULL,
    authors       VARCHAR(300),
    achieved_date DATE,
    level_name    VARCHAR(64),
    remark        VARCHAR(500),
    created_at    TIMESTAMP
);

CREATE TABLE IF NOT EXISTS approval_record (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    biz_type      VARCHAR(20) NOT NULL,
    biz_id        BIGINT NOT NULL,
    action        VARCHAR(32) NOT NULL,
    from_status   VARCHAR(32),
    to_status     VARCHAR(32),
    operator_id   BIGINT,
    operator_name VARCHAR(64),
    opinion       VARCHAR(500),
    created_at    TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_log (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT,
    username   VARCHAR(64),
    action     VARCHAR(64),
    target     VARCHAR(128),
    detail     VARCHAR(1000),
    ip         VARCHAR(64),
    created_at TIMESTAMP
);
