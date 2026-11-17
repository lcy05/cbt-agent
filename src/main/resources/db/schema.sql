-- Run once by the operator. GRAPH_THREAD / GRAPH_CHECKPOINT are created automatically
-- by MysqlSaver (CREATE_IF_NOT_EXISTS); this script only owns business tables.
CREATE DATABASE IF NOT EXISTS cbt_agent
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE cbt_agent;

CREATE TABLE IF NOT EXISTS session_record (
  id                          BIGINT       NOT NULL AUTO_INCREMENT,
  thread_id                   VARCHAR(64)  NOT NULL,
  session_summary             TEXT,
  confirmed_facts             JSON,
  user_reported_thoughts      JSON,
  user_reported_emotions      JSON,
  evidence_review             JSON,
  accepted_alternative_thought TEXT,
  intervention                VARCHAR(64),
  action_result               TEXT,
  unresolved_issues           JSON,
  next_step                   TEXT,
  confidence_of_record        VARCHAR(32),
  record_approved             TINYINT      NOT NULL DEFAULT 0,
  created_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_session_thread (thread_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
