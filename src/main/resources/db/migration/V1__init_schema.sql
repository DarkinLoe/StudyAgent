-- V1 基线：初始业务表结构（chat/qa/plan/rag/knowledge 五个上下文）
-- 说明：由运行库真实结构（SHOW CREATE TABLE）导出，并将历史遗留的 tinytext 统一为 longtext
--       （Hibernate 7 + MySQL 下 @Lob 的 String 会被映射成 tinytext，长文本会 Data too long）

SET NAMES utf8mb4;

CREATE TABLE `chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `content` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `references_json` text COLLATE utf8mb4_unicode_ci,
  `role` enum('ASSISTANT','SYSTEM','USER') COLLATE utf8mb4_unicode_ci NOT NULL,
  `session_id` bigint NOT NULL,
  `usage_json` text COLLATE utf8mb4_unicode_ci,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_chat_message_session` (`session_id`),
  KEY `idx_chat_message_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `chat_session` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `last_message_at` datetime(6) DEFAULT NULL,
  `status` enum('ACTIVE','ARCHIVED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `knowledge_document` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `chunk_count` int NOT NULL,
  `content_type` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `error_msg` text COLLATE utf8mb4_unicode_ci,
  `indexed_at` datetime(6) DEFAULT NULL,
  `name` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL,
  `size_bytes` bigint DEFAULT NULL,
  `source_type` enum('COURSE_TABLE','NOTE','OTHER','PPT','QUESTION_BANK','STUDY_ARRANGEMENT') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('FAILED','INDEXED','PENDING','PROCESSING') COLLATE utf8mb4_unicode_ci NOT NULL,
  `stored_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tags_json` text COLLATE utf8mb4_unicode_ci,
  `text_content` longtext COLLATE utf8mb4_unicode_ci,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_kdoc_user` (`user_id`),
  KEY `idx_kdoc_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `plan_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `done` bit(1) NOT NULL,
  `plan_id` bigint NOT NULL,
  `planned_date` date NOT NULL,
  `planned_minutes` int NOT NULL,
  `planned_start` time NOT NULL,
  `reminder_sent` bit(1) NOT NULL,
  `subject` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_plan_task_plan` (`plan_id`),
  KEY `idx_plan_task_date` (`planned_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `question` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `answer` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `bank_id` bigint DEFAULT NULL,
  `difficulty` int NOT NULL,
  `explanation` longtext COLLATE utf8mb4_unicode_ci,
  `options_json` text COLLATE utf8mb4_unicode_ci,
  `source_doc_id` bigint DEFAULT NULL,
  `stem` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `tags_json` text COLLATE utf8mb4_unicode_ci,
  `type` enum('FILL_BLANK','MULTIPLE_CHOICE','SHORT_ANSWER','SINGLE_CHOICE','TRUE_FALSE') COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_question_user` (`user_id`),
  KEY `idx_question_bank` (`bank_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `question_bank` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `study_note` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `content` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_ref` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_type` enum('AUTO','MANUAL') COLLATE utf8mb4_unicode_ci NOT NULL,
  `tags_json` text COLLATE utf8mb4_unicode_ci,
  `title` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_note_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `study_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `end_date` date DEFAULT NULL,
  `start_date` date DEFAULT NULL,
  `status` enum('ACTIVE','ARCHIVED','COMPLETED','PLANNING') COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `study_reminder` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `message` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `plan_id` bigint DEFAULT NULL,
  `read_flag` bit(1) NOT NULL,
  `remind_at` datetime(6) NOT NULL,
  `task_id` bigint NOT NULL,
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_reminder_user` (`user_id`),
  KEY `idx_reminder_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wrong_question` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `last_wrong_at` datetime(6) NOT NULL,
  `mistake_count` int NOT NULL,
  `question_id` bigint NOT NULL,
  `review_status` enum('MASTERED','PENDING','REVIEWED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_answer` text COLLATE utf8mb4_unicode_ci,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wrong_user_question` (`user_id`,`question_id`),
  KEY `idx_wrong_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

