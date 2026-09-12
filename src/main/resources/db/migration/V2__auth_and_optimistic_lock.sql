-- V2：鉴权用户表 + 乐观锁版本列 + 长文本列统一为 longtext
-- 幂等考虑：已存在的库执行到这一步即可自动补齐；全新库先跑 V1 再跑 V2

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS sys_user (
  id bigint NOT NULL AUTO_INCREMENT,
  username varchar(64) NOT NULL,
  password_hash varchar(100) NOT NULL,
  
ickname varchar(64) DEFAULT NULL,
  enabled bit(1) NOT NULL,
  created_at datetime(6) NOT NULL,
  updated_at datetime(6) NOT NULL,
  ersion bigint DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `chat_message` ADD COLUMN `version` bigint DEFAULT NULL COMMENT '乐观锁版本号';
ALTER TABLE `chat_session` ADD COLUMN `version` bigint DEFAULT NULL COMMENT '乐观锁版本号';
ALTER TABLE `knowledge_document` ADD COLUMN `version` bigint DEFAULT NULL COMMENT '乐观锁版本号';
ALTER TABLE `plan_task` ADD COLUMN `version` bigint DEFAULT NULL COMMENT '乐观锁版本号';
ALTER TABLE `question` ADD COLUMN `version` bigint DEFAULT NULL COMMENT '乐观锁版本号';
ALTER TABLE `question_bank` ADD COLUMN `version` bigint DEFAULT NULL COMMENT '乐观锁版本号';
ALTER TABLE `study_note` ADD COLUMN `version` bigint DEFAULT NULL COMMENT '乐观锁版本号';
ALTER TABLE `study_plan` ADD COLUMN `version` bigint DEFAULT NULL COMMENT '乐观锁版本号';
ALTER TABLE `study_reminder` ADD COLUMN `version` bigint DEFAULT NULL COMMENT '乐观锁版本号';
ALTER TABLE `wrong_question` ADD COLUMN `version` bigint DEFAULT NULL COMMENT '乐观锁版本号';

ALTER TABLE chat_message MODIFY content longtext NOT NULL;
ALTER TABLE question MODIFY stem longtext NOT NULL;
ALTER TABLE question MODIFY nswer longtext NOT NULL;
ALTER TABLE question MODIFY explanation longtext NULL;
ALTER TABLE knowledge_document MODIFY 	ext_content longtext NULL;
ALTER TABLE study_note MODIFY content longtext NOT NULL;
ALTER TABLE study_reminder MODIFY message longtext NOT NULL;
ALTER TABLE plan_task MODIFY content longtext NOT NULL;